// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

/// @notice The subset of Coinbase Smart Wallet (MultiOwnable) that recovery needs.
interface ICoinbaseSmartWallet {
    function addOwnerPublicKey(bytes32 x, bytes32 y) external;
    function removeOwnerAtIndex(uint256 index, bytes calldata owner) external;
    function ownerAtIndex(uint256 index) external view returns (bytes memory);
}

/// @title SwissRecoveryModule
/// @notice Social recovery ("guardians") for Coinbase Smart Wallets.
///
/// How it works, in plain words:
///  - ONE shared contract serves every wallet (settings are stored per wallet address).
///  - A wallet opts in by listing this contract as one of its owners when it is created,
///    then calling `setupGuardians` from itself (guardians, threshold e.g. 3-of-4, delay).
///  - If the user loses their device, they create a NEW passkey on a new device and ask
///    their guardians to sign a "recovery request" for that new key.
///  - With enough signatures, anyone can call `initiateRecovery`. A timer starts.
///  - During the delay, the wallet itself (i.e. the user, if they still have a device) can
///    `cancelRecovery` — this is the protection against guardians colluding.
///  - After the delay, anyone calls `finalizeRecovery`: this contract (being an owner of the
///    wallet) adds the new passkey as owner and optionally removes the lost passkey.
///
/// What this contract can NEVER do: move funds, call arbitrary functions, remove itself or
/// remove a non-passkey owner. Its only power is "add a passkey after N guardians + delay".
contract SwissRecoveryModule {
    uint256 public constant MAX_GUARDIANS = 8;
    uint256 public constant NO_REVOKE = type(uint256).max;
    bytes32 public constant RECOVERY_TYPEHASH = keccak256(
        "SwissRecovery(address wallet,bytes32 x,bytes32 y,uint256 revokeIndex,uint256 nonce,uint256 chainId,address module)"
    );

    struct Config {
        address[] guardians;
        uint64 threshold;
        uint64 delay; // seconds
        uint64 nonce; // bumps on every setup / initiation → old signatures can't be replayed
    }

    struct Pending {
        bytes32 x;
        bytes32 y;
        uint256 revokeIndex;
        uint64 executeAfter;
        bool active;
    }

    mapping(address wallet => Config) internal _configs;
    mapping(address wallet => Pending) internal _pending;

    event GuardiansSet(address indexed wallet, address[] guardians, uint256 threshold, uint256 delay);
    event RecoveryInitiated(address indexed wallet, bytes32 x, bytes32 y, uint256 revokeIndex, uint256 executeAfter);
    event RecoveryCancelled(address indexed wallet);
    event RecoveryExecuted(address indexed wallet, bytes32 x, bytes32 y, uint256 revokedIndex);

    error InvalidConfig();
    error NotConfigured();
    error NotEnoughApprovals(uint256 got, uint256 needed);
    error NoPendingRecovery();
    error RecoveryAlreadyPending();
    error TooEarly(uint256 executeAfter);

    // -----------------------------------------------------------------------------------------
    // Called by the wallet itself (through a UserOperation: execute(module, 0, setupGuardians(...)))
    // -----------------------------------------------------------------------------------------

    function setupGuardians(address[] calldata guardians, uint64 threshold, uint64 delay) external {
        uint256 n = guardians.length;
        if (n == 0 || n > MAX_GUARDIANS || threshold == 0 || threshold > n) revert InvalidConfig();
        for (uint256 i; i < n; ++i) {
            if (guardians[i] == address(0)) revert InvalidConfig();
            for (uint256 j; j < i; ++j) {
                if (guardians[j] == guardians[i]) revert InvalidConfig();
            }
        }
        Config storage c = _configs[msg.sender];
        c.guardians = guardians;
        c.threshold = threshold;
        c.delay = delay;
        unchecked {
            c.nonce++;
        }
        delete _pending[msg.sender];
        emit GuardiansSet(msg.sender, guardians, threshold, delay);
    }

    /// @notice The legitimate owner can stop a recovery during the delay.
    function cancelRecovery() external {
        if (!_pending[msg.sender].active) revert NoPendingRecovery();
        delete _pending[msg.sender];
        emit RecoveryCancelled(msg.sender);
    }

    // -----------------------------------------------------------------------------------------
    // Callable by anyone (signatures / timer are what protect it)
    // -----------------------------------------------------------------------------------------

    /// @notice What each guardian signs (with EIP-191 personal_sign).
    function recoveryHash(address wallet, bytes32 x, bytes32 y, uint256 revokeIndex) public view returns (bytes32) {
        return keccak256(
            abi.encode(RECOVERY_TYPEHASH, wallet, x, y, revokeIndex, _configs[wallet].nonce, block.chainid, address(this))
        );
    }

    function initiateRecovery(address wallet, bytes32 x, bytes32 y, uint256 revokeIndex, bytes[] calldata signatures)
        external
    {
        Config storage c = _configs[wallet];
        if (c.threshold == 0) revert NotConfigured();
        if (_pending[wallet].active) revert RecoveryAlreadyPending();

        bytes32 digest = _toEthSignedMessageHash(recoveryHash(wallet, x, y, revokeIndex));
        uint256 seen;
        uint256 count;
        for (uint256 i; i < signatures.length; ++i) {
            address signer = _recover(digest, signatures[i]);
            uint256 idx = _indexOf(c.guardians, signer);
            if (idx == type(uint256).max) continue; // not a guardian
            uint256 bit = 1 << idx;
            if (seen & bit != 0) continue; // same guardian twice counts once
            seen |= bit;
            ++count;
        }
        if (count < c.threshold) revert NotEnoughApprovals(count, c.threshold);

        unchecked {
            c.nonce++;
        }
        uint64 executeAfter = uint64(block.timestamp) + c.delay;
        _pending[wallet] = Pending(x, y, revokeIndex, executeAfter, true);
        emit RecoveryInitiated(wallet, x, y, revokeIndex, executeAfter);
    }

    function finalizeRecovery(address wallet) external {
        Pending memory p = _pending[wallet];
        if (!p.active) revert NoPendingRecovery();
        if (block.timestamp < p.executeAfter) revert TooEarly(p.executeAfter);
        delete _pending[wallet];

        ICoinbaseSmartWallet(wallet).addOwnerPublicKey(p.x, p.y);

        uint256 revoked = NO_REVOKE;
        if (p.revokeIndex != NO_REVOKE) {
            bytes memory old = ICoinbaseSmartWallet(wallet).ownerAtIndex(p.revokeIndex);
            // Only ever removes a passkey (64 bytes). Never an address owner (e.g. this module).
            if (old.length == 64) {
                ICoinbaseSmartWallet(wallet).removeOwnerAtIndex(p.revokeIndex, old);
                revoked = p.revokeIndex;
            }
        }
        emit RecoveryExecuted(wallet, p.x, p.y, revoked);
    }

    // -----------------------------------------------------------------------------------------
    // Views
    // -----------------------------------------------------------------------------------------

    function guardiansOf(address wallet) external view returns (address[] memory) {
        return _configs[wallet].guardians;
    }

    function configOf(address wallet)
        external
        view
        returns (uint256 threshold, uint256 delay, uint256 nonce, uint256 guardianCount)
    {
        Config storage c = _configs[wallet];
        return (c.threshold, c.delay, c.nonce, c.guardians.length);
    }

    function pendingOf(address wallet)
        external
        view
        returns (bytes32 x, bytes32 y, uint256 revokeIndex, uint256 executeAfter, bool active)
    {
        Pending memory p = _pending[wallet];
        return (p.x, p.y, p.revokeIndex, p.executeAfter, p.active);
    }

    // -----------------------------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------------------------

    function _indexOf(address[] storage list, address who) private view returns (uint256) {
        if (who == address(0)) return type(uint256).max;
        for (uint256 i; i < list.length; ++i) {
            if (list[i] == who) return i;
        }
        return type(uint256).max;
    }

    function _toEthSignedMessageHash(bytes32 hash) private pure returns (bytes32) {
        return keccak256(abi.encodePacked("\x19Ethereum Signed Message:\n32", hash));
    }

    /// @dev 65-byte r‖s‖v signature. Returns address(0) on any malformed / malleable input.
    function _recover(bytes32 digest, bytes calldata sig) private pure returns (address) {
        if (sig.length != 65) return address(0);
        bytes32 r = bytes32(sig[0:32]);
        bytes32 s = bytes32(sig[32:64]);
        uint8 v = uint8(sig[64]);
        if (v < 27) v += 27;
        if (v != 27 && v != 28) return address(0);
        if (uint256(s) > 0x7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0) return address(0);
        return ecrecover(digest, v, r, s);
    }
}

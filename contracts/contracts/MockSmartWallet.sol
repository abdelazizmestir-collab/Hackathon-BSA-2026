// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

/// @notice Test double reproducing Coinbase Smart Wallet's MultiOwnable owner rules.
contract MockSmartWallet {
    mapping(uint256 => bytes) public owners;
    mapping(bytes => bool) public isOwnerBytes;
    uint256 public nextOwnerIndex;

    error Unauthorized();
    error AlreadyOwner(bytes owner);
    error WrongOwnerAtIndex(uint256 index);

    constructor(bytes[] memory initial) {
        for (uint256 i; i < initial.length; ++i) _add(initial[i]);
    }

    function _checkOwner() internal view {
        if (isOwnerBytes[abi.encode(msg.sender)] || msg.sender == address(this)) return;
        revert Unauthorized();
    }

    function _add(bytes memory o) internal {
        if (isOwnerBytes[o]) revert AlreadyOwner(o);
        isOwnerBytes[o] = true;
        owners[nextOwnerIndex++] = o;
    }

    function addOwnerPublicKey(bytes32 x, bytes32 y) external {
        _checkOwner();
        _add(abi.encode(x, y));
    }

    function removeOwnerAtIndex(uint256 index, bytes calldata owner) external {
        _checkOwner();
        if (keccak256(owners[index]) != keccak256(owner)) revert WrongOwnerAtIndex(index);
        delete isOwnerBytes[owner];
        delete owners[index];
    }

    function ownerAtIndex(uint256 index) external view returns (bytes memory) {
        return owners[index];
    }

    /// Simulates "the wallet calls a contract" (what execute() does in the real wallet).
    function call(address target, bytes calldata data) external returns (bytes memory) {
        (bool ok, bytes memory ret) = target.call(data);
        if (!ok) {
            assembly {
                revert(add(ret, 32), mload(ret))
            }
        }
        return ret;
    }
}

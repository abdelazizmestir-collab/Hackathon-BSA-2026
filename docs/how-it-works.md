# How it works

The mechanics behind the wallet, explained simply: smart accounts, ERC-4337,
gas sponsorship and social recovery. Who can do what, and what is simulated,
is in [custody-and-threat-model.md](custody-and-threat-model.md).

## 1. Smart contracts in this project

A smart contract is a program deployed on the blockchain at an address
(`0x…`). It has code that cannot change, some state, and anyone can call it.

| Contract | Role |
|---|---|
| **USDC** (`0x036C…CF7e` on Base Sepolia) | An ERC-20 token: a table `balance[address]` and a `transfer(to, amount)` function |
| **EntryPoint v0.6** (`0x5FF1…2789`) | The central ERC-4337 contract, the same on every chain |
| **Coinbase Smart Wallet factory** (`0x0BA5…428a`) | Deploys wallet contracts |
| **SwissRecoveryModule** (`0xB378…428F`) | Our recovery contract: 3 guardians of 4, plus a delay |
| **Coinbase Smart Wallet** | The client's wallet is itself a smart contract |

To call a function, the app sends ABI-encoded data: the first 4 bytes of
`keccak256("transfer(address,uint256)")` (the selector, `0xa9059cbb`), then each
argument on 32 bytes. `data/aa/Abi.kt` does exactly that.

## 2. Why not a classic wallet

A classic wallet (MetaMask) is an externally owned account: just a private key.

- Lose the key and everything is lost; there is no recovery.
- Sending anything, even USDC, requires ETH to pay gas.
- The only rule it can enforce is "the signature is valid".

## 3. Account abstraction and ERC-4337

With account abstraction the account is a smart contract, and the contract
decides what counts as a valid transaction. The key becomes one way among
others to authorise it. That allows:

- **sponsored gas:** a paymaster pays the fees, so the client needs no ETH;
- social recovery, limits, several signers, device keys;
- several actions in one operation;
- an address known before deployment (CREATE2), so the wallet can receive funds
  at once.

ERC-4337 is the standard that makes this possible without changing Ethereum:
instead of a transaction, the app sends a **UserOperation**.

```
 ┌───────────────┐   UserOperation    ┌──────────────┐   handleOps([...])   ┌──────────────┐
 │  SwissWallet  │ ─────────────────▶ │   BUNDLER    │ ───────────────────▶ │  EntryPoint  │
 │ (Android app) │                    │  (Pimlico)   │   real transaction   │  (contract)  │
 └──────┬────────┘                    └──────────────┘                      └──────┬───────┘
        │ pm_sponsorUserOperation                                1. validateUserOp │ → wallet
        ▼                                                        2. validatePaymaster → paymaster
 ┌──────────────┐                                                3. execute(to, value, data)
 │  PAYMASTER   │  "I pay the gas for this operation" (signature)
 │  (Pimlico)   │
 └──────────────┘
```

| Actor | In the code | Role |
|---|---|---|
| UserOperation | `domain/aa/AccountAbstraction.kt` | The intent: who (`sender`), what (`callData`), nonce, gas, paymaster, signature |
| Smart account | `CoinbaseSmartAccount.kt` | The wallet contract: checks an owner's signature and runs `execute(to, value, data)` |
| Device key | `AndroidPasskeyVault.kt`, `WebAuthnAssertion.kt` | A P-256 key generated in the phone's secure hardware (Android Keystore), never exportable. It only signs, in the WebAuthn format |
| Bundler | `PimlicoClient` (`eth_sendUserOperation`) | Collects UserOperations and sends one real transaction to the EntryPoint |
| Paymaster | `PimlicoClient` (`pm_sponsorUserOperation`) | Pays the gas in place of the client |
| EntryPoint | `ChainConfig.ENTRY_POINT_V06` | Validates, executes, and charges the right party |
| Factory | `ChainConfig.CSW_FACTORY_V1` | Deploys the wallet on its first operation (`initCode`) |

**Pimlico** runs the bundler and the paymaster behind one JSON-RPC URL. The
paymaster simulates the operation, estimates gas and returns
`paymasterAndData`: its address and a signature saying "I pay". The EntryPoint
checks that signature on-chain and charges Pimlico's deposit, not the wallet.
"Gasless" means the client does not pay; the sponsor does (Swissquote in
production, with a sponsorship policy). On testnets it is free.

## 4. What happens on "Confirm & send"

Files: `data/aa/AaTransferService.kt` and `data/aa/UserOperationSender.kt`, the
pipeline shared by every on-chain action of the app. The payment safeguards run
first (see [custody-and-threat-model.md](custody-and-threat-model.md),
"Authentication").

1. **Preparing.** Reads the balance, the nonce (`EntryPoint.getNonce`), whether
   the wallet is deployed, and the gas price. Builds the wallet's `callData`:
   `execute(recipient, amount, 0x)` for ETH, or
   `execute(USDC, 0, transfer(recipient, amount))` for USDC. The first
   operation adds `initCode`, which deploys the wallet.
2. **Sponsoring.** `pm_sponsorUserOperation` returns the gas limits and
   `paymasterAndData`.
3. **Signing.** Computes the `userOpHash` and has the device key sign it. The
   signature is wrapped in the WebAuthn format; the wallet contract verifies
   on-chain that the challenge is the `userOpHash` and that the P-256 signature
   is valid.
4. **Submitting.** `eth_sendUserOperation`.
5. **Waiting.** Polls `eth_getUserOperationReceipt` until the real transaction
   hash is known, then links to the explorer.

## 5. Wallet generation (Swissquote → SwissWallet)

1. The Swissquote app starts SwissWallet's invisible `ProvisionActivity`,
   protected by a signature permission (only our apps can call it), and passes
   its guardian address.
2. SwissWallet creates the device key in the Keystore.
3. It computes the wallet's counterfactual address:
   `factory.getAddress([device key, recovery module], 0)`. The contract does not
   exist yet, but CREATE2 makes its address known.
4. It returns the address to Swissquote: "Your wallet is ready!".
5. The contract is deployed by the first operation, paid by the paymaster.

## 6. Social recovery

The device key cannot be copied, so a lost phone means a lost key. A smart
wallet can plan for that.

The wallet is a vault whose address never changes, with two owners:

- **#0 the device key:** can do everything (send, change guardians, cancel a
  recovery);
- **#1 `SwissRecoveryModule`:** can do one thing only, add a new key and remove
  the lost one, and only if **3 of the 4 guardians** signed and the **safety
  delay** has passed (30 seconds in the demo, 48 hours in production). During
  the delay the current key can cancel.

### Guardians

| Guardian | How it is added | How it approves |
|---|---|---|
| **Swissquote** (automatic) | It passes its guardian address when the wallet is generated. Already in the list; cannot be removed. | On the new phone, *Ask Swissquote* opens the Swissquote app, which checks the session and that it is this client's wallet, runs an identity check (simulated), signs, and returns to SwissWallet. |
| **The client's other device** | On that device: *Be a guardian*, *For my own wallet*, copy the guardian code (`SWGRD1.…`). On the phone: Profile, Guardians, *Add guardian*, paste. | Receives the request code (`SWREC1.…`), checks the key code, approves, and sends back an approval code (`SWSIG1.…`). |
| **People close to the client** | Same, with *For a close one*, or a plain `0x…` address. | Same. They should call the client and compare the key code (for example `3F2A-91C0`) first. |
| **An independent institution** (a notary, a trust company) | Chosen by the client, independent of the client and of Swissquote. Demo: *Independent institution (demo)*, with a simulated key on the phone. | Checks the client's identity, then signs. |
| **Simulated guardian** (demo only) | *Simulated guardian (demo)*: a key kept on the same phone, to run the flow on one emulator. | *Approve with simulated guardians (demo)*. |

Why 3 of 4: one guardian can be unreachable; one or two guardians can do
nothing; a guardian never sees or moves the funds. Why a delay: if three
guardians are deceived or collude while the client still has the phone, the
app shows an alert in Profile, Guardians, and the client cancels.

### On-chain steps

| Step | Who | Call |
|---|---|---|
| Activate protection | The wallet (UserOperation signed by the device key, gas sponsored) | `executeBatch([deploy the module with CREATE2 if needed, module.setupGuardians(4 addresses, 3, 30)])` |
| A guardian approves | The guardian, off-chain | Signs `recoveryHash(wallet, new key, index to remove, nonce, chainId, module)` (EIP-191) |
| Start the recovery | The new phone, through a small helper wallet of its own | `module.initiateRecovery(wallet, x, y, index, [3 signatures])`: checks three distinct guardians, starts the timer |
| Cancel | The current key | `module.cancelRecovery()` |
| Finish | The new phone (helper) | `module.finalizeRecovery(wallet)`: `addOwnerPublicKey(x, y)` and `removeOwnerAtIndex(lost key)` |

Protections in the module:

- the **nonce** changes at every start and every reconfiguration, so a
  signature works once;
- the **chain id** and the module address are part of what is signed, so it
  cannot be replayed elsewhere;
- a guardian is counted once, and non-guardians are ignored;
- starting a recovery takes three signatures, so one guardian cannot start or
  block one;
- the module only removes device keys (64 bytes), never an address, so it
  cannot lock itself out.

The module is one contract shared by all wallets, at the same address on every
chain: `0xB3786e6BdB300575df7b5b3291E387a6f287428F`.

## 7. Where the code is

| File | Role |
|---|---|
| `contracts/contracts/SwissRecoveryModule.sol` | The recovery contract |
| `contracts/test.js`, `contracts/e2e.cjs` | Contract tests; end-to-end test with the real Coinbase Smart Wallet and EntryPoint, driven by the app's Kotlin code |
| `core/recovery/Recovery.kt` | `RecoveryRequest` (what guardians sign), `GuardianApproval`, the 3-of-4 and delay settings |
| `swisswallet/data/recovery/SocialRecoveryService.kt` | Activate, cancel, request, collect, start, finish |
| `swisswallet/data/recovery/GuardianRepositories.kt` | Guardian names, simulated guardians, "Be a guardian" mode |
| `swisswallet/data/key/AndroidPasskeyVault.kt` | The P-256 device key in the Android Keystore |
| `swisswallet/security/` | Payment safeguards: device authentication, limits, blocked addresses |
| `swisswallet/ui/recovery/` | Guardians, Recover my wallet, Be a guardian screens |
| `swissquote/guardian/GuardianApprovalActivity.kt` | Swissquote as guardian: identity check, then signature |

## 8. Limits

- Testnet only (Base Sepolia by default). Mainnet would need a backend holding
  the Pimlico key with a sponsorship policy, Swissquote's guardian key in an
  HSM, and a real identity check.
- Received payments are not indexed: the History screen shows what the app
  sent, plus a few entries tagged "Demo". An incoming payment changes the
  balance only.
- The device key is not bound to biometrics in hardware; the app asks for
  device authentication before a payment instead.
- Codes are shared by copy and paste; production would use QR codes or
  notifications.

# Swissquote × SwissWallet — two Android apps (Jetpack Compose, MVVM, ERC-4337)

Two apps on the same phone:

* **Swissquote** — the trading app. Its Wallet tab generates your crypto wallet, and Swissquote becomes
  **automatic guardian #1** of that wallet.
* **SwissWallet** — the crypto wallet: a **Coinbase Smart Wallet** (ERC-4337 smart account) controlled by a
  **passkey** in the phone's secure chip, with **gasless** transfers (Pimlico) and **social recovery
  3-of-4** (Swissquote + your other device + close ones, 30 s safety delay for the demo, 48 h in production).

```
SwissWallet/                 ← Android Studio project (open this folder)
├── core/                    ← shared Android library
│   ├── designsystem/        theme (Figma tokens), icons, components
│   ├── common/Formats.kt    CHF formatting 1'182.12
│   ├── contract/            Swissquote ⇄ SwissWallet contract (actions, extras, signature permissions)
│   └── recovery/            RecoveryRequest / GuardianApproval (what guardians sign), EncryptedKeyStore
├── swissquote/              ← APP 1 (com.example.swissquote)
│   ├── data/     fake market feed, DataStore session, wallet launcher, KeystoreSwissquoteGuardian
│   ├── guardian/ GuardianApprovalActivity  ← "Approve wallet recovery" (identity check, then signs)
│   └── ui/       trade · welcome · wallet tab
├── swisswallet/             ← APP 2 (com.example.swisswallet)
│   ├── domain/   models (incl. RecoveryModels), AA interfaces, repository/service interfaces
│   ├── data/
│   │   ├── aa/        CoinbaseSmartAccount, UserOperationSender (shared ERC-4337 pipeline),
│   │   │              PimlicoClient (v0.6), RecoveryModule (+ bytecode), Abi, AaTransferService
│   │   ├── key/       AndroidPasskeyVault (P-256 in Android Keystore) + WebAuthnAssertion
│   │   ├── recovery/  SocialRecoveryService, guardian repositories, Swissquote approval contract
│   │   ├── rpc/       JSON-RPC client (OkHttp)
│   │   └── repository/ wallet, portfolio, prices (fake), history, settings
│   ├── ui/       splash · home · wallet · history · profile · send · receive
│   │             · recovery/ (Guardians, Recover my wallet, Be a guardian)
│   └── ProvisionActivity.kt  (invisible, called by Swissquote to generate the wallet)
├── contracts/               ← Solidity: SwissRecoveryModule + tests (local EVM) + end-to-end test
└── docs/                    ← explanations in French (ERC-4337, social recovery)
```

## MVVM in one picture

```
Composable Screen ──(user events)──▶ ViewModel ──▶ Repository / Service (interface, domain/)
        ▲                               │                     │
        └────── StateFlow<UiState> ◀────┘          implementation (data/) — fake or real
```

* Each screen has **one immutable `UiState`** exposed as `StateFlow`.
* ViewModels only know **interfaces**; `di/AppContainer` wires the implementations (manual DI).
* Loading = skeletons / spinners, buttons block double taps, every network call has an error state.

## Setup

1. Open the folder in Android Studio and let Gradle sync.
2. Free Pimlico account → <https://dashboard.pimlico.io> → API Keys → copy the key.
3. Add it to `local.properties` (**never committed**, it's in `.gitignore`):
   ```
   PIMLICO_API_KEY=pim_xxxxxxxxxxxxxxxxx
   # optional — default network is Base Sepolia (Coinbase's testnet, passkey verification is cheap there)
   SWISSWALLET_CHAIN=base-sepolia
   # optional custom RPC (default https://sepolia.base.org)
   RPC_URL=
   ```
4. Start **one** emulator (API ≥ 26).
5. Run **`swisswallet`** ▶ then **`swissquote`** ▶ (same debug key → the signature permissions between the apps work).
   If you had the previous version installed, uninstall both apps first (new wallet format).

## Demo flow

1. Swissquote → **Wallet** tab → *Generate my wallet* → SwissWallet creates a **passkey** and computes the
   wallet address (nothing on-chain yet). Swissquote sends its **guardian address** at the same time.
2. Tap the Wallet icon → **SwissWallet** opens. **Receive** → copy the address → get Base Sepolia ETH
   (Coinbase faucet).
3. **Send** → address + amount → *Confirm & send* → 5 ERC-4337 steps → **0 gas paid** (Pimlico).
   The first operation also deploys your Coinbase Smart Wallet.
4. **Profile → Guardians** → Swissquote is already there → add 3 more (see below) →
   *Activate protection (3 of 4)*.
5. **Profile → Simulate lost phone (demo)** → the passkey is deleted.
6. **I lost my phone — recover my wallet** → address prefilled → *Find my guardians* →
   *Create my new key & ask guardians* → *Ask Swissquote* (identity check in the Swissquote app) +
   *Approve with simulated guardians* → **3/3** → *Start recovery* → **30 s** → *Finish recovery* →
   same wallet, same funds, new passkey. The lost passkey is removed.

## Social recovery (3 of 4) — what I added

### The idea in plain words

Your wallet is a **safe (smart contract)** with a list of people allowed to give it orders ("owners"):

| Owner | What it can do |
|---|---|
| **#0 your passkey** (secure chip of your phone) | everything: send money, change guardians, cancel a recovery |
| **#1 the SwissRecoveryModule** (a small contract) | **only one thing**: add a new passkey (and remove the lost one) — and only if **3 of your 4 guardians** agree **and** the **safety delay** is over |

The address of the safe never changes, so losing the phone doesn't lose the money: you just put a new key on it.

### Your 4 guardians

| Guardian | How it's added | How it approves |
|---|---|---|
| **Swissquote** (automatic) | When you tap *Generate my wallet*, Swissquote sends its guardian address to SwissWallet (`EXTRA_SWISSQUOTE_GUARDIAN`). It's pre-filled as guardian #1 and can't be removed. | On the new phone: *Ask Swissquote* opens Swissquote's **Approve wallet recovery** screen. It checks you're logged in and that the wallet is the one linked to your account, runs an **identity check (simulated: 2FA, selfie vs ID, SMS)**, then signs. |
| **My other device** (tablet, old phone…) | On that device: SwissWallet → *Be a guardian* → *For my own wallet* → copy the **guardian code** (`SWGRD1.…`). On your phone: Profile → Guardians → *Add guardian* → *My other device* → paste. | It receives your **request code** (`SWREC1.…`), checks it (*You are a guardian of this wallet ✓*, fingerprint), taps *Approve* and sends back an **approval code** (`SWSIG1.…`) that you paste. |
| **A close one** (mom, friend…) | Same, with *For a close one*. You can also paste a plain `0x…` address + a name. | Same as above. They can never see or move your money. |
| **Simulated guardian (demo)** | *Add guardian* → *Simulated guardian (demo)*: a key stored on this phone, so the whole 3-of-4 flow works with **one emulator**. | *Approve with simulated guardians (demo)*. In real life each key would be on someone else's phone. |

**Rule: 3 of 4.** Losing one guardian is fine; one (or two) guardians alone can do nothing.
**Safety delay: 30 s for the demo** (`RecoveryPolicy.DELAY_SECONDS`, 48 h in production). During the delay, if
your old phone is still in your hands (= it's an attack), **Profile → Guardians** shows a red banner →
*Cancel this recovery*.

### How it's built

* **Wallet = Coinbase Smart Wallet** (factory `0x0BA5ED0c…F428a`, EntryPoint v0.6) created with
  owners `[passkey(x, y), SwissRecoveryModule]` → `data/aa/CoinbaseSmartAccount.kt`.
* **Passkey** = P-256 key generated in the **Android Keystore** (`AndroidPasskeyVault`). Each signature is
  wrapped as a **WebAuthn assertion** (`WebAuthnAssertion`: authenticatorData + clientDataJSON with the
  userOpHash as challenge) exactly like a browser passkey, so the wallet verifies it on-chain.
* **SwissRecoveryModule.sol** (`contracts/contracts/`) — one shared contract per chain, deployed with CREATE2
  at the same address everywhere: `0xB3786e6BdB300575df7b5b3291E387a6f287428F`. The first wallet that activates
  protection deploys it (inside the same gasless operation). Functions: `setupGuardians`, `initiateRecovery`
  (checks 3 EIP-191 guardian signatures over `recoveryHash`, starts the timer), `finalizeRecovery` (after
  the delay: `addOwnerPublicKey` + removes the lost passkey), `cancelRecovery`.
  Signatures include the module **nonce**, the **chain id** and the **module address** → no replay.
* **Activate protection** = one UserOperation `executeBatch([deploy module if needed, setupGuardians(4, 3, 30)])`.
* **New phone**: creates a new passkey, builds the request, collects approvals, then sends
  `initiateRecovery` / `finalizeRecovery` from a tiny **helper Coinbase wallet** owned by the new passkey
  (it can't use the real wallet yet). Gas is still paid by Pimlico.
* `UserOperationSender` = the shared ERC-4337 pipeline (nonce → initCode → Pimlico sponsorship → passkey
  signature → bundler → receipt) used by Send, Activate protection, Cancel, Start/Finish recovery.

### Tests (all run and passing)

| Test | What it proves |
|---|---|
| `contracts/` → `npm test` | 10 Solidity tests on a local EVM: 3-of-4, duplicates / outsiders don't count, delay, cancel, no replay, module can't lock itself out… |
| `contracts/` → `npm run e2e` | **End to end with the REAL Coinbase Smart Wallet + EntryPoint v0.6**, every calldata / hash / passkey signature produced by **the app's Kotlin code**: deploy + transfer signed by passkey → executeBatch deploys the module + 3-of-4 setup → attack cancelled by the owner → recovery via helper account → finalize refused before 30 s → after 30 s new passkey controls the same wallet, old one rejected. (Needs `kotlinc` for `kotlin-harness/build.sh`.) |
| `./gradlew :swisswallet:testDebugUnitTest` | Kotlin encodings byte-for-byte equal to **viem** (createAccount, execute, executeBatch, setupGuardians, initiateRecovery, SignatureWrapper, WebAuthnAuth, userOpHash v0.6, recoveryHash) + WebAuthn assertion verifies. |

## What's real vs simulated

| Real (Base Sepolia blockchain) | Simulated |
|---|---|
| Passkey in Android Keystore, WebAuthn signatures verified on-chain | Swissquote login / account opening |
| Coinbase Smart Wallet, counterfactual address, deployment | Swissquote identity check (2FA / selfie / SMS) |
| Gasless UserOperations (Pimlico bundler + paymaster) | "Simulated guardians" (keys on the same phone, for a 1-emulator demo) |
| SwissRecoveryModule: guardians, 3-of-4 signatures, delay, cancel, key rotation | Watchlist, CHF prices & charts, swap/NFT/staking |
| ETH + USDC balances, explorer links | Swissquote's guardian key is in the app (production: Swissquote's HSM, server side) |

Tested on the emulator (Pixel 10a, API 37) on Base Sepolia: generate → activate protection (module deployed +
4 guardians) → simulate lost phone → Swissquote identity check + 2 simulated guardians → 30 s → finish → same
address, new passkey, and the new passkey signs the next operation. Note: copy/paste of codes needs a working
clipboard (on this emulator Android Studio reported "System clipboard is unavailable"; on a real phone it works).

Guardian names ("Mom", "My iPad") are only stored on the phone; on-chain a guardian is just an address.
The passkey isn't bound to biometrics in the demo (emulator); on a real phone you'd add
`setUserAuthenticationRequired(true)`.

## En bref (FR)

Le wallet est un coffre-fort (smart contract Coinbase Smart Wallet). Ta **passkey** (puce sécurisée du
téléphone) l'ouvre au quotidien. Le **module de récupération** peut seulement ajouter une nouvelle passkey,
et seulement si **3 de tes 4 garants** sont d'accord **et** après un **délai de sécurité** (30 s en démo,
48 h en vrai) pendant lequel tu peux annuler. Garants : **Swissquote automatiquement** (il vérifie ton
identité avant de signer), **ton autre appareil**, **tes proches**. Explication complète :
**docs/Recuperation_sociale.md** (et **docs/ERC-4337_explication.md** pour l'abstraction de compte).

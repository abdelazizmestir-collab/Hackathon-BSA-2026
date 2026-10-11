# Swissquote × SwissWallet — two Android apps (Jetpack Compose, MVVM, ERC-4337)

```
SwissWallet/                 ← Android Studio project (open this folder)
├── core/                    ← shared Android library
│   ├── designsystem/        theme (Figma tokens), icons (Figma SVG paths), components
│   ├── common/Formats.kt    CHF formatting 1'182.12
│   └── contract/            Swissquote ⇄ SwissWallet contract (action, extras, permission)
├── swissquote/              ← APP 1 (com.example.swissquote)
│   ├── domain/   model + repository interfaces
│   ├── data/     fake market feed, DataStore session, wallet launcher + ActivityResult contract
│   ├── di/       AppContainer (manual DI)
│   └── ui/       trade · welcome · wallet tab  (Screen + ViewModel each)
└── swisswallet/             ← APP 2 (com.example.swisswallet)
    ├── domain/   models, AA interfaces (Signer, SmartAccount, BundlerClient, PaymasterClient…)
    ├── data/
    │   ├── aa/   UserOperation v0.7 hashing, ABI, SimpleSmartAccount, PimlicoClient, AaTransferService
    │   ├── key/  KeystoreKeyVault (owner key encrypted by Android Keystore)
    │   ├── rpc/  JSON-RPC client (OkHttp) + Ethereum node client
    │   └── repository/  wallet, portfolio, fake prices, history, settings
    ├── ui/       splash · home · wallet · history · profile · send · receive
    └── ProvisionActivity.kt  (invisible, called by Swissquote to generate the wallet)
```

## MVVM in one picture

```
Composable Screen ──(user events)──▶ ViewModel ──▶ Repository / Service (interface, domain/)
        ▲                               │                     │
        └────── StateFlow<UiState> ◀────┘          implementation (data/) — fake or real
```

* Each screen has **one immutable `UiState`** (loading / content / error) exposed as `StateFlow`.
* One-shot events (navigation, snackbars, launch an Activity) go through a `Channel`.
* ViewModels only know **interfaces**; `di/AppContainer` wires the implementations.
* Loading states use **skeleton shimmers**, buttons show a spinner and block double taps,
  every network call has an error state with *Try again*.

## Setup

1. Open the folder in Android Studio and let Gradle sync.
2. Create a free Pimlico account → <https://dashboard.pimlico.io> → API Keys → copy the key.
3. Add it to `local.properties` (never committed):
   ```
   PIMLICO_API_KEY=pim_xxxxxxxxxxxxxxxxx
   # optional, defaults to publicnode:
   SEPOLIA_RPC_URL=https://ethereum-sepolia-rpc.publicnode.com
   ```
4. Start **one** emulator (any API ≥ 26).
5. Run configuration **`swisswallet`** ▶ then **`swissquote`** ▶ — both get installed on the same emulator.
   (Both are signed with your debug key, so the signature permission between them works.)

## Demo flow

1. Swissquote opens on **Trade** (live-ish fake watchlist, pull to refresh, sort).
2. Home / Trade / Banking / Plan → **Welcome** page (Login, Open your account, Discover the app = guest).
3. **Wallet** tab
   * guest → *Create your crypto wallet* → sign-up CTA
   * client → *Generate my wallet* → Swissquote calls SwissWallet's invisible `ProvisionActivity`
     → key created in the Keystore → smart-account address computed → *Your wallet is ready!*
   * once generated, tapping the **Wallet** icon opens the **SwissWallet** app.
4. In SwissWallet → **Receive** → copy the address → get Sepolia ETH from the faucet.
5. **Send** → paste a public address, amount → *Review* → *Confirm & send* → watch the 5 ERC-4337
   steps → *View on Etherscan*. **You pay 0 gas** (Pimlico paymaster).

## Tests

`./gradlew :swisswallet:testDebugUnitTest` — ABI selectors, encoding, userOpHash, validation.

## What's real vs simulated

| Real (Sepolia blockchain) | Simulated |
|---|---|
| Owner key generation + Keystore encryption | Swissquote login / account opening |
| Counterfactual smart-account address | Watchlist prices |
| ETH + USDC balances | CHF prices & charts |
| UserOperation build / sign / sponsor / send / receipt | Swap, NFT, staking, PIN, biometrics toggles |
| Etherscan links | Demo entries in History (tagged “Demo”) |

See **docs/ERC-4337_explication.md** for the full explanation (French).

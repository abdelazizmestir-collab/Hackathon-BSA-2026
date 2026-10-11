# Project description

**Swissquote Wallet**: a self-custody wallet a Swissquote client generates from
inside the Swissquote app. The client's device key is the only key that can
spend. If the phone is lost, any 3 of 4 guardians (Swissquote plus three the
client chose) install a new key after a waiting period the client can cancel.
Swissquote alone can do nothing.

This page covers what the track asks a submission to describe and links to the
full documents.

## 1. Custody model and threat model

- **Decision:** user-controlled spending with collective recovery authority.
- **Swissquote alone or with a partner:** alone, no. There is no partner
  institution; Swissquote always needs two of the client's own guardians.
- **If Swissquote is gone:** the client's three guardians recover without it.
- **Main open risks:** an unlocked phone can spend; the recovery contract is
  new and unaudited; the spending key can change the guardians at once.

Full text: [custody-and-threat-model.md](custody-and-threat-model.md).

## 2. Architecture

```
 Swissquote app (MOCK bank app)                SwissWallet app
 ├─ login, account (MOCK)                      ├─ device key (P-256, secure hardware)
 ├─ "Generate my wallet" ───────────────────▶  ├─ Coinbase Smart Wallet address
 └─ Swissquote guardian key (MOCK:             ├─ send (gasless UserOperations)
    in the app; production: bank HSM)          └─ guardians, recovery, cancel
        │ signs recovery approvals                      │
        ▼                                               ▼
 Guardians on their own phones            Pimlico bundler + paymaster ──▶ Base Sepolia
 ("Be a guardian" mode, own keys)                                         ├─ Coinbase Smart Wallet
                                                                          │  owners: device key + module
                                                                          └─ SwissRecoveryModule
                                                                             4 guardians, 3 signatures, delay
```

Mocked parts are marked MOCK. The Swissquote app is a separate app with its own
process and storage; the wallet app never holds Swissquote's key. The complete
list is "What is simulated" in
[custody-and-threat-model.md](custody-and-threat-model.md) and the table in the
[README](../README.md). How ERC-4337 and the recovery work, in French:
[ERC-4337_explication.md](ERC-4337_explication.md),
[Recuperation_sociale.md](Recuperation_sociale.md).

## 3. Custody and the Travel Rule

- **Custody:** Swissquote cannot move the funds alone, so the assets are not
  in the bank's custody. We still treat the recovery quorum as a form of
  control and argue the grey-zone question openly.
- **Travel Rule, account to wallet:** the wallet is generated from inside the
  client's Swissquote session, so the bank knows whose wallet it is from the
  start.
- **Travel Rule, wallet to a third party:** Swissquote does not send the funds,
  but it provides the app and pays the gas inside a permanent client
  relationship, which Swiss law treats as a payment service. So it should
  screen what it helps send.

Full text, with each source and how far it was checked: [legal.md](legal.md).

## 4. What Swissquote gains

Retention of clients who want self-custody (about CHF 626 of operating income
per account per year in 2025), revenue less tied to trading volume, and lower
costs for ownership checks. Full text: [business-case.md](business-case.md).

## 5. Bonus topics

**Gasless transactions.** Real. Every operation is an ERC-4337 UserOperation
whose gas is paid by a paymaster, including wallet deployment and recovery.
Swissquote pays, as a retention cost.

**Compliant interaction.** Partial. The review screen shows recipient and
amount before signing. The blocked-address check, payment limits and device
authentication are on the branch `feat/payment-safeguards`; transaction
simulation and the richer checks were built in the earlier web prototype.

## 6. AI tools

The team worked with **Claude Code** (Anthropic, model Claude Opus 5.5).
Commits made with it carry a `Co-Authored-By` line. It was used to write and
review code, to write tests, and to draft and check the documents, including
the legal sources. People made the design decisions, ran the apps and reviewed
the results. *Each teammate to confirm, and to add any other tool used.*

## 7. Deployment manifest

| Item | Value |
|---|---|
| Chain | Base Sepolia, chain id 84532 |
| Wallet contract | Coinbase Smart Wallet, factory `0x0BA5ED0c6AA8c49038F819E587E2633c4A9F428a` |
| EntryPoint | v0.6, `0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789` |
| Recovery contract | `SwissRecoveryModule`, `0xB3786e6BdB300575df7b5b3291E387a6f287428F` (CREATE2), source in `contracts/contracts/` |
| Recovery settings | 4 guardians, 3 signatures, waiting period 30 seconds in the demo (48 hours as the production starting point) |
| USDC | Circle test USDC, `0x036CbD53842c5426634e7929541eC2318f3dCF7e` |
| Bundler and paymaster | Pimlico |
| Demo wallet address and transactions | *To fill from the demo run* |

## 8. Evidence and earlier work

| What | Where |
|---|---|
| Contract tests and end-to-end test with the real Coinbase Smart Wallet | `contracts/` (`npm test`, `npm run e2e`) |
| Kotlin encoding tests | `./gradlew :swisswallet:testDebugUnitTest` |
| Demo story | [demo-script.md](demo-script.md) |
| Judge questions | [judge-qa.md](judge-qa.md) |
| Earlier web prototype (passkeys, relay, separate guardian services, independent recovery page, sanctions screening, simulation) | Repository `rayenea/swissquote-wallet`, live at https://sq-wallet-hackathon-demo.vercel.app |

## Before submitting

Remove this section when each line is done.

- [ ] Fill the demo wallet address and transaction links (section 7).
- [ ] Teammates confirm section 6.
- [ ] Update section 5 if the safeguards branch is merged.
- [ ] Check whether the recovery contract's source is verified on Basescan.

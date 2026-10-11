# Custody model and threat model

Applies to the Android apps in this repository (`swissquote` + `swisswallet`).

## Decision

**User-controlled spending with collective recovery authority.**

- Only the client's device key can move funds. It is a P-256 key generated
  inside the phone's secure hardware (Android Keystore); it cannot be exported
  and is not synced to any cloud account.
- A lost key is replaced by **any 3 of 4 guardians**, after a waiting period
  the client can cancel. Guardians are **Swissquote** (added automatically)
  and **three chosen by the client**: their other device, people close to
  them, or an independent institution (e.g. a notary or a trust company, independent from both the client and Swissquote).

We do not call this "legally non-custodial": a group that can install a new
owner key is a form of control. What we claim is narrower and checkable
on-chain: Swissquote can never act alone.

Stack: Coinbase Smart Wallet (ERC-4337, EntryPoint v0.6) on Base Sepolia,
owners `[device key, SwissRecoveryModule]`.

## Who can do what

| Who | Spend | Replace the key (recover) |
|---|---|---|
| Client, with the device key | Yes | Can cancel any recovery during the waiting period; can change the guardians |
| Swissquote alone | No | No (1 signature of the 3 needed) |
| Swissquote + one client guardian | No | No (2 of 3) |
| Swissquote + the independent institution, if the client picked one | No | No: one of the client's own guardians is still needed |
| Swissquote + two client guardians | No | Yes, after the waiting period, unless the client cancels |
| The client's three guardians, without Swissquote | No | Yes, after the waiting period |
| `SwissRecoveryModule` contract | No | Adds the new key and removes the lost one, only with 3 guardian signatures and after the delay. It has no function that moves funds. |

## The brief's question

> Could Swissquote, alone or together with a partner, move the funds without
> the client?

- **Alone: no.** One signature of three.
- **With an institution: only one the client chose.** Swissquote has no
  partner. The client may pick an independent institution as one of their three
  guardians; Swissquote + that institution are still one signature short and
  need one of the client's own guardians (person or device), the waiting
  period, and no cancel from the client.
- **With two of the client's own guardians: indirectly.** They cannot spend,
  but they can install a new key after the waiting period. The client's
  current key cancels the request at any time before then.

That last line is the trust boundary. The contract checks signatures and time;
it cannot check that the guardians judged correctly.

## Where the keys live

| Key | Where | Who controls it | Can it move funds? |
|---|---|---|---|
| Device key (wallet owner) | Phone's secure hardware, non-exportable | Client | Yes |
| SwissRecoveryModule (wallet owner) | A contract; it has no private key | Its code | No |
| Swissquote guardian key | **Mocked:** in the Swissquote app, a separate app with its own storage (production: bank HSM, server side) | Swissquote | No, one signature of three |
| Client guardians' keys | On each guardian's own phone ("Be a guardian" mode) | Each guardian | No, one signature of three |

## Wallet creation and identity

1. The client is logged in to the Swissquote app (mocked login).
2. *Generate my wallet*: the Swissquote app calls the wallet app, which creates
   the device key and computes the wallet address. Nothing is on-chain yet.
3. Swissquote passes its guardian address at the same moment and becomes
   guardian #1. The wallet is therefore tied to the client's Swissquote
   session from its first second.
4. The first payment deploys the wallet contract, with gas paid by the sponsor.

## Authentication

**In `main`** (payment safeguards merged after testing on the emulator), before
a payment is signed the app applies:

- device authentication (fingerprint, face or screen lock) before every payment;
- an additional explicit confirmation, after a short pause, for a payment above
  a threshold, a payment that takes the day's total above a daily limit, or a
  first payment to a new recipient;
- a blocked-address check before anything is signed (demo list).

On a phone with no screen lock (a fresh emulator) the payment goes through
without the system prompt and the review screen says so: set a screen lock on
the demo phone.

Limit either way: these rules are enforced by the app, not by the wallet
contract, and the key itself is not hardware-bound to biometrics.

## Threat model

**Who could steal funds?**

- *Someone holding the unlocked phone.* This is the main open risk. The key is
  not bound to biometrics in this build, and app-level checks (see
  Authentication) do not stop modified software on the phone. Production: bind
  the key with `setUserAuthenticationRequired`, and enforce limits in the
  wallet contract or with a second signer.
- *Three guardians acting together.* They can install a new key after the
  delay. Mitigations: at least two are people the client chose, each compares
  the request out of band, the delay is public, the client can cancel.
- *A contract bug.* `SwissRecoveryModule` is new and not audited. Its only
  calls into the wallet are "add this key" and "remove that lost key".

**Who could lock the client out?**

- *The client, by losing the phone:* recovery with 3 of 4.
- *Two unavailable guardians:* recovery tolerates one.
- *A thief with the unlocked phone:* can cancel recoveries or change the
  guardians, because the spending key is also the admin key. Production: a
  waiting period on security changes.
- *Swissquote:* it cannot freeze a wallet. If it disappears, the client's three
  guardians recover without it; approvals are signed codes that need no
  Swissquote server.

**Attacker with the client's phone and email.** Email and SMS play no part.
The attacker needs three guardian signatures: Swissquote's identity check
refuses, and the client's own guardians are asked directly by the real client.

**A single dishonest guardian** cannot start or block a recovery: starting one
takes three signatures.

## What is simulated

| Simulated | In production |
|---|---|
| Swissquote login and account | The bank's real client session |
| Swissquote's identity check before it signs | Video identification against the KYC record |
| Swissquote's guardian key inside the Swissquote app | HSM on the bank's servers |
| "Simulated guardians" whose keys sit on the same phone | **Not a security feature.** They exist only so the flow can be shown on one emulator. A judged demo should use real guardians on separate devices. |
| Blocked-address list (hard-coded, on the safeguards branch) | Chainalysis and SECO lists |
| Waiting period of 30 seconds | 48 hours as a starting point |

## Known limits

- EVM chains only; no Bitcoin.
- The device key cannot be backed up. Losing the phone always means recovery
  through guardians, which is the intended trade-off for not syncing the key.
- A zero waiting period is accepted by the contract; the app always sets one.
- Guardians sign a 32-byte hash, not readable typed data.

# Business case for Swissquote

## The problem today

A client who wants to hold their own keys has to withdraw to an outside wallet.
From that moment Swissquote loses sight of the assets, the trades that follow,
and often the client.

## What the published numbers say

| Figure | Value | Source |
|---|---|---|
| Accounts, 30 June 2026 | 1,220,818 | H1 2026 media release |
| Client assets, 30 June 2026 | CHF 96.3 billion | H1 2026 media release |
| Operating income, 2025 | CHF 724.2 million | 2025 report to shareholders |
| Net crypto assets income, 2025 | CHF 85.7 million (crypto trading volume down 12.1%) | 2025 report to shareholders |
| Net crypto assets income, H1 2026 | CHF 14.6 million, against CHF 42.6 million in H2 2025 | H1 2026 media release |

Two things follow.

- **Crypto matters to Swissquote and it is volatile.** It was about 12% of
  operating income in 2025, then fell by roughly two thirds in one half-year.
  That income comes from trading, so it disappears when clients stop trading.
- **A client is worth far more than their crypto fees.** Operating income per
  account was about CHF 626 in 2025 (CHF 724.2 million over 1,156,807 accounts
  at year-end). A client who leaves for self-custody can take the whole
  relationship with them.

## What the wallet changes

| Lever | How |
|---|---|
| **Retention** | The client gets self-custody without leaving. The wallet is created from the Swissquote account, and moving assets back to trade or sell is one step, because ownership is already proven. |
| **New clients** | People who would never open a custodial crypto account can start with the wallet, already identified by a Swiss bank, and become Swissquote clients. |
| **Revenue less tied to trading** | A paid recovery tier (Swissquote as guardian, identity re-verification) is recurring. Conversions between the account and the wallet keep earning the usual margin at Swissquote instead of at a competitor. |
| **Lower costs** | Wallet ownership checks for the Travel Rule become automatic. Assets in the wallet are not in the bank's custody, so they need no cold storage or custody insurance from Swissquote. |
| **Brand** | "Recovery backed by a Swiss bank, which still cannot touch your funds" is something neither a seed-phrase wallet nor an exchange can offer. |

## What it costs

- **Sponsored gas.** Swissquote pays the network fee for its clients' wallets.
  On Base this is a small per-transfer cost, a retention expense like free
  custody.
- **Recovery operations.** One identity re-verification per recovery case, and
  the guardian key in the bank's HSM.
- **Build and audit.** The mobile apps, and an audit of the recovery contract
  before any launch.

## Illustrative first year

Every input in this table is **our assumption**, not a Swissquote figure. It
shows how the numbers combine and should be replaced with real data.

| Input (assumed) | Value |
|---|---|
| Share of accounts that open a wallet | 2% → about 24,400 wallets |
| Clients who would otherwise have left for an outside wallet | 10% of those → about 2,400 |
| Share of wallet users who take a paid recovery tier at CHF 3 a month | 30% → about 7,300 |
| Sponsored transfers per wallet per year, at CHF 0.05 each | 24 |
| Recoveries per year, at CHF 20 of verification cost each | 3% of wallets → about 730 |

| Result | Per year |
|---|---|
| Revenue kept by retaining 2,400 clients at CHF 626 each | about CHF 1.5 million |
| Recovery tier revenue | about CHF 260,000 |
| Sponsored gas | about CHF 29,000 |
| Recovery verification | about CHF 15,000 |

The point of the table is the ratio: retention alone is worth more than thirty
times the running costs. The result is most sensitive to how many clients the wallet
actually keeps, which is the number Swissquote can measure in a pilot.

## Alternatives a client has today

| Option | What it lacks |
|---|---|
| Seed-phrase wallet (for example MetaMask) | No recovery if the phrase is lost; nothing links it to the bank |
| Hardware wallet | A device to buy and keep; the same seed-phrase backup underneath |
| Passkey smart wallet from a crypto company | No identity-based recovery from a regulated bank; not connected to the client's Swissquote account |
| Leaving the assets at an exchange | Not self-custody |

## Risks to the case

- **Regulatory uncertainty.** The custody and anti-money-laundering position
  needs confirming with counsel; see [legal.md](legal.md).
- **Reputation.** A failed or abused recovery would be blamed on the bank even
  though it cannot act alone; see [custody-and-threat-model.md](custody-and-threat-model.md).
- **Coverage.** This version handles EVM assets only. Bitcoin stays custodial
  until a later version; see [custody-and-threat-model.md](custody-and-threat-model.md).

## Sources

- Swissquote, [Report to the Shareholders 2025](https://www.swissquote.com/en/group/annual-report/2025/main-chapters/shareholders). Read at source.
- Swissquote, [H1 2026 media release](https://www.swissquote.com/en/group/investor-relations/financial-reports), 13 August 2026. Figures taken from the release on swissquote.com; check them against the PDF before putting them on a slide.

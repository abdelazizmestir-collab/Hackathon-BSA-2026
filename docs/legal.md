# Swiss legal feasibility

We are engineers, not lawyers. This is our reasoning about how the design
meets Swiss rules, with every source and how far we checked it listed at the
end. It is not legal advice.

Design in one line: the client's device key is the only key that can spend; any
3 of 4 guardians (Swissquote plus three chosen by the client) can replace a lost
key after a waiting period the client can cancel. See
[custody-and-threat-model.md](custody-and-threat-model.md).

## 1. Which side of custody are we on?

The brief's test is who can move the funds.

- **Swissquote cannot move them alone.** It holds no key that can sign a
  payment. Its only key is one recovery vote out of the three required.
- **So the assets are not held in custody by the bank.** They never sit at an
  address Swissquote controls and would not be on its books. FINMA's Guidance
  01/2026 on the custody of cryptobased assets addresses assets that
  supervised institutions hold or have held for clients; here there are none.

We describe the model as **user-controlled spending with collective recovery
authority**, and avoid "legally non-custodial". A quorum that can appoint a new
owner is a kind of control, even if no single member has it.

## 2. Swissquote together with a partner: the grey zone

The brief says control shared between Swissquote and a partner is a grey zone.
In this design there is no partner institution:

- The other three guardians are chosen by the client: their other device and
  people close to them.
- Swissquote holds 1 signature. Three are required, so it always needs two of
  the client's own guardians.
- A quorum cannot transfer anything. It can only install a new key, after a
  waiting period, and the client's current key can cancel it.

**Why we think this is not custody:** Swissquote never has power of disposal,
alone or with another institution. It arises only with two people the client
chose, after a delay, and subject to the client's veto.

**The counter-argument we would put to a lawyer:** Swissquote's signature is
still one of the three that can install a key that spends. A supervisor could
treat that as participation in control.

## 3. Anti-money-laundering status of the service

This point changed our first draft, which called screening "a service to the
client, not an obligation".

Under Art. 4 para. 1 let. b of the Anti-Money Laundering Ordinance, helping to
transfer virtual currencies to a third party is a payment service when the
provider has a permanent business relationship with the client **or** exercises
power of disposal for them. Either condition is enough.

In our design Swissquote has a permanent relationship with the client, provides
the wallet app that builds the client's transfers, and pays their gas. So even
without power of disposal, these transfers very likely fall under this
provision.

What follows:

- Screening and monitoring of these transfers are **obligations**. The check
  belongs before anything is signed, and Swissquote should refuse to sponsor a
  blocked transfer. (Prototype: a hard-coded list in the app, on the safeguards
  branch.)
- The extra burden is small: Swissquote is already a supervised bank and the
  client is already identified.
- When the client transacts with no help from Swissquote (another client
  application and their own gas), Swissquote provides no service for that
  transfer.

## 4. Travel Rule and wallet ownership

FINMA Guidance 02/2019 sets the Swiss practice: a supervised institution may
send tokens only to an external wallet that belongs to its own, already
identified customer, and may receive tokens only from such a customer. FINMA
states that, unlike the FATF standard, this applies without an exception for
unregulated wallets. The customer's control over the wallet has to be verified
by technical means.

**Between the Swissquote account and the client's wallet.** Swissquote is the
sending or receiving institution, so it must have verified ownership. Today
that is manual. With our wallet it is built in: the wallet is generated from
inside the client's Swissquote session, so the bank records `client ↔ wallet`
at creation. A signed ownership proof verified on-chain (ERC-1271 / ERC-6492)
is the production version of that link; the earlier web prototype implements
it.

**When the client pays a third party from the wallet.** The brief asks whether
Swissquote is the sending institution at all.

- The assets never pass through Swissquote, and it cannot execute the transfer
  itself. In that sense it is not sending anything.
- But per section 3, assisting the transfer inside a permanent relationship is
  a regulated payment service, so anti-money-laundering duties apply to it.
- Whether the duty to transmit originator and beneficiary data applies is the
  open question: the recipient is an address with no institution behind it to
  receive the data.

Our position is the cautious one: treat sponsored transfers as in scope. The
originator is known, the recipient address is screened, and the record is kept.

The FATF's March 2026 targeted report on stablecoins and unhosted wallets
points the same way: it treats peer-to-peer transfers through unhosted wallets
as a gap and encourages analytics on unhosted counterparties. It creates no
binding duty by itself.

## 5. Data protection

Nothing personal goes on-chain: only addresses and public keys. Identity
records stay with Swissquote. Guardian names are stored only on the client's
phone; on-chain a guardian is just an address.

## Questions for a lawyer

1. Does one recovery vote out of three, held by the bank, affect the custody
   or balance-sheet treatment of the client's assets?
2. Does being an automatic guardian of every client wallet change the
   analysis compared with an optional one?
3. Does the Travel Rule's data-transmission duty apply to a sponsored transfer
   to an unhosted third-party address?
4. Does sponsoring the gas change the analysis in section 3?

## Sources and how far we checked them

| Source | Used for | Checked |
|---|---|---|
| [Anti-Money Laundering Ordinance (AMLO, SR 955.01)](https://www.fedlex.admin.ch/eli/cc/2015/791/de), Art. 4 para. 1 let. b, status as of 1 October 2026 | Section 3 | **Read at source** on Fedlex, in German. The English text of the current version was not yet published. Wording in force since 1 August 2021. |
| [FINMA Guidance 02/2019, "Payments on the blockchain"](https://www.finma.ch/en/news/2019/08/20190826-mm-kryptogwg), 26 August 2019 | Section 4 | **FINMA's press release read at source.** The "technical means" requirement comes from the guidance itself as quoted by commentators; we did not open the PDF. |
| [FINMA Guidance 01/2026, "Custody of cryptobased assets"](https://www.finma.ch/en/news/2026/01/20260112-mm-am-01-26/), 12 January 2026 | Section 1 | **Existence, date and subject confirmed** on FINMA's press release. We did not read the guidance PDF; our summary of its scope relies on law-firm notes. |
| [FATF targeted report on stablecoins and unhosted wallets](https://www.fatf-gafi.org/en/publications/Virtualassets/targeted-report-stablecoins-unhosted-wallets.html), 3 March 2026 | Section 4 | **Not verified at source.** The FATF site blocked automated access. Title and date match the FATF page listing and several commentaries. Open the link before citing it. |
| DLT Act (2021), SECO sanctions lists, AMLO-FINMA | Not relied on | Not checked. Earlier drafts mentioned them; we removed the claims rather than cite them unverified. |

## Slide summary

1. Swissquote cannot move client funds alone. Recovery always needs two
   guardians the client chose.
2. Wallet ownership is proven cryptographically at creation, so the Travel
   Rule check between account and wallet is automatic.
3. Swissquote still assists the client's transfers, so it screens them. We
   treat that as a duty, not a courtesy.

# Judge Q&A

Short answers to say out loud. The first sentence is the answer; the rest is
support if they ask for more.

## Custody and security

**Why not MPC (splitting the key)?**
Because Swissquote would have to co-sign every payment. It could then block
the client, and with the holder of another piece it could sign without the
client. Here Swissquote signs no payment at all. MPC is our plan for Bitcoin.

**Why is the bank a guardian?**
It is the one party that already knows who the client is and can check it again
without any device or password. It gets one signature of the three needed.

**Could Swissquote move the funds, alone or with a partner?**
Alone, no. There is no partner institution: the other three guardians are
chosen by the client. Swissquote always needs two of the client's own
guardians, a waiting period, and the client can cancel.

**What if Swissquote disappears?**
The client's three guardians recover the wallet without it. Approvals are
signed codes exchanged between phones; no Swissquote server is involved.

**Where is the key?**
In the phone's secure chip. It cannot be exported and is not copied to Google
or Apple. That is why losing the phone goes through the guardians.

**Is it a passkey?**
It is a device-bound P-256 key that signs in the passkey (WebAuthn) format, so
the wallet contract verifies it the same way. It is not created through the
phone's passkey manager and is not synced.

**What about a stolen, unlocked phone?**
That is our main open risk and we say so. The production answer is to bind the
key to the fingerprint in hardware and to enforce limits in the wallet
contract or with a second signer.

**How does a large payment differ from a small one?**
Device authentication for every payment, plus an extra confirmation after a
pause for a large amount (1,000 USDC / 0.25 ETH), a 24-hour total (2,000 USDC /
0.5 ETH), or a new recipient. These are app rules, not wallet-contract rules.

**What stops an attacker with the phone and the email from recovering?**
Email and SMS play no part. The attacker needs three guardian signatures:
Swissquote's identity check refuses, and the client's own guardians hear from
the real client.

**Can one dishonest guardian block a recovery?**
No. Starting a recovery takes three signatures, so one guardian can neither
start nor block one.

**What is the weakest point?**
Three things: an unlocked phone can spend; the recovery contract is new and
unaudited; and the simulated guardians in the app are a demo shortcut, not a
security feature.

## Scope and honesty

**Does it support Bitcoin?**
No. Ethereum-style chains only in this version.

**What is simulated?**
The Swissquote login, its identity check, its guardian key (in the app; on a
server in production), market prices, and the optional simulated guardians.
The wallet, the key, the payments, the gas sponsorship and the recovery
contract are real, on Base Sepolia.

**What did AI do?**
We worked with Claude Code; its commits are marked in the history. People made
the design decisions and ran and checked the app.

## Legal and business

**Is this custody under Swiss law?**
We think not, because Swissquote can never move the funds alone. We do not
claim it is settled; a group that can appoint a new owner is a kind of control.

**How do you handle the Travel Rule?**
The wallet is generated from inside the client's Swissquote session, so the
bank knows whose wallet it is from the start. A Swiss bank may only send crypto
to a wallet it has verified as its client's; here that check is built in.

**Who pays the fees?**
Swissquote, through a paymaster, as a cost of keeping the client.

**How does Swissquote make money?**
Mostly by not losing the client: about CHF 626 of operating income per account
per year in 2025, far more than crypto fees alone.

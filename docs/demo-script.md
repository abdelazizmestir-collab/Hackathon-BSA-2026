# Demo and pitch (target: 3 minutes)

Rehearse it twice and record one full run as the backup video.

## Prep (before going on stage)

- [ ] Demo phone or emulator: both apps installed, Pimlico key set in
      `local.properties`, network Base Sepolia.
- [ ] Wallet generated, funded with test USDC and a little test ETH.
- [ ] **Two other devices** (teammates' phones or emulators) with SwissWallet
      open on *Be a guardian*. Each has given its guardian code to the demo
      phone. With Swissquote that makes three real guardians on three devices.
- [ ] Guardians added and *Activate protection (3 of 4)* done, so the recovery
      contract is already deployed.
- [ ] Check that copy and paste of codes works between the devices. On one
      emulator Android Studio reported the clipboard as unavailable.
- [ ] A real phone with a camera and internet, for the login QR code.
- [ ] Explorer tab open on the wallet address. Backup video on the laptop.

**Do not use "Approve with simulated guardians" in front of the judges.** Those
keys sit on the same phone as the wallet, which the challenge does not allow
for a key holder.

## Flow

1. **Problem (15 s).** "Today, self-custody means leaving Swissquote."
2. **Log in (15 s).** In the Swissquote app: *Login*. A QR code appears. Scan it
   with a real phone, read the 6-digit code the phone shows, type it in. The
   client is now logged in with a second device.
3. **Create (20 s).** Wallet tab, *Generate my wallet*.
   The key is created in the phone's secure chip. No seed phrase. Swissquote
   becomes guardian #1 automatically.
4. **Send (25 s).** In SwissWallet: Send, address, amount, confirm. Zero gas
   paid. Show the transaction on the explorer.
5. **Guardians (15 s).** Profile, Guardians: Swissquote plus the client's
   other device, a person close to them and an independent institution. 3 of 4.
6. **Lose the phone (10 s).** Profile, *Simulate lost phone*: the key is deleted.
7. **Recover (50 s).** *I lost my phone*, new key, request code. Swissquote
   runs its identity check and signs. Two guardians sign on their own devices
   and send back their codes. 3 of 3, *Start recovery*, 30 seconds (48 hours in
   production), *Finish recovery*. Same address, same funds, new key.
8. **During the 30 seconds (in parallel).** Show the who-can-do-what table:
   Swissquote alone cannot recover; the client can cancel.
9. **Swissquote is gone (15 s, say it).** The three client guardians can do
   the same without Swissquote: approvals are signed codes, with no Swissquote
   server involved.
10. **Close (15 s).** What is real, what is simulated, what Swissquote gains.

## Slides

- **Who can do what:** use the table in
  [custody-and-threat-model.md](custody-and-threat-model.md). One line under
  it: *Swissquote has no partner. The client chooses the other three guardians,
  possibly an independent institution, and Swissquote always needs at least one
  of the client's own guardians.*
- **Evidence:** the four transactions of a complete on-chain recovery and the
  verified contract source, from [submission.md](submission.md), section 8.

## One-minute summary

Today a Swissquote client who wants to hold their own keys has to leave. With
this wallet they generate it from inside the Swissquote app. The key lives in
their phone's secure chip and is the only thing that can spend. Fees are paid
for them. If they lose the phone, three of four guardians bring them back to
the same wallet after a waiting period they can cancel: Swissquote is one
guardian, and the other three are chosen by the client. Swissquote alone can do
nothing, and if Swissquote disappears the client's own guardians are enough.

## Say it openly

1. **Authentication.** "Every payment asks for the phone's fingerprint or PIN,
   and large or first-time payments need a second confirmation. These are app
   rules: the key is not yet bound to the fingerprint in hardware."
2. **Simulated parts.** "Swissquote's identity check and its guardian key in
   the app are simulated; in production the key is on a bank server. The
   simulated guardians are a one-emulator shortcut, not part of the design."
3. **Checks before signing.** "This app has a blocked-address list. Sanctions
   screening against real lists and transaction simulation were built in our
   earlier web prototype."

If asked what is not finished: limits enforced by the wallet contract and a
second signer for large amounts, the key bound to biometrics in hardware, an
audit of the recovery contract, and Bitcoin.

## If something fails on stage

- Play the backup video for that scene and keep talking.
- The earlier web prototype (create, send, recover with separate guardian
  services) is at https://sq-wallet-hackathon-demo.vercel.app, repository
  `rayenea/swissquote-wallet`.

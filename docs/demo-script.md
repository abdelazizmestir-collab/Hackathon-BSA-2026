# Demo script (target: 3 minutes)

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
- [ ] Explorer tab open on the wallet address. Backup video on the laptop.

**Do not use "Approve with simulated guardians" in front of the judges.** Those
keys sit on the same phone as the wallet, which the challenge does not allow
for a key holder.

## Flow

1. **Problem (15 s).** "Today, self-custody means leaving Swissquote."
2. **Create (20 s).** In the Swissquote app: Wallet tab, *Generate my wallet*.
   The key is created in the phone's secure chip. No seed phrase. Swissquote
   becomes guardian #1 automatically.
3. **Send (25 s).** In SwissWallet: Send, address, amount, confirm. Zero gas
   paid. Show the transaction on the explorer.
4. **Guardians (15 s).** Profile, Guardians: Swissquote plus the client's
   other device and two people close to them. 3 of 4.
5. **Lose the phone (10 s).** Profile, *Simulate lost phone*: the key is deleted.
6. **Recover (50 s).** *I lost my phone*, new key, request code. Swissquote
   runs its identity check and signs. Two guardians sign on their own devices
   and send back their codes. 3 of 3, *Start recovery*, 30 seconds (48 hours in
   production), *Finish recovery*. Same address, same funds, new key.
7. **During the 30 seconds (in parallel).** Show the who-can-do-what table:
   Swissquote alone cannot recover; the client can cancel.
8. **Swissquote is gone (15 s, say it).** The three client guardians can do
   the same without Swissquote: approvals are signed codes, with no Swissquote
   server involved.
9. **Close (15 s).** What is real, what is simulated, what Swissquote gains.

## Say it openly

- Swissquote's identity check and its guardian key in the app are simulated;
  in production the key is on a bank server.
- Device authentication before signing and payment limits: say whether the
  build on stage includes them (branch `feat/payment-safeguards`).

## If something fails on stage

- Play the backup video for that scene and keep talking.
- The earlier web prototype (create, send, recover with separate guardian
  services) is at https://sq-wallet-hackathon-demo.vercel.app, repository
  `rayenea/swissquote-wallet`.

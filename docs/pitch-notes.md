# Pitch notes

Text ready to paste into slides or to say. Longer answers are in
[judge-qa.md](judge-qa.md).

## Slide: who can do what

| Who | Spend | Replace a lost key |
|---|---|---|
| **Client**, with the device key | Yes | Can cancel any recovery during the waiting period |
| **Swissquote alone** | No | No |
| **Swissquote + one of the client's guardians** | No | No |
| **Swissquote + two of the client's guardians** | No | Yes, after the waiting period, unless the client cancels |
| **Swissquote + the independent institution** (if the client picked one) | No | No: one of the client's own guardians is still needed |
| **The client's three guardians, without Swissquote** | No | Yes, after the waiting period |

One line under the table: *Swissquote has no partner. The client chooses the
other three guardians, possibly an independent institution, and Swissquote
always needs at least one of the client's own guardians.*

Do not reuse the earlier version of this slide, where the institution was
Swissquote's partner: here it is an option the client chooses.

## Slide: evidence

A complete recovery, on-chain, this morning (Base Sepolia, wallet
`0x3038…0f11`):

1. 07:26 protection activated, 3 of 4 guardians, 30 s delay
2. 07:30 recovery started with 3 guardian signatures
3. 07:31 recovery finished: new key added, lost key removed
4. 07:31 the new key signs its first operation

Same address before and after. Links are in
[submission.md](submission.md), section 8. The recovery contract's source is
verified on Sourcify, exact match.

## Three things to say openly

1. **Fingerprint.** "In the build on stage, the key signs without asking for a
   fingerprint, so that it runs on an emulator. Binding the key to the
   fingerprint is one setting on a real phone." If the safeguards branch was
   merged, say instead: "every payment asks for the phone's fingerprint or PIN,
   and large or first-time payments need a second confirmation."
2. **Simulated guardians.** "The app has simulated guardians so the flow can
   run on a single emulator. They are a demo shortcut, not part of the design:
   in the design each guardian's key is on that guardian's own phone, as you
   just saw."
3. **Checks before signing.** "Sanctions screening and transaction simulation
   were built in our earlier web prototype. In this app they are" (choose)
   "not ported yet" / "limited to a blocked-address list."

## One-minute summary

Today a Swissquote client who wants to hold their own keys has to leave. With
this wallet they generate it from inside the Swissquote app. The key lives in
their phone's secure chip and is the only thing that can spend. Fees are paid
for them. If they lose the phone, three of four guardians bring them back to
the same wallet after a waiting period they can cancel: Swissquote is one
guardian, and the other three are chosen by the client, possibly including an
independent institution. Swissquote alone can do
nothing, and if Swissquote disappears the client's own guardians are enough.

## If asked what is not finished

- Limits enforced by the wallet contract itself, and a second signer for large
  amounts.
- The device key bound to biometrics in hardware.
- An audit of the recovery contract.
- Bitcoin.

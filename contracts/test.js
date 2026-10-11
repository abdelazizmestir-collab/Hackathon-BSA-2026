// End-to-end tests of SwissRecoveryModule on an in-process EVM (ganache).
const assert = require('assert');
const ganache = require('ganache');
const { BrowserProvider, ContractFactory, Contract, Wallet, AbiCoder, keccak256, getBytes, randomBytes, hexlify, Interface } = require('ethers');
const fs = require('fs');

const coder = AbiCoder.defaultAbiCoder();
const load = (n) => JSON.parse(fs.readFileSync(`build/${n}.json`));
const NO_REVOKE = (1n << 256n) - 1n;

async function expectRevert(p, name, iface) {
  try { const r = await p; if (r && r.wait) await r.wait(); } catch (e) {
    if (name && iface) {
      const sel = iface.getError(name).selector.slice(2);
      const blob = JSON.stringify(e, (k, v) => (typeof v === 'bigint' ? v.toString() : v)) + String(e?.message);
      assert.ok(blob.toLowerCase().includes(sel.toLowerCase()), `expected ${name} (${sel}) in revert, got: ${String(e?.message).slice(0, 200)}`);
    }
    return;
  }
  throw new Error(`expected revert ${name}`);
}

(async () => {
  const provider = new BrowserProvider(ganache.provider({ logging: { quiet: true }, chain: { chainId: 1337 } }));
  const deployer = await provider.getSigner(0);

  const M = load('SwissRecoveryModule');
  const W = load('MockSmartWallet');
  const moduleC = await new ContractFactory(M.abi, M.bytecode, deployer).deploy();
  await moduleC.waitForDeployment();
  const moduleAddr = await moduleC.getAddress();
  const iface = new Interface(M.abi);

  // Wallet owners: [passkey #0 (64 bytes), module (address, 32 bytes)]
  const lostX = hexlify(randomBytes(32)), lostY = hexlify(randomBytes(32));
  const owner0 = coder.encode(['bytes32', 'bytes32'], [lostX, lostY]);
  const owner1 = coder.encode(['address'], [moduleAddr]);
  const wallet = await new ContractFactory(W.abi, W.bytecode, deployer).deploy([owner0, owner1]);
  await wallet.waitForDeployment();
  const walletAddr = await wallet.getAddress();
  const viaWallet = (fn, args) => wallet.call(moduleAddr, iface.encodeFunctionData(fn, args));

  // 4 guardians: Swissquote, my device, close one #1, close one #2
  const guardians = [0, 1, 2, 3].map(() => Wallet.createRandom());
  const outsider = Wallet.createRandom();

  // -- setup: 3 of 4, 30 s
  await (await viaWallet('setupGuardians', [guardians.map((g) => g.address), 3, 30])).wait();
  let cfg = await moduleC.configOf(walletAddr);
  assert.deepStrictEqual([cfg[0], cfg[1], cfg[2], cfg[3]], [3n, 30n, 1n, 4n]);
  assert.deepStrictEqual([...(await moduleC.guardiansOf(walletAddr))], guardians.map((g) => g.address));
  console.log('✔ setupGuardians 3-of-4 / 30 s');

  // invalid configs
  await expectRevert(viaWallet('setupGuardians', [[guardians[0].address], 2, 30]), 'InvalidConfig', iface);
  await expectRevert(viaWallet('setupGuardians', [[guardians[0].address, guardians[0].address], 1, 30]), 'InvalidConfig', iface);
  console.log('✔ rejects invalid configs');

  // -- new device key
  const newX = hexlify(randomBytes(32)), newY = hexlify(randomBytes(32));
  const sign = async (signer, hash) => signer.signMessage(getBytes(hash)); // EIP-191

  let h = await moduleC.recoveryHash(walletAddr, newX, newY, 0);
  // Reference implementation of the hash (same as the Kotlin one)
  const TYPEHASH = keccak256(Buffer.from('SwissRecovery(address wallet,bytes32 x,bytes32 y,uint256 revokeIndex,uint256 nonce,uint256 chainId,address module)'));
  const ref = keccak256(coder.encode(['bytes32', 'address', 'bytes32', 'bytes32', 'uint256', 'uint256', 'uint256', 'address'], [TYPEHASH, walletAddr, newX, newY, 0, 1, 1337, moduleAddr]));
  assert.strictEqual(h, ref);
  console.log('✔ recoveryHash matches off-chain computation');

  // 2 signatures + duplicate + outsider → not enough
  const s0 = await sign(guardians[0], h), s1 = await sign(guardians[1], h), s2 = await sign(guardians[2], h);
  const sOut = await sign(outsider, h);
  await expectRevert(moduleC.initiateRecovery(walletAddr, newX, newY, 0, [s0, s1, s1, sOut]), 'NotEnoughApprovals', iface);
  console.log('✔ 2 guardians + duplicate + outsider is NOT enough');

  // 3 valid → pending
  await (await moduleC.initiateRecovery(walletAddr, newX, newY, 0, [s2, sOut, s0, s1])).wait();
  let p = await moduleC.pendingOf(walletAddr);
  assert.strictEqual(p[4], true);
  console.log('✔ 3-of-4 starts the recovery timer');

  // replay of the same signatures after nonce bump must fail
  await expectRevert(viaWallet('cancelRecovery', []).then(async (tx) => { await tx.wait(); return moduleC.initiateRecovery(walletAddr, newX, newY, 0, [s0, s1, s2]); }), 'NotEnoughApprovals', iface);
  console.log('✔ owner can cancel, and old signatures cannot be replayed');

  // new round with fresh signatures
  h = await moduleC.recoveryHash(walletAddr, newX, newY, 0);
  const sigs = await Promise.all([1, 2, 3].map((i) => sign(guardians[i], h)));
  await (await moduleC.initiateRecovery(walletAddr, newX, newY, 0, sigs)).wait();
  await expectRevert(moduleC.finalizeRecovery(walletAddr), 'TooEarly', iface);
  console.log('✔ cannot finalize before the delay');

  await provider.send('evm_increaseTime', [31]);
  await provider.send('evm_mine', []);
  await (await moduleC.finalizeRecovery(walletAddr, { gasLimit: 1_000_000 })).wait();
  const newOwner = coder.encode(['bytes32', 'bytes32'], [newX, newY]);
  assert.strictEqual(await wallet.isOwnerBytes(newOwner), true);
  assert.strictEqual(await wallet.isOwnerBytes(owner0), false);
  assert.strictEqual(await wallet.isOwnerBytes(owner1), true); // module kept
  assert.strictEqual(await wallet.ownerAtIndex(2), newOwner);
  console.log('✔ finalize: new passkey added at index 2, lost passkey #0 revoked, module kept');

  // revoking a non-passkey owner (the module at index 1) is ignored
  const x3 = hexlify(randomBytes(32)), y3 = hexlify(randomBytes(32));
  h = await moduleC.recoveryHash(walletAddr, x3, y3, 1);
  await (await moduleC.initiateRecovery(walletAddr, x3, y3, 1, await Promise.all([0, 1, 2].map((i) => sign(guardians[i], h))))).wait();
  await provider.send('evm_increaseTime', [31]); await provider.send('evm_mine', []);
  await (await moduleC.finalizeRecovery(walletAddr, { gasLimit: 1_000_000 })).wait();
  assert.strictEqual(await wallet.isOwnerBytes(owner1), true);
  console.log('✔ module never removes an address owner (cannot lock itself out)');

  // NO_REVOKE path
  const x4 = hexlify(randomBytes(32)), y4 = hexlify(randomBytes(32));
  h = await moduleC.recoveryHash(walletAddr, x4, y4, NO_REVOKE);
  await (await moduleC.initiateRecovery(walletAddr, x4, y4, NO_REVOKE, await Promise.all([0, 1, 3].map((i) => sign(guardians[i], h))))).wait();
  await provider.send('evm_increaseTime', [31]); await provider.send('evm_mine', []);
  await (await moduleC.finalizeRecovery(walletAddr, { gasLimit: 1_000_000 })).wait();
  assert.strictEqual(await wallet.isOwnerBytes(newOwner), true);
  console.log('✔ NO_REVOKE keeps existing passkeys');

  console.log('\nALL TESTS PASSED');
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });

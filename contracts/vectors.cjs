// Reference vectors (computed with viem) used by the Kotlin unit tests.
const { encodeFunctionData, encodeAbiParameters, keccak256, toHex, parseAbi, pad, concat } = require('viem');
const { getUserOperationHash } = require('viem/account-abstraction');
const fs = require('fs');
const dep = JSON.parse(fs.readFileSync('build/deployment.json'));

const x = '0x' + '11'.repeat(32), y = '0x' + '22'.repeat(32);
const moduleAddr = dep.address;
const ownerPasskey = encodeAbiParameters([{ type: 'bytes32' }, { type: 'bytes32' }], [x, y]);
const ownerModule = pad(moduleAddr); // abi.encode(address)
const g = ['0x1000000000000000000000000000000000000001', '0x2000000000000000000000000000000000000002', '0x3000000000000000000000000000000000000003', '0x4000000000000000000000000000000000000004'];
const wallet = '0xA0Cf798816D4b9b9866b5330EEa46a18382f251e';

const csw = parseAbi([
  'function createAccount(bytes[] owners, uint256 nonce)',
  'function getAddress(bytes[] owners, uint256 nonce)',
  'function execute(address target, uint256 value, bytes data)',
  'struct Call { address target; uint256 value; bytes data; }',
  'function executeBatch(Call[] calls)',
]);
const mod = parseAbi([
  'function setupGuardians(address[] guardians, uint64 threshold, uint64 delay)',
  'function initiateRecovery(address wallet, bytes32 x, bytes32 y, uint256 revokeIndex, bytes[] signatures)',
  'function finalizeRecovery(address wallet)',
]);
const sig1 = '0x' + 'aa'.repeat(65), sig2 = '0x' + 'bb'.repeat(65);

const userOp = {
  sender: wallet, nonce: 5n, initCode: '0x', callData: '0x1234',
  callGasLimit: 100000n, verificationGasLimit: 200000n, preVerificationGas: 50000n,
  maxFeePerGas: 1000000000n, maxPriorityFeePerGas: 100000000n,
  paymasterAndData: '0xdeadbeef', signature: '0x',
};
const TYPEHASH = keccak256(toHex('SwissRecovery(address wallet,bytes32 x,bytes32 y,uint256 revokeIndex,uint256 nonce,uint256 chainId,address module)'));

const vectors = {
  moduleAddress: moduleAddr,
  createAccount: encodeFunctionData({ abi: csw, functionName: 'createAccount', args: [[ownerPasskey, ownerModule], 0n] }),
  execute: encodeFunctionData({ abi: csw, functionName: 'execute', args: [g[0], 7n, '0x1234'] }),
  executeBatch: encodeFunctionData({ abi: csw, functionName: 'executeBatch', args: [[{ target: g[0], value: 7n, data: '0x1234' }, { target: g[1], value: 0n, data: '0x' }]] }),
  setupGuardians: encodeFunctionData({ abi: mod, functionName: 'setupGuardians', args: [g, 3n, 30n] }),
  initiateRecovery: encodeFunctionData({ abi: mod, functionName: 'initiateRecovery', args: [wallet, x, y, 0n, [sig1, sig2]] }),
  signatureWrapper: encodeAbiParameters(
    [{ type: 'tuple', components: [{ name: 'ownerIndex', type: 'uint256' }, { name: 'signatureData', type: 'bytes' }] }],
    [{ ownerIndex: 1n, signatureData: '0x' + 'cc'.repeat(65) }]),
  webAuthnAuth: encodeAbiParameters(
    [{ type: 'tuple', components: [
      { name: 'authenticatorData', type: 'bytes' }, { name: 'clientDataJSON', type: 'string' },
      { name: 'challengeIndex', type: 'uint256' }, { name: 'typeIndex', type: 'uint256' },
      { name: 'r', type: 'uint256' }, { name: 's', type: 'uint256' }] }],
    [{ authenticatorData: '0x' + '01'.repeat(37), clientDataJSON: '{"type":"webauthn.get"}', challengeIndex: 23n, typeIndex: 1n, r: 5n, s: 6n }]),
  userOpHashV06: getUserOperationHash({ userOperation: userOp, entryPointAddress: '0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789', entryPointVersion: '0.6', chainId: 84532 }),
  recoveryHash: keccak256(encodeAbiParameters(
    [{ type: 'bytes32' }, { type: 'address' }, { type: 'bytes32' }, { type: 'bytes32' }, { type: 'uint256' }, { type: 'uint256' }, { type: 'uint256' }, { type: 'address' }],
    [TYPEHASH, wallet, x, y, 0n, 1n, 84532n, moduleAddr])),
};
fs.writeFileSync('build/vectors.json', JSON.stringify(vectors, null, 2));
console.log(JSON.stringify(vectors, null, 1).slice(0, 1500));

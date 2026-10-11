// Compiles the REAL Coinbase Smart Wallet + EntryPoint v0.6 + SwissRecoveryModule for the local end-to-end test.
const fs = require('fs');
const path = require('path');
const solc = require('solc');

const remaps = [
  ['account-abstraction/', 'node_modules/@account-abstraction/contracts/'],
  ['solady/', 'node_modules/solady/src/'],
  ['webauthn-sol/', 'vendor/webauthn/'],
  ['FreshCryptoLib/', 'vendor/fcl/'],
  ['openzeppelin-contracts/contracts/', 'node_modules/@openzeppelin/contracts/'],
  ['@openzeppelin/contracts/', 'node_modules/oz4/'],
];
function findImports(p) {
  for (const [from, to] of remaps) if (p.startsWith(from)) p = to + p.slice(from.length);
  const candidates = [p, path.join('vendor/csw', p), path.join('contracts', p)];
  for (const c of candidates) if (fs.existsSync(c)) return { contents: fs.readFileSync(c, 'utf8') };
  return { error: 'not found: ' + p };
}

const sources = {
  'CoinbaseSmartWallet.sol': { content: fs.readFileSync('vendor/csw/CoinbaseSmartWallet.sol', 'utf8') },
  'CoinbaseSmartWalletFactory.sol': { content: fs.readFileSync('vendor/csw/CoinbaseSmartWalletFactory.sol', 'utf8') },
  'SwissRecoveryModule.sol': { content: fs.readFileSync('contracts/SwissRecoveryModule.sol', 'utf8') },
  'EntryPoint.sol': { content: 'import "account-abstraction/core/EntryPoint.sol";' },
};
const input = {
  language: 'Solidity',
  sources,
  settings: {
    optimizer: { enabled: true, runs: 200 },
    viaIR: true,
    evmVersion: 'shanghai',
    outputSelection: { '*': { '*': ['abi', 'evm.bytecode.object'] } },
  },
};
const out = JSON.parse(solc.compile(JSON.stringify(input), { import: findImports }));
const errs = (out.errors || []).filter((e) => e.severity === 'error');
errs.forEach((e) => console.log(e.formattedMessage));
if (errs.length) process.exit(1);
fs.mkdirSync('build/e2e', { recursive: true });
for (const [file, cs] of Object.entries(out.contracts)) for (const [name, c] of Object.entries(cs)) {
  if (!['CoinbaseSmartWallet', 'CoinbaseSmartWalletFactory', 'EntryPoint', 'SwissRecoveryModule'].includes(name)) continue;
  fs.writeFileSync(`build/e2e/${name}.json`, JSON.stringify({ abi: c.abi, bytecode: '0x' + c.evm.bytecode.object }));
  console.log('built', name, (c.evm.bytecode.object.length / 2) | 0, 'bytes');
}

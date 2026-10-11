// Compiles the contracts with solc-js and writes build/*.json + deployment info.
const fs = require('fs');
const path = require('path');
const solc = require('solc');
const { keccak256, toUtf8Bytes, getCreate2Address } = require('ethers');

const sources = {};
for (const f of fs.readdirSync('contracts')) sources[f] = { content: fs.readFileSync(path.join('contracts', f), 'utf8') };

const input = {
  language: 'Solidity',
  sources,
  settings: {
    optimizer: { enabled: true, runs: 200 },
    evmVersion: 'paris', // no PUSH0 → deployable on any EVM chain
    outputSelection: { '*': { '*': ['abi', 'evm.bytecode.object', 'evm.deployedBytecode.object'] } },
  },
};
const out = JSON.parse(solc.compile(JSON.stringify(input)));
const errors = (out.errors || []).filter((e) => e.severity === 'error');
for (const e of out.errors || []) console.log(e.severity.toUpperCase(), e.formattedMessage);
if (errors.length) process.exit(1);

fs.mkdirSync('build', { recursive: true });
for (const file of Object.keys(out.contracts)) {
  for (const [name, c] of Object.entries(out.contracts[file])) {
    fs.writeFileSync(`build/${name}.json`, JSON.stringify({ abi: c.abi, bytecode: '0x' + c.evm.bytecode.object }, null, 2));
  }
}

// Deterministic deployment through Arachnid's CREATE2 proxy (present on Sepolia & Base Sepolia).
const DEPLOYER = '0x4e59b44847b379578588920cA78FbF26c0B4956C';
const SALT = keccak256(toUtf8Bytes('SwissRecoveryModule.v1'));
const initCode = '0x' + out.contracts['SwissRecoveryModule.sol'].SwissRecoveryModule.evm.bytecode.object;
const address = getCreate2Address(DEPLOYER, SALT, keccak256(initCode));
const info = { deployer: DEPLOYER, salt: SALT, initCodeHash: keccak256(initCode), address, initCodeBytes: (initCode.length - 2) / 2 };
fs.writeFileSync('build/deployment.json', JSON.stringify(info, null, 2));
console.log('compiled ✔', info);

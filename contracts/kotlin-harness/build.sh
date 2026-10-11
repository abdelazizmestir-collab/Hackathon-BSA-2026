#!/usr/bin/env bash
# Compiles the app's REAL Kotlin code (ABI encoder, Coinbase Smart Wallet helpers, WebAuthn assertion,
# userOpHash, recovery hash…) + tiny shims for web3j/org.json into a CLI used by ../e2e.cjs.
# Needs `kotlinc` (2.x) on the PATH. Usage: ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
APP=../../swisswallet/src/main/java/com/example/swisswallet
CORE=../../core/src/main/java/com/example/core
COROUTINES=$(dirname "$(command -v kotlinc)")/../lib/kotlinx-coroutines-core-jvm.jar
kotlinc -cp "$COROUTINES" shims/*.kt Main.kt \
  $CORE/recovery/Recovery.kt \
  $APP/data/aa/Abi.kt $APP/data/aa/CoinbaseSmartAccount.kt $APP/data/aa/RecoveryModule.kt \
  $APP/data/aa/RecoveryModuleBytecode.kt $APP/data/aa/UserOperations.kt \
  $APP/data/key/WebAuthnAssertion.kt $APP/domain/aa/AccountAbstraction.kt \
  -include-runtime -d harness.jar
echo "harness.jar built"

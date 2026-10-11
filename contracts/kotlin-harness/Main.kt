import com.example.core.recovery.RecoveryRequest
import com.example.swisswallet.data.aa.Abi
import com.example.swisswallet.data.aa.CoinbaseSmartAccount
import com.example.swisswallet.data.aa.Hex
import com.example.swisswallet.data.aa.RecoveryModule
import com.example.swisswallet.data.aa.UserOperationHasher
import com.example.swisswallet.data.key.WebAuthnAssertion
import com.example.swisswallet.domain.aa.UserOperation
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

fun big(s: String) = if (s.startsWith("0x")) BigInteger(s.removePrefix("0x").ifEmpty { "0" }, 16) else BigInteger(s)

fun main(args: Array<String>) {
    val a = args.drop(1)
    val out: String = when (args[0]) {
        "keygen" -> {
            val kpg = KeyPairGenerator.getInstance("EC"); kpg.initialize(ECGenParameterSpec("secp256r1"))
            val kp = kpg.generateKeyPair(); val pub = kp.public as ECPublicKey
            listOf(Hex.of(kp.private.encoded), Hex.of(WebAuthnAssertion.coord(pub.w.affineX)), Hex.of(WebAuthnAssertion.coord(pub.w.affineY))).joinToString(" ")
        }
        "initcode" -> Hex.of(Hex.bytes(a[0]) + CoinbaseSmartAccount.createAccountCall(
            listOf(CoinbaseSmartAccount.passkeyOwner(Hex.bytes(a[1]), Hex.bytes(a[2])), CoinbaseSmartAccount.addressOwner(a[3])), BigInteger.ZERO))
        "execute" -> Hex.of(Abi.encodeCall("execute(address,uint256,bytes)", Abi.Arg.Address(a[0]), Abi.Arg.Uint(big(a[1])), Abi.Arg.Bytes(Hex.bytes(a[2]))))
        "setup" -> Hex.of(RecoveryModule.setupGuardians(a[0].split(","), a[1].toInt(), a[2].toLong()))
        "initiate" -> Hex.of(RecoveryModule.initiateRecovery(a[0], Hex.bytes(a[1]), Hex.bytes(a[2]), big(a[3]), a[4].split(",").map { Hex.bytes(it) }))
        "finalize" -> Hex.of(RecoveryModule.finalizeRecovery(a[0]))
        "recoveryhash" -> Hex.of(RecoveryRequest(a[0], Hex.bytes(a[1]), Hex.bytes(a[2]), big(a[3]), big(a[4]), a[5].toLong(), a[6], "test").hash())
        "module" -> RecoveryModule.address
        // helper account of the new phone: owners = [new passkey] only
        "helperinit" -> Hex.of(Hex.bytes(a[0]) + CoinbaseSmartAccount.createAccountCall(listOf(CoinbaseSmartAccount.passkeyOwner(Hex.bytes(a[1]), Hex.bytes(a[2]))), BigInteger.ZERO))
        "deploymodule" -> Hex.of(RecoveryModule.deployCalldata())
        // Exactly what SocialRecoveryService.activateProtection sends: executeBatch([CREATE2 deploy module, setupGuardians])
        "activate" -> Hex.of(dummyAccount().encodeExecuteBatch(listOf(
            com.example.swisswallet.domain.aa.Call("0x4e59b44847b379578588920cA78FbF26c0B4956C", BigInteger.ZERO, RecoveryModule.deployCalldata()),
            com.example.swisswallet.domain.aa.Call(RecoveryModule.address, BigInteger.ZERO, RecoveryModule.setupGuardians(a[0].split(","), a[1].toInt(), a[2].toLong())),
        )))
        "sign" -> {
            // pkcs8 ownerIndex entryPoint chainId sender nonce initCode callData cgl vgl pvg maxFee maxPrio pmd
            val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Hex.bytes(a[0])))
            val op = UserOperation(a[4], big(a[5]), Hex.bytes(a[6]), Hex.bytes(a[7]), big(a[8]), big(a[9]), big(a[10]), big(a[11]), big(a[12]), Hex.bytes(a[13]))
            val h = UserOperationHasher.hash(op, a[2], a[3].toLong())
            val sig = WebAuthnAssertion.build(h) { msg -> Signature.getInstance("SHA256withECDSA").run { initSign(key); update(msg); sign() } }
            Hex.of(h) + " " + Hex.of(CoinbaseSmartAccount.wrapSignature(a[1].toInt(), CoinbaseSmartAccount.encodeWebAuthn(sig)))
        }
        "vectors" -> {
            val v = java.io.File(a[0]).readText()
            fun vec(k: String) = Regex("\"$k\": \"(0x[0-9a-fA-F]*)\"").find(v)!!.groupValues[1].lowercase()
            val x = ByteArray(32) { 0x11 }; val y = ByteArray(32) { 0x22 }
            val g = listOf("0x1000000000000000000000000000000000000001", "0x2000000000000000000000000000000000000002", "0x3000000000000000000000000000000000000003", "0x4000000000000000000000000000000000000004")
            val wallet = "0xA0Cf798816D4b9b9866b5330EEa46a18382f251e"
            val checks = mapOf(
                "moduleAddress" to RecoveryModule.address.lowercase(),
                "createAccount" to Hex.of(CoinbaseSmartAccount.createAccountCall(listOf(CoinbaseSmartAccount.passkeyOwner(x, y), CoinbaseSmartAccount.addressOwner(RecoveryModule.address)), BigInteger.ZERO)),
                "execute" to Hex.of(Abi.encodeCall("execute(address,uint256,bytes)", Abi.Arg.Address(g[0]), Abi.Arg.Uint(7), Abi.Arg.Bytes(byteArrayOf(0x12, 0x34)))),
                "setupGuardians" to Hex.of(RecoveryModule.setupGuardians(g, 3, 30)),
                "initiateRecovery" to Hex.of(RecoveryModule.initiateRecovery(wallet, x, y, BigInteger.ZERO, listOf(ByteArray(65) { 0xaa.toByte() }, ByteArray(65) { 0xbb.toByte() }))),
                "signatureWrapper" to Hex.of(CoinbaseSmartAccount.wrapSignature(1, ByteArray(65) { 0xcc.toByte() })),
                "webAuthnAuth" to Hex.of(CoinbaseSmartAccount.encodeWebAuthn(com.example.swisswallet.domain.aa.WebAuthnSignature(ByteArray(37) { 1 }, "{\"type\":\"webauthn.get\"}", 23, 1, BigInteger.valueOf(5), BigInteger.valueOf(6)))),
                "userOpHashV06" to Hex.of(UserOperationHasher.hash(UserOperation(wallet, BigInteger.valueOf(5), ByteArray(0), byteArrayOf(0x12, 0x34), BigInteger.valueOf(100000), BigInteger.valueOf(200000), BigInteger.valueOf(50000), BigInteger.valueOf(1000000000), BigInteger.valueOf(100000000), Hex.bytes("0xdeadbeef")), "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789", 84532)),
                "executeBatch" to Hex.of(dummyAccount().encodeExecuteBatch(listOf(
                    com.example.swisswallet.domain.aa.Call(g[0], BigInteger.valueOf(7), byteArrayOf(0x12, 0x34)),
                    com.example.swisswallet.domain.aa.Call(g[1], BigInteger.ZERO, ByteArray(0)),
                ))),
                "recoveryHash" to Hex.of(RecoveryRequest(wallet, x, y, BigInteger.ZERO, BigInteger.ONE, 84532, RecoveryModule.address, "t").hash()),
            )
            checks.entries.joinToString("\n") { (k, mine) -> (if (mine.lowercase() == vec(k)) "✔ " else "✘ ") + k + (if (mine.lowercase() == vec(k)) "" else "\n   kotlin=$mine\n   viem  =${vec(k)}") }
        }
        else -> error("unknown command")
    }
    println(out)
}

fun dummyAccount() = CoinbaseSmartAccount(
    "0x0000000000000000000000000000000000000001",
    object : com.example.swisswallet.domain.aa.Passkey {
        override val id = "x"; override val x = ByteArray(32); override val y = ByteArray(32)
        override fun sign(challenge: ByteArray) = TODO()
    },
    0, emptyList(), "0x0000000000000000000000000000000000000002", BigInteger.ZERO,
    object : com.example.swisswallet.domain.aa.EthereumRpc {
        override suspend fun call(to: String, data: ByteArray) = TODO()
        override suspend fun getBalance(address: String) = TODO()
        override suspend fun getCode(address: String) = TODO()
    },
    "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789", 1,
)

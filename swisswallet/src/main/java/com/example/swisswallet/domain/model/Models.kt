package com.example.swisswallet.domain.model

import com.example.swisswallet.config.ChainConfig
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** A token the wallet supports. [contractAddress] == null → native ETH. */
data class Token(
    val symbol: String,
    val name: String,
    val decimals: Int,
    val contractAddress: String?,
    val glyph: String,
    val color: Long,
) {
    val isNative: Boolean get() = contractAddress == null

    fun toDecimal(raw: BigInteger): BigDecimal = BigDecimal(raw).movePointLeft(decimals)
}

object Tokens {
    val ETH = Token("ETH", "Ethereum", 18, null, "Ξ", 0xFF627EEA)
    val USDC = Token("USDC", "USD Coin", 6, ChainConfig.USDC, "$", 0xFF2775CA)
    val all = listOf(ETH, USDC)
    fun bySymbol(symbol: String?): Token = all.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } ?: ETH
}

/** Fake market price (not blockchain related → simulated). */
data class Price(val chf: BigDecimal, val change24hPercent: Double)

data class TokenBalance(
    val token: Token,
    val raw: BigInteger,
    val price: Price,
) {
    val amount: BigDecimal get() = token.toDecimal(raw)
    val valueChf: BigDecimal get() = amount.multiply(price.chf).setScale(2, RoundingMode.HALF_UP)
}

data class Portfolio(
    val balances: List<TokenBalance>,
) {
    val totalChf: BigDecimal get() = balances.fold(BigDecimal.ZERO) { acc, b -> acc + b.valueChf }

    /** Value-weighted 24h change. */
    val change24hPercent: Double
        get() {
            val total = totalChf
            if (total.signum() == 0) return 0.0
            return balances.sumOf { it.valueChf.toDouble() * it.price.change24hPercent } / total.toDouble()
        }

    val change24hChf: BigDecimal
        get() {
            val pct = change24hPercent / 100.0
            val prev = totalChf.toDouble() / (1 + pct)
            return BigDecimal.valueOf(totalChf.toDouble() - prev).setScale(2, RoundingMode.HALF_UP)
        }
}

enum class ChartRange(val label: String, val points: Int) {
    H24("24H", 24), D7("7D", 28), D30("30D", 30), Y1("1Y", 52)
}

/**
 * The wallet as seen by the app.
 * - [smartAccountAddress]: the Coinbase Smart Wallet contract (ERC-4337) that actually holds the funds.
 * - [passkeyId] / [passkeyFingerprint]: the P-256 passkey of THIS phone (owner of the wallet).
 * - [ownerIndex]: position of that passkey in the wallet's owner list (0 at creation, changes after a recovery).
 * - [swissquoteGuardian]: Swissquote's guardian address (sent by Swissquote when the wallet was generated).
 */
data class WalletAccount(
    val smartAccountAddress: String,
    val passkeyId: String,
    val passkeyFingerprint: String,
    val ownerIndex: Int,
    val swissquoteGuardian: String?,
)

enum class TxKind { SENT, RECEIVED, DEPOSIT, SWAP }
enum class TxStatus { PENDING, CONFIRMED, FAILED }

data class TxRecord(
    val id: String,
    val kind: TxKind,
    val tokenSymbol: String,
    /** Human readable amount, e.g. "0.01". */
    val amount: String,
    val counterparty: String,
    val timestamp: Long,
    val status: TxStatus,
    val userOpHash: String? = null,
    val txHash: String? = null,
    val error: String? = null,
    /** Seeded example entries (not on-chain). */
    val isDemo: Boolean = false,
)

data class TransferRequest(
    val token: Token,
    val to: String,
    val amountRaw: BigInteger,
)

/** Every step of an ERC-4337 transfer, emitted by [com.example.swisswallet.domain.repository.TransferService]. */
sealed interface SendProgress {
    /** Reading nonce / deployment state / gas price and building callData. */
    data object Preparing : SendProgress
    /** Asking Pimlico's paymaster to sponsor (and estimate) the UserOperation. */
    data object Sponsoring : SendProgress
    /** Signing the userOpHash with the passkey (WebAuthn P-256). */
    data object Signing : SendProgress
    /** eth_sendUserOperation to the bundler. */
    data object Submitting : SendProgress
    /** Waiting for the bundler to include it on-chain. */
    data class Pending(val userOpHash: String) : SendProgress
    data class Confirmed(val userOpHash: String, val txHash: String) : SendProgress
    data class Failed(val message: String, val userOpHash: String? = null) : SendProgress
}

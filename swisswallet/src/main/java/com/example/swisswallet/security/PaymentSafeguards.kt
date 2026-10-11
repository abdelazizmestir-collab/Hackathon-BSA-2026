package com.example.swisswallet.security

import android.content.Context
import java.math.BigDecimal

/*
 * App-side payment safeguards. They bind this app, not the wallet contract: someone who can make
 * the device key sign through other software is not stopped by them. Limits enforced on-chain, or
 * a second signer, are the production version.
 */

/** DEMO list standing in for a sanctions-screening provider (Chainalysis, SECO lists). */
object BlockedAddresses {
    private val BLOCKED = setOf("0x000000000000000000000000000000000000dead")

    fun isBlocked(address: String): Boolean = address.trim().lowercase() in BLOCKED
}

/** Decides when a payment needs the extra confirmation. Pure logic, no Android. */
object SpendingPolicy {

    /** Per-payment threshold and rolling 24 h total, in whole tokens. */
    data class Limits(val highValue: BigDecimal, val dailyTotal: BigDecimal)

    private val LIMITS = mapOf(
        "USDC" to Limits(highValue = BigDecimal("1000"), dailyTotal = BigDecimal("2000")),
        "ETH" to Limits(highValue = BigDecimal("0.25"), dailyTotal = BigDecimal("0.5")),
    )

    fun limitsFor(symbol: String): Limits? = LIMITS[symbol.uppercase()]

    /** Empty = an ordinary payment. Many small payments add up through [sentLast24h]. */
    fun reasons(symbol: String, amount: BigDecimal, sentLast24h: BigDecimal, knownRecipient: Boolean): List<String> {
        val reasons = mutableListOf<String>()
        val limits = limitsFor(symbol)
        if (limits != null) {
            if (amount >= limits.highValue) {
                reasons.add("High-value payment (${limits.highValue.toPlainString()} $symbol or more)")
            }
            if (sentLast24h.add(amount) > limits.dailyTotal) {
                reasons.add("Above your daily total of ${limits.dailyTotal.toPlainString()} $symbol")
            }
        }
        if (!knownRecipient) reasons.add("First payment to this address")
        return reasons
    }
}

interface PaymentSafeguards {
    fun isBlocked(recipient: String): Boolean

    /** Why this payment needs the extra confirmation; empty for an ordinary payment. */
    fun reasons(symbol: String, amount: BigDecimal, recipient: String): List<String>

    fun recordSent(symbol: String, amount: BigDecimal, recipient: String)
}

/** Remembers what this phone sent in the last 24 h, and to whom, in SharedPreferences. */
class LocalPaymentSafeguards(context: Context) : PaymentSafeguards {

    private val prefs = context.applicationContext.getSharedPreferences("payment_safeguards", Context.MODE_PRIVATE)

    override fun isBlocked(recipient: String): Boolean = BlockedAddresses.isBlocked(recipient)

    override fun reasons(symbol: String, amount: BigDecimal, recipient: String): List<String> =
        SpendingPolicy.reasons(symbol, amount, sentLast24h(symbol), isKnownRecipient(recipient))

    override fun recordSent(symbol: String, amount: BigDecimal, recipient: String) {
        val entries = recentEntries() + Entry(System.currentTimeMillis(), symbol.uppercase(), amount)
        val recipients = knownRecipients() + recipient.trim().lowercase()
        prefs.edit()
            .putString(KEY_SENT, entries.joinToString(";") { "${it.at}|${it.symbol}|${it.amount.toPlainString()}" })
            .putStringSet(KEY_RECIPIENTS, recipients)
            .apply()
    }

    private fun sentLast24h(symbol: String): BigDecimal {
        var total = BigDecimal.ZERO
        for (entry in recentEntries()) {
            if (entry.symbol == symbol.uppercase()) total = total.add(entry.amount)
        }
        return total
    }

    private fun isKnownRecipient(recipient: String): Boolean = recipient.trim().lowercase() in knownRecipients()

    private fun knownRecipients(): Set<String> = prefs.getStringSet(KEY_RECIPIENTS, null)?.toSet() ?: emptySet()

    private fun recentEntries(): List<Entry> {
        val cutoff = System.currentTimeMillis() - DAY_MS
        val stored = prefs.getString(KEY_SENT, null) ?: return emptyList()
        val entries = mutableListOf<Entry>()
        for (raw in stored.split(";")) {
            val parts = raw.split("|")
            if (parts.size != 3) continue
            val at = parts[0].toLongOrNull() ?: continue
            val amount = parts[2].toBigDecimalOrNull() ?: continue
            if (at >= cutoff) entries.add(Entry(at, parts[1], amount))
        }
        return entries
    }

    private data class Entry(val at: Long, val symbol: String, val amount: BigDecimal)

    private companion object {
        const val KEY_SENT = "sent"
        const val KEY_RECIPIENTS = "recipients"
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

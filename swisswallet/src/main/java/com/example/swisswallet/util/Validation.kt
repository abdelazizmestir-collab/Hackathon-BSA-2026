package com.example.swisswallet.util

import org.web3j.crypto.Keys
import java.math.BigDecimal
import java.math.BigInteger

object AddressValidator {
    private val HEX_ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")

    /** @return an error message, or null if the address is valid. */
    fun validate(input: String, ownAddress: String?): String? {
        val a = input.trim()
        if (a.isEmpty()) return "Enter the recipient's public address"
        if (!HEX_ADDRESS.matches(a)) return "Not a valid address (0x + 40 hexadecimal characters)"
        val body = a.substring(2)
        val mixedCase = body.any { it.isUpperCase() } && body.any { it.isLowerCase() }
        if (mixedCase && Keys.toChecksumAddress(a) != a) return "Checksum mismatch — double-check the address"
        if (ownAddress != null && a.equals(ownAddress, ignoreCase = true)) return "This is your own wallet address"
        return null
    }
}

object AmountParser {

    sealed interface Result {
        data class Ok(val raw: BigInteger, val value: BigDecimal) : Result
        data class Error(val message: String) : Result
    }

    /** Keeps only digits and one decimal separator while typing. */
    fun sanitize(input: String): String {
        val normalized = input.replace(',', '.').filter { it.isDigit() || it == '.' }
        val firstDot = normalized.indexOf('.')
        return if (firstDot == -1) normalized
        else normalized.substring(0, firstDot + 1) + normalized.substring(firstDot + 1).replace(".", "")
    }

    fun parse(input: String, decimals: Int, balanceRaw: BigInteger?): Result {
        val text = sanitize(input)
        if (text.isEmpty() || text == ".") return Result.Error("Enter an amount")
        val value = try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            return Result.Error("Invalid amount")
        }
        if (value.signum() <= 0) return Result.Error("The amount must be greater than 0")
        if (value.stripTrailingZeros().scale() > decimals) return Result.Error("Max $decimals decimals for this token")
        val raw = value.movePointRight(decimals).toBigInteger()
        if (balanceRaw != null && raw > balanceRaw) return Result.Error("Insufficient balance")
        return Result.Ok(raw, value)
    }
}

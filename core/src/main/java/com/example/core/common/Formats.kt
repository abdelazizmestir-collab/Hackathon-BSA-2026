package com.example.core.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Swiss-style number formatting: 1'182.12 */
object Formats {

    private fun symbols() = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = '\''
        decimalSeparator = '.'
    }

    fun money(value: BigDecimal): String =
        DecimalFormat("#,##0.00", symbols()).format(value.setScale(2, RoundingMode.HALF_UP))

    fun money(value: Double): String = money(BigDecimal.valueOf(value))

    /** Token amount with up to [maxDecimals] decimals, trailing zeros trimmed (min 2). */
    fun token(value: BigDecimal, maxDecimals: Int = 6): String {
        val pattern = "#,##0.00" + "#".repeat((maxDecimals - 2).coerceAtLeast(0))
        return DecimalFormat(pattern, symbols()).format(value.setScale(maxDecimals, RoundingMode.DOWN))
    }

    fun signedMoney(value: BigDecimal): String = (if (value.signum() >= 0) "+" else "−") + money(value.abs())

    /** 0x1234abcd…9f2c */
    fun shortAddress(address: String, head: Int = 6, tail: Int = 4): String =
        if (address.length <= head + tail + 2) address
        else address.take(head + 2) + "…" + address.takeLast(tail)
}

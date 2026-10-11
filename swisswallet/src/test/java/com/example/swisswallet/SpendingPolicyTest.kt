package com.example.swisswallet

import com.example.swisswallet.security.BlockedAddresses
import com.example.swisswallet.security.SpendingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SpendingPolicyTest {

    private fun reasons(amount: String, sentToday: String = "0", known: Boolean = true) =
        SpendingPolicy.reasons("USDC", BigDecimal(amount), BigDecimal(sentToday), known)

    @Test
    fun smallPaymentToKnownRecipientNeedsNoExtraConfirmation() {
        assertTrue(reasons("5").isEmpty())
    }

    @Test
    fun paymentAtTheThresholdIsHighValue() {
        assertEquals(1, reasons("1000").size)
        assertTrue(reasons("999.99").isEmpty())
    }

    @Test
    fun manySmallPaymentsAddUpToTheDailyTotal() {
        assertTrue(reasons("900", sentToday = "1000").isEmpty())
        assertEquals(1, reasons("900", sentToday = "1200").size)
    }

    @Test
    fun firstPaymentToAnAddressAlwaysNeedsIt() {
        assertEquals(1, reasons("1", known = false).size)
    }

    @Test
    fun tokenWithoutLimitsStillChecksTheRecipient() {
        assertTrue(SpendingPolicy.reasons("DAI", BigDecimal("999999"), BigDecimal.ZERO, true).isEmpty())
        assertEquals(1, SpendingPolicy.reasons("DAI", BigDecimal("1"), BigDecimal.ZERO, false).size)
    }

    @Test
    fun blockedListIgnoresCase() {
        assertTrue(BlockedAddresses.isBlocked("0x000000000000000000000000000000000000dEaD"))
        assertFalse(BlockedAddresses.isBlocked("0x5290840009852780000000000000000000000001"))
    }
}

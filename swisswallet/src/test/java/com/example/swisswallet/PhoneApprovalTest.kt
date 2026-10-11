package com.example.swisswallet

import com.example.core.auth.PhoneApproval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The approval page (docs/approve/index.html) computes the same codes; these vectors pin both sides. */
class PhoneApprovalTest {
    private val s = "0123456789abcdef"
    private val to = "0x49d805DeCf87BE92E69461341fDEbD2bA73e3286"

    @Test
    fun loginCodeMatchesThePage() = assertEquals("590312", PhoneApproval.loginCode(s))

    @Test
    fun paymentCodeMatchesThePage() = assertEquals("611828", PhoneApproval.paymentCode(s, "0.01", "ETH", to))

    @Test
    fun paymentCodeIsBoundToAmountAndRecipient() {
        assertNotEquals(PhoneApproval.paymentCode(s, "0.01", "ETH", to), PhoneApproval.paymentCode(s, "0.02", "ETH", to))
        assertNotEquals(
            PhoneApproval.paymentCode(s, "0.01", "ETH", to),
            PhoneApproval.paymentCode(s, "0.01", "ETH", "0x1111111111111111111111111111111111111111"),
        )
    }

    @Test
    fun paymentUrlCarriesTheDetailsInTheFragment() = assertEquals(
        "https://abdelazizmestir-collab.github.io/Hackathon-BSA-2026/approve/#a=pay&s=$s&amt=0.01&sym=ETH&to=$to",
        PhoneApproval.paymentUrl(s, "0.01", "ETH", to),
    )
}

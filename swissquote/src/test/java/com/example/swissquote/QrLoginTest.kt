package com.example.swissquote

import com.example.swissquote.ui.welcome.QrLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrLoginTest {

    /** The approval page computes 590312 for this session; the app must agree or no login can succeed. */
    @Test
    fun codeMatchesTheHostedPage() {
        assertEquals("590312", QrLogin.expectedCode("0123456789abcdef"))
    }

    @Test
    fun everyLoginScreenGetsItsOwnSessionAndCode() {
        val a = QrLogin.newSession()
        val b = QrLogin.newSession()
        assertTrue(Regex("^[0-9a-f]{16}$").matches(a))
        assertNotEquals(a, b)
        assertEquals(6, QrLogin.expectedCode(a).length)
    }

    @Test
    fun sessionTravelsInTheUrlFragment() {
        assertEquals("https://abdelazizmestir-collab.github.io/Hackathon-BSA-2026/approve/#a=login&s=abc", QrLogin.loginUrl("abc"))
    }
}

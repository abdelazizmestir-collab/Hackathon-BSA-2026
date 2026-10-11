package com.example.swisswallet

import com.example.swisswallet.data.aa.Abi
import com.example.swisswallet.data.aa.Hex
import com.example.swisswallet.data.aa.UserOperationHasher
import com.example.swisswallet.domain.aa.UserOperation
import com.example.swisswallet.util.AddressValidator
import com.example.swisswallet.util.AmountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** Run with: ./gradlew :swisswallet:testDebugUnitTest */
class AbiAndValidationTest {

    @Test
    fun `function selectors match the well-known values`() {
        assertEquals("0xa9059cbb", Hex.of(Abi.selector("transfer(address,uint256)")))
        assertEquals("0x70a08231", Hex.of(Abi.selector("balanceOf(address)")))
        assertEquals("0xb61d27f6", Hex.of(Abi.selector("execute(address,uint256,bytes)")))
        assertEquals("0x35567e1a", Hex.of(Abi.selector("getNonce(address,uint192)")))
    }

    @Test
    fun `execute with empty bytes is encoded with offset 0x60 and zero length`() {
        val to = "0x000000000000000000000000000000000000dEaD"
        val data = Abi.encodeCall(
            "execute(address,uint256,bytes)",
            Abi.Arg.Address(to), Abi.Arg.Uint(BigInteger.ONE), Abi.Arg.Bytes(ByteArray(0)),
        )
        // selector + 3 head words + 1 length word
        assertEquals(4 + 32 * 4, data.size)
        assertEquals(BigInteger.valueOf(0x60), Abi.decodeUint(data.copyOfRange(4, data.size), 2))
        assertEquals(BigInteger.ZERO, Abi.decodeUint(data.copyOfRange(4, data.size), 3))
    }

    @Test
    fun `bytes argument is right-padded to 32 bytes`() {
        val encoded = Abi.encodeArgs(Abi.Arg.Bytes(byteArrayOf(1, 2, 3)))
        assertEquals(32 * 3, encoded.size) // offset + length + 1 padded word
    }

    @Test
    fun `userOpHash is 32 bytes and changes with the chain id`() {
        val op = UserOperation(
            sender = "0x1111111111111111111111111111111111111111",
            nonce = BigInteger.ZERO,
            callData = ByteArray(0),
            maxFeePerGas = BigInteger.TEN,
            maxPriorityFeePerGas = BigInteger.ONE,
            signature = ByteArray(65),
        )
        val ep = "0x0000000071727De22E5E9d8BAf0edAc6f37da032"
        val h1 = UserOperationHasher.hash(op, ep, 11155111)
        val h2 = UserOperationHasher.hash(op, ep, 1)
        assertEquals(32, h1.size)
        assertTrue(!h1.contentEquals(h2))
    }

    @Test
    fun `address validation`() {
        assertNull(AddressValidator.validate("0x52908400098527886E0F7030069857D2E4169EE7", null))
        assertNotNull(AddressValidator.validate("0x1234", null))
        assertNotNull(AddressValidator.validate("0x52908400098527886e0F7030069857D2E4169EE7", null)) // bad checksum
        assertNotNull(AddressValidator.validate("0xabc0000000000000000000000000000000000001", "0xABC0000000000000000000000000000000000001"))
    }

    @Test
    fun `amount parsing`() {
        val ok = AmountParser.parse("0.5", 18, null) as AmountParser.Result.Ok
        assertEquals(BigInteger("500000000000000000"), ok.raw)
        assertTrue(AmountParser.parse("0", 18, null) is AmountParser.Result.Error)
        assertTrue(AmountParser.parse("1.1234567", 6, null) is AmountParser.Result.Error)
        assertTrue(AmountParser.parse("2", 18, BigInteger.ONE) is AmountParser.Result.Error)
        assertEquals("1.25", AmountParser.sanitize("1,2.5"))
    }
}

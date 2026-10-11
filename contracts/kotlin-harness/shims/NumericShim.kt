package org.web3j.utils
import java.math.BigInteger
object Numeric {
    fun cleanHexPrefix(s: String) = if (s.startsWith("0x") || s.startsWith("0X")) s.substring(2) else s
    fun hexStringToByteArray(s: String): ByteArray { var h = cleanHexPrefix(s); if (h.length % 2 == 1) h = "0$h"
        return ByteArray(h.length/2) { ((h[it*2].digitToInt(16) shl 4) + h[it*2+1].digitToInt(16)).toByte() } }
    fun toHexStringNoPrefix(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    fun toHexString(b: ByteArray) = "0x" + toHexStringNoPrefix(b)
    fun toHexStringWithPrefix(v: BigInteger) = "0x" + v.toString(16)
    fun toBytesPadded(v: BigInteger, len: Int): ByteArray { val raw = v.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        require(raw.size <= len) { "too big" }; return ByteArray(len - raw.size) + raw }
}

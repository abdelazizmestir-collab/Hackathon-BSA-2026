// Minimal stand-ins for the few web3j / org.json APIs used by the pure-Kotlin code under test.
package org.web3j.crypto
import java.math.BigInteger
object Hash {
    private val RC = longArrayOf(0x0000000000000001L,0x0000000000008082L,-0x7fffffffffff7f76L,-0x7fffffff7fff8000L,0x000000000000808bL,0x0000000080000001L,-0x7fffffff7fff7f7fL,-0x7fffffffffff7ff7L,0x000000000000008aL,0x0000000000000088L,0x0000000080008009L,0x000000008000000aL,0x000000008000808bL,-0x7fffffffffffff75L,-0x7fffffffffff7f77L,-0x7fffffffffff7ffdL,-0x7fffffffffff7ffeL,-0x7fffffffffffff80L,0x000000000000800aL,-0x7fffffff7ffffff6L,-0x7fffffff7fff7f7fL,-0x7fffffffffff7f80L,0x0000000080000001L,-0x7fffffff7fff7ff8L)
    private val R = intArrayOf(0,1,62,28,27,36,44,6,55,20,3,10,43,25,39,41,45,15,21,8,18,2,61,56,14)
    private fun f(a: LongArray) { val c=LongArray(5); val b=LongArray(25)
        for (round in 0 until 24) { for (x in 0 until 5) c[x]=a[x] xor a[x+5] xor a[x+10] xor a[x+15] xor a[x+20]
            for (x in 0 until 5){ val d=c[(x+4)%5] xor java.lang.Long.rotateLeft(c[(x+1)%5],1); for (y in 0 until 5) a[x+5*y]=a[x+5*y] xor d }
            for (x in 0 until 5) for (y in 0 until 5) b[y+5*((2*x+3*y)%5)] = java.lang.Long.rotateLeft(a[x+5*y], R[x+5*y])
            for (x in 0 until 5) for (y in 0 until 5) a[x+5*y]=b[x+5*y] xor (b[(x+1)%5+5*y].inv() and b[(x+2)%5+5*y])
            a[0]=a[0] xor RC[round] } }
    fun sha3(input: ByteArray): ByteArray { val rate=136; val a=LongArray(25)
        val padded = input.copyOf(((input.size/rate)+1)*rate); padded[input.size]=(padded[input.size].toInt() xor 0x01).toByte(); padded[padded.size-1]=(padded[padded.size-1].toInt() xor 0x80).toByte()
        for (off in padded.indices step rate) { for (i in 0 until rate/8) { var v=0L; for (k in 0 until 8) v = v or ((padded[off+i*8+k].toLong() and 0xff) shl (8*k)); a[i]=a[i] xor v }; f(a) }
        val out=ByteArray(32); for (i in 0 until 32) out[i]=(a[i/8] ushr (8*(i%8))).toByte(); return out }
}
object Keys {
    fun getAddress(pub: BigInteger): String = error("shim")
    fun toChecksumAddress(address: String): String { val a = address.removePrefix("0x").lowercase()
        val h = org.web3j.utils.Numeric.toHexStringNoPrefix(Hash.sha3(a.toByteArray()))
        return "0x" + a.mapIndexed { i, ch -> if (ch.isLetter() && h[i].digitToInt(16) >= 8) ch.uppercaseChar() else ch }.joinToString("") }
}
class Sign { class SignatureData(val v: Byte, val r: ByteArray, val s: ByteArray)
    companion object { @JvmStatic fun signedPrefixedMessageToKey(m: ByteArray, d: SignatureData): BigInteger = error("shim") } }

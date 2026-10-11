package com.example.swisswallet.data.aa

import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/** Hex helpers (0x-prefixed). */
object Hex {
    fun bytes(hex: String): ByteArray = Numeric.hexStringToByteArray(hex)
    fun of(bytes: ByteArray): String = Numeric.toHexString(bytes)
    /** JSON-RPC "quantity": 0x0, 0x1a… */
    fun quantity(value: BigInteger): String = Numeric.toHexStringWithPrefix(value)
    fun toBigInt(hex: String): BigInteger {
        val clean = Numeric.cleanHexPrefix(hex)
        return if (clean.isEmpty()) BigInteger.ZERO else BigInteger(clean, 16)
    }
}

/**
 * Small Solidity ABI encoder/decoder — just what an ERC-4337 wallet needs:
 * address, uint256, bytes32, dynamic bytes/string, address[], bytes[], tuples and tuple[].
 * No reflection, no code generation (and checked against viem in the unit tests).
 */
object Abi {

    sealed interface Arg {
        val isDynamic: Boolean

        class Address(val value: String) : Arg { override val isDynamic = false }
        class Uint(val value: BigInteger) : Arg {
            constructor(value: Long) : this(BigInteger.valueOf(value))
            override val isDynamic = false
        }
        class Bytes32(val value: ByteArray) : Arg {
            init { require(value.size == 32) { "bytes32 must be 32 bytes" } }
            override val isDynamic = false
        }
        /** `bytes` or `string` (same encoding). */
        class Bytes(val value: ByteArray) : Arg { override val isDynamic = true }
        class AddressArray(val values: List<String>) : Arg { override val isDynamic = true }
        class BytesArray(val values: List<ByteArray>) : Arg { override val isDynamic = true }
        class Tuple(vararg val items: Arg) : Arg { override val isDynamic = items.any { it.isDynamic } }
        /** `T[]` where T is a tuple, e.g. Coinbase's `executeBatch((address,uint256,bytes)[])`. */
        class TupleArray(val values: List<Tuple>) : Arg { override val isDynamic = true }
    }

    /** First 4 bytes of keccak256("transfer(address,uint256)") etc. */
    fun selector(signature: String): ByteArray =
        Hash.sha3(signature.toByteArray(Charsets.US_ASCII)).copyOfRange(0, 4)

    fun word(value: BigInteger): ByteArray = Numeric.toBytesPadded(value, 32)
    fun word(value: Long): ByteArray = word(BigInteger.valueOf(value))

    fun addressWord(address: String): ByteArray {
        val raw = Hex.bytes(address)
        require(raw.size == 20) { "Invalid address: $address" }
        return ByteArray(12) + raw
    }

    /** abi.encode(args...) */
    fun encodeArgs(vararg args: Arg): ByteArray = encodeSequence(args.toList())

    fun encodeCall(signature: String, vararg args: Arg): ByteArray = selector(signature) + encodeArgs(*args)

    private fun staticSize(arg: Arg): Int = when (arg) {
        is Arg.Tuple -> arg.items.sumOf { staticSize(it) }
        else -> 32
    }

    private fun encodeSequence(items: List<Arg>): ByteArray {
        val headSize = items.sumOf { if (it.isDynamic) 32 else staticSize(it) }
        val head = ByteArrayOutputStream()
        val tail = ByteArrayOutputStream()
        for (item in items) {
            if (item.isDynamic) {
                head.write(word((headSize + tail.size()).toLong()))
                tail.write(encodeDynamic(item))
            } else {
                head.write(encodeStatic(item))
            }
        }
        return head.toByteArray() + tail.toByteArray()
    }

    private fun encodeStatic(arg: Arg): ByteArray = when (arg) {
        is Arg.Address -> addressWord(arg.value)
        is Arg.Uint -> word(arg.value)
        is Arg.Bytes32 -> arg.value
        is Arg.Tuple -> encodeSequence(arg.items.toList())
        else -> error("$arg is dynamic")
    }

    private fun encodeDynamic(arg: Arg): ByteArray = when (arg) {
        is Arg.Bytes -> {
            val pad = (32 - arg.value.size % 32) % 32
            word(arg.value.size.toLong()) + arg.value + ByteArray(pad)
        }
        is Arg.AddressArray -> word(arg.values.size.toLong()) + arg.values.fold(ByteArray(0)) { acc, a -> acc + addressWord(a) }
        is Arg.BytesArray -> word(arg.values.size.toLong()) + encodeSequence(arg.values.map { Arg.Bytes(it) })
        is Arg.Tuple -> encodeSequence(arg.items.toList())
        is Arg.TupleArray -> word(arg.values.size.toLong()) + encodeSequence(arg.values)
        else -> error("$arg is static")
    }

    // ------------------------------------------------------------------ decoding (return data)

    fun decodeUint(data: ByteArray, wordIndex: Int = 0): BigInteger {
        val start = wordIndex * 32
        if (data.size < start + 32) return BigInteger.ZERO
        return BigInteger(1, data.copyOfRange(start, start + 32))
    }

    fun decodeBool(data: ByteArray, wordIndex: Int = 0): Boolean = decodeUint(data, wordIndex).signum() != 0

    fun decodeBytes32(data: ByteArray, wordIndex: Int = 0): ByteArray {
        val start = wordIndex * 32
        require(data.size >= start + 32) { "No bytes32 in return data" }
        return data.copyOfRange(start, start + 32)
    }

    fun decodeAddress(data: ByteArray, wordIndex: Int = 0): String {
        val start = wordIndex * 32
        require(data.size >= start + 32) { "No address in return data" }
        return Keys.toChecksumAddress(Hex.of(data.copyOfRange(start + 12, start + 32)))
    }

    /** `returns (bytes memory)` */
    fun decodeBytes(data: ByteArray, wordIndex: Int = 0): ByteArray {
        if (data.size < 32) return ByteArray(0)
        val offset = decodeUint(data, wordIndex).toInt()
        val len = BigInteger(1, data.copyOfRange(offset, offset + 32)).toInt()
        return data.copyOfRange(offset + 32, offset + 32 + len)
    }

    /** `returns (address[] memory)` */
    fun decodeAddressArray(data: ByteArray, wordIndex: Int = 0): List<String> {
        if (data.size < 64) return emptyList()
        val offset = decodeUint(data, wordIndex).toInt()
        val n = BigInteger(1, data.copyOfRange(offset, offset + 32)).toInt()
        return (0 until n).map { i ->
            val start = offset + 32 + i * 32
            Keys.toChecksumAddress(Hex.of(data.copyOfRange(start + 12, start + 32)))
        }
    }
}

/** ERC-20 calls used by the wallet. */
object Erc20 {
    fun transfer(to: String, amount: BigInteger): ByteArray =
        Abi.encodeCall("transfer(address,uint256)", Abi.Arg.Address(to), Abi.Arg.Uint(amount))

    fun balanceOf(owner: String): ByteArray =
        Abi.encodeCall("balanceOf(address)", Abi.Arg.Address(owner))
}

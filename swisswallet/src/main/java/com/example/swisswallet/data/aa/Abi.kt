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
 * Tiny Solidity ABI encoder — just what an ERC-4337 wallet needs
 * (address, uint256, dynamic bytes). No reflection, no codegen.
 */
object Abi {

    sealed interface Arg {
        class Address(val value: String) : Arg
        class Uint(val value: BigInteger) : Arg
        class Bytes(val value: ByteArray) : Arg
    }

    /** First 4 bytes of keccak256("transfer(address,uint256)") etc. */
    fun selector(signature: String): ByteArray =
        Hash.sha3(signature.toByteArray(Charsets.US_ASCII)).copyOfRange(0, 4)

    fun word(value: BigInteger): ByteArray = Numeric.toBytesPadded(value, 32)

    fun addressWord(address: String): ByteArray {
        val raw = Hex.bytes(address)
        require(raw.size == 20) { "Invalid address: $address" }
        return ByteArray(12) + raw
    }

    fun encodeArgs(vararg args: Arg): ByteArray {
        val head = ByteArrayOutputStream()
        val tail = ByteArrayOutputStream()
        val headSize = 32 * args.size
        for (arg in args) {
            when (arg) {
                is Arg.Address -> head.write(addressWord(arg.value))
                is Arg.Uint -> head.write(word(arg.value))
                is Arg.Bytes -> {
                    // dynamic type: head holds the offset, tail holds length + padded data
                    head.write(word(BigInteger.valueOf((headSize + tail.size()).toLong())))
                    tail.write(word(BigInteger.valueOf(arg.value.size.toLong())))
                    tail.write(arg.value)
                    val pad = (32 - arg.value.size % 32) % 32
                    tail.write(ByteArray(pad))
                }
            }
        }
        return head.toByteArray() + tail.toByteArray()
    }

    fun encodeCall(signature: String, vararg args: Arg): ByteArray = selector(signature) + encodeArgs(*args)

    fun decodeUint(data: ByteArray, wordIndex: Int = 0): BigInteger {
        val start = wordIndex * 32
        if (data.size < start + 32) return BigInteger.ZERO
        return BigInteger(1, data.copyOfRange(start, start + 32))
    }

    fun decodeAddress(data: ByteArray, wordIndex: Int = 0): String {
        val start = wordIndex * 32
        require(data.size >= start + 32) { "No address in return data" }
        return Keys.toChecksumAddress(Hex.of(data.copyOfRange(start + 12, start + 32)))
    }
}

/** ERC-20 calls used by the wallet. */
object Erc20 {
    fun transfer(to: String, amount: BigInteger): ByteArray =
        Abi.encodeCall("transfer(address,uint256)", Abi.Arg.Address(to), Abi.Arg.Uint(amount))

    fun balanceOf(owner: String): ByteArray =
        Abi.encodeCall("balanceOf(address)", Abi.Arg.Address(owner))
}

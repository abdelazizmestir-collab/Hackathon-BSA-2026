package com.example.swisswallet.config

import com.example.swisswallet.BuildConfig

/**
 * Network constants. Everything targets Ethereum **Sepolia** (testnet): free test ETH, same
 * contracts as mainnet, and Pimlico sponsors gas there for free.
 */
object ChainConfig {
    const val CHAIN_ID: Long = 11_155_111L
    const val NETWORK_NAME = "Sepolia"
    const val PIMLICO_CHAIN = "sepolia"

    /** Canonical ERC-4337 EntryPoint v0.7 (same address on every EVM chain). */
    const val ENTRY_POINT_V07 = "0x0000000071727De22E5E9d8BAf0edAc6f37da032"

    /** eth-infinitism SimpleAccountFactory for EntryPoint v0.7 (the one permissionless.js uses). */
    const val SIMPLE_ACCOUNT_FACTORY_V07 = "0x91E60e0613810449d098b0b5Ec8b51A0FE8c8985"

    /** Circle's USDC on Sepolia (6 decimals). */
    const val USDC_SEPOLIA = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"

    const val EXPLORER = "https://sepolia.etherscan.io"
    const val FAUCET = "https://cloud.google.com/application/web3/faucet/ethereum/sepolia"
    const val USDC_FAUCET = "https://faucet.circle.com"

    val pimlicoApiKey: String get() = BuildConfig.PIMLICO_API_KEY
    val hasPimlicoKey: Boolean get() = pimlicoApiKey.isNotBlank()

    /** Pimlico = bundler + paymaster on one JSON-RPC endpoint. */
    val pimlicoUrl: String get() = "https://api.pimlico.io/v2/$PIMLICO_CHAIN/rpc?apikey=$pimlicoApiKey"

    /** Standard Ethereum node (balances, eth_call, getCode). */
    val rpcUrl: String get() = BuildConfig.SEPOLIA_RPC_URL

    fun txUrl(hash: String) = "$EXPLORER/tx/$hash"
    fun addressUrl(address: String) = "$EXPLORER/address/$address"
}

package com.example.swisswallet.config

import com.example.swisswallet.BuildConfig

/** A test network SwissWallet can run on. Pick it with `SWISSWALLET_CHAIN=` in local.properties. */
enum class Network(
    val key: String,
    val displayName: String,
    val chainId: Long,
    val pimlicoName: String,
    val defaultRpc: String,
    val explorer: String,
    val usdc: String,
    val faucetUrl: String,
    val faucetLabel: String,
) {
    /** Coinbase's own L2 testnet: Coinbase Smart Wallet + the P-256 (passkey) precompile are native here. */
    BASE_SEPOLIA(
        key = "base-sepolia",
        displayName = "Base Sepolia",
        chainId = 84_532L,
        pimlicoName = "base-sepolia",
        defaultRpc = "https://sepolia.base.org",
        explorer = "https://sepolia.basescan.org",
        usdc = "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
        faucetUrl = "https://portal.cdp.coinbase.com/products/faucet",
        faucetLabel = "Coinbase faucet (Base Sepolia ETH)",
    ),
    SEPOLIA(
        key = "sepolia",
        displayName = "Sepolia",
        chainId = 11_155_111L,
        pimlicoName = "sepolia",
        defaultRpc = "https://ethereum-sepolia-rpc.publicnode.com",
        explorer = "https://sepolia.etherscan.io",
        usdc = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238",
        faucetUrl = "https://cloud.google.com/application/web3/faucet/ethereum/sepolia",
        faucetLabel = "Google faucet (Sepolia ETH)",
    );

    companion object {
        fun fromKey(key: String?): Network = entries.firstOrNull { it.key.equals(key?.trim(), true) } ?: BASE_SEPOLIA
    }
}

/**
 * Everything chain-related in one place.
 *
 * Wallet = **Coinbase Smart Wallet** (ERC-4337, EntryPoint v0.6) whose owners are:
 *   #0 a passkey (P-256 key in the phone's secure hardware)
 *   #1 the SwissRecoveryModule (can only add a new passkey after 3-of-4 guardians + delay)
 */
object ChainConfig {
    val network: Network = Network.fromKey(BuildConfig.SWISSWALLET_CHAIN)

    val CHAIN_ID: Long get() = network.chainId
    val NETWORK_NAME: String get() = network.displayName

    /** Canonical ERC-4337 EntryPoint v0.6 — the one Coinbase Smart Wallet uses. */
    const val ENTRY_POINT_V06 = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789"

    /** Coinbase Smart Wallet factories (same address on every chain). v1 is viem's default. */
    const val CSW_FACTORY_V1 = "0x0BA5ED0c6AA8c49038F819E587E2633c4A9F428a"
    const val CSW_FACTORY_V1_1 = "0xBA5ED110eFDBa3D005bfC882d75358ACBbB85842"

    /** Arachnid's deterministic CREATE2 proxy (deployed on virtually every EVM chain). */
    const val CREATE2_DEPLOYER = "0x4e59b44847b379578588920cA78FbF26c0B4956C"

    val USDC: String get() = network.usdc
    val EXPLORER: String get() = network.explorer
    val FAUCET: String get() = network.faucetUrl
    val FAUCET_LABEL: String get() = network.faucetLabel
    const val USDC_FAUCET = "https://faucet.circle.com"

    val pimlicoApiKey: String get() = BuildConfig.PIMLICO_API_KEY
    val hasPimlicoKey: Boolean get() = pimlicoApiKey.isNotBlank()
    val pimlicoUrl: String get() = "https://api.pimlico.io/v2/${network.pimlicoName}/rpc?apikey=$pimlicoApiKey"

    val rpcUrl: String get() = BuildConfig.RPC_URL.ifBlank { network.defaultRpc }

    fun txUrl(hash: String) = "$EXPLORER/tx/$hash"
    fun addressUrl(address: String) = "$EXPLORER/address/$address"
}

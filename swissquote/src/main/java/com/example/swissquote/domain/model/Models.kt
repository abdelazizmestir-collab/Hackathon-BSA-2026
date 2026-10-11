package com.example.swissquote.domain.model

/** One line of the watchlist (SMI, NESN, UBSG…). */
data class Quote(
    val ticker: String,
    val name: String,
    val price: Double,
    val changePercent: Double,
    val currency: String = "CHF",
)

data class WatchlistSnapshot(
    val quotes: List<Quote>,
    /** Epoch millis of the last market update. */
    val updatedAt: Long,
)

enum class WatchlistSort(val label: String) {
    Default("Sort"),
    ChangeDesc("Top movers"),
    NameAsc("A–Z"),
}

/**
 * Local Swissquote session.
 * - [hasAccount] false = "Discover the app" guest mode.
 * - [walletAddress] set once the SwissWallet has been generated (ERC-4337 smart account address).
 */
data class Session(
    val hasAccount: Boolean,
    val walletAddress: String?,
) {
    val walletGenerated: Boolean get() = walletAddress != null
}

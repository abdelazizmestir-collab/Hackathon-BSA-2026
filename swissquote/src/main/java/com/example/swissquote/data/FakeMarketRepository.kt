package com.example.swissquote.data

import com.example.swissquote.domain.model.Quote
import com.example.swissquote.domain.model.WatchlistSnapshot
import com.example.swissquote.domain.repository.MarketRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Simulated market feed: first load has a realistic latency, then each call nudges prices
 * a little so the watchlist feels "live".
 */
class FakeMarketRepository(
    private val random: Random = Random(42),
) : MarketRepository {

    private val mutex = Mutex()
    private var firstLoad = true

    private var quotes = listOf(
        Quote("SMI", "SWISS MARKET INDEX SMI® PRICE", 13787.40, 1.10),
        Quote("SDZ", "Est. 70.00", 69.72, 0.58),
        Quote("SREN", "Est. 144.45", 144.00, 1.19),
        Quote("ALC", "Est. 53.54", 53.14, 1.80),
        Quote("GALD", "GALDERMA GROUP N", 164.95, 1.32),
        Quote("ZURN", "ZURICH INSURANCE N", 576.00, 0.56),
        Quote("NESN", "NESTLE N", 76.04, 0.05),
        Quote("GEBN", "GEBERIT N", 528.60, 0.92),
        Quote("SLHN", "SWISS LIFE HOLDING AG N", 883.60, 0.25),
        Quote("UBSG", "UBS GROUP N", 39.90, 2.76),
    )

    override suspend fun fetchWatchlist(): WatchlistSnapshot = mutex.withLock {
        if (firstLoad) {
            delay(1100)
            firstLoad = false
        } else {
            delay(350)
            quotes = quotes.map { q ->
                val drift = (random.nextDouble() - 0.5) * 0.0016 // ±0.08 %
                val newPrice = q.price * (1 + drift)
                val open = q.price / (1 + q.changePercent / 100)
                q.copy(price = newPrice, changePercent = (newPrice / open - 1) * 100)
            }
        }
        WatchlistSnapshot(quotes, System.currentTimeMillis())
    }
}

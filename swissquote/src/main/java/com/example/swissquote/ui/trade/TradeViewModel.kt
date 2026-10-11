package com.example.swissquote.ui.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.swissquote.di.AppContainer
import com.example.swissquote.domain.model.Quote
import com.example.swissquote.domain.model.WatchlistSort
import com.example.swissquote.domain.repository.MarketRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TradeTab(val label: String) {
    Watchlists("Watchlists"), Markets("Markets"), Inspiration("Inspiration"), News("News")
}

data class TradeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val quotes: List<Quote> = emptyList(),
    val updatedAt: Long? = null,
    val error: String? = null,
    val tab: TradeTab = TradeTab.Watchlists,
    val sort: WatchlistSort = WatchlistSort.Default,
)

class TradeViewModel(
    private val market: MarketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TradeUiState())
    val state: StateFlow<TradeUiState> = _state.asStateFlow()

    private var raw: List<Quote> = emptyList()

    init {
        load(showSkeleton = true)
        // Fake "live" feed: refresh quietly every 5 s while the screen's ViewModel is alive.
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val s = _state.value
                if (!s.isLoading && !s.isRefreshing && s.error == null) fetch(silent = true)
            }
        }
    }

    fun onRefresh() = load(showSkeleton = false)
    fun onRetry() = load(showSkeleton = true)

    fun onTabSelected(tab: TradeTab) = _state.update { it.copy(tab = tab) }

    fun onSortClicked() = _state.update {
        val next = WatchlistSort.entries[(it.sort.ordinal + 1) % WatchlistSort.entries.size]
        it.copy(sort = next, quotes = sorted(raw, next))
    }

    private fun load(showSkeleton: Boolean) {
        _state.update {
            if (showSkeleton) it.copy(isLoading = true, error = null) else it.copy(isRefreshing = true)
        }
        viewModelScope.launch { fetch(silent = false) }
    }

    private suspend fun fetch(silent: Boolean) {
        try {
            val snapshot = market.fetchWatchlist()
            raw = snapshot.quotes
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    quotes = sorted(raw, it.sort),
                    updatedAt = snapshot.updatedAt,
                    error = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (silent) return
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = if (it.quotes.isEmpty()) "Couldn't load market data. Check your connection." else null,
                )
            }
        }
    }

    private fun sorted(list: List<Quote>, sort: WatchlistSort): List<Quote> = when (sort) {
        WatchlistSort.Default -> list
        WatchlistSort.ChangeDesc -> list.sortedByDescending { it.changePercent }
        WatchlistSort.NameAsc -> list.sortedBy { it.ticker }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { TradeViewModel(c.marketRepository) }
        }
    }
}

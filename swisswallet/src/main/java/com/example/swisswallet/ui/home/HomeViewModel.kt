package com.example.swisswallet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.swisswallet.data.aa.ErrorMapper
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.model.ChartRange
import com.example.swisswallet.domain.model.Portfolio
import com.example.swisswallet.domain.repository.PortfolioRepository
import com.example.swisswallet.domain.repository.PriceRepository
import com.example.swisswallet.domain.repository.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val portfolio: Portfolio? = null,
    val range: ChartRange = ChartRange.D7,
    val chart: List<Float> = emptyList(),
    val hideBalance: Boolean = false,
)

class HomeViewModel(
    private val wallet: WalletRepository,
    private val portfolios: PortfolioRepository,
    private val prices: PriceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init { load(Mode.Initial) }

    /** Called each time the screen appears (e.g. back from Send) → silent refresh. */
    fun onAppear() { if (_state.value.portfolio != null) load(Mode.Silent) }
    fun onRefresh() = load(Mode.Pull)
    fun onRetry() = load(Mode.Initial)
    fun onToggleHide() = _state.update { it.copy(hideBalance = !it.hideBalance) }

    fun onRangeSelected(range: ChartRange) = _state.update {
        it.copy(range = range, chart = it.portfolio?.let { p -> prices.history(range, p.totalChf) } ?: emptyList())
    }

    private enum class Mode { Initial, Pull, Silent }

    private fun load(mode: Mode) {
        if (loadJob?.isActive == true) return
        _state.update {
            when (mode) {
                Mode.Initial -> it.copy(isLoading = true, error = null)
                Mode.Pull -> it.copy(isRefreshing = true)
                Mode.Silent -> it
            }
        }
        loadJob = viewModelScope.launch {
            try {
                val account = wallet.currentAccount() ?: error("No wallet on this device")
                val portfolio = portfolios.loadPortfolio(account.smartAccountAddress)
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        portfolio = portfolio,
                        chart = prices.history(it.range, portfolio.totalChf),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val hadData = _state.value.portfolio != null
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, error = if (hadData) null else ErrorMapper.humanize(e))
                }
                if (hadData && mode != Mode.Silent) _messages.trySend("Couldn't refresh balances: ${ErrorMapper.humanize(e)}")
            }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { HomeViewModel(c.walletRepository, c.portfolioRepository, c.priceRepository) }
        }
    }
}

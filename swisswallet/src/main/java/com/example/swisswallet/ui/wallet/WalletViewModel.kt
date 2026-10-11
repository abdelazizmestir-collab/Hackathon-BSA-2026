package com.example.swisswallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.swisswallet.data.aa.ErrorMapper
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.model.Portfolio
import com.example.swisswallet.domain.model.WalletAccount
import com.example.swisswallet.domain.repository.PortfolioRepository
import com.example.swisswallet.domain.repository.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WalletTab(val label: String) { Tokens("Tokens"), Nft("NFT"), Staking("Staking") }

data class WalletUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val account: WalletAccount? = null,
    val portfolio: Portfolio? = null,
    val error: String? = null,
    val tab: WalletTab = WalletTab.Tokens,
)

class WalletViewModel(
    private val wallet: WalletRepository,
    private val portfolios: PortfolioRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()
    private var job: Job? = null

    init { load(showSkeleton = true) }

    fun onAppear() { if (_state.value.portfolio != null) load(showSkeleton = false, silent = true) }
    fun onRefresh() = load(showSkeleton = false)
    fun onRetry() = load(showSkeleton = true)
    fun onTabSelected(tab: WalletTab) = _state.update { it.copy(tab = tab) }

    private fun load(showSkeleton: Boolean, silent: Boolean = false) {
        if (job?.isActive == true) return
        _state.update {
            when {
                silent -> it
                showSkeleton -> it.copy(isLoading = true, error = null)
                else -> it.copy(isRefreshing = true)
            }
        }
        job = viewModelScope.launch {
            try {
                val account = wallet.currentAccount() ?: error("No wallet on this device")
                _state.update { it.copy(account = account) }
                val portfolio = portfolios.loadPortfolio(account.smartAccountAddress)
                _state.update { it.copy(isLoading = false, isRefreshing = false, portfolio = portfolio, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (it.portfolio == null) ErrorMapper.humanize(e) else null,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { WalletViewModel(c.walletRepository, c.portfolioRepository) }
        }
    }
}

package com.example.swisswallet.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.aa.EthereumRpc
import com.example.swisswallet.domain.model.WalletAccount
import com.example.swisswallet.domain.repository.SettingsRepository
import com.example.swisswallet.domain.repository.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Deployment status of the smart account (it's deployed by its first UserOperation). */
enum class DeployState { Checking, Deployed, NotDeployed, Unknown }

data class ProfileUiState(
    val account: WalletAccount? = null,
    val deployState: DeployState = DeployState.Checking,
    val biometrics: Boolean = true,
    val notifications: Boolean = true,
    val paymasterConfigured: Boolean = ChainConfig.hasPimlicoKey,
)

class ProfileViewModel(
    private val wallet: WalletRepository,
    private val settings: SettingsRepository,
    private val rpc: EthereumRpc,
) : ViewModel() {

    private val deploy = MutableStateFlow(DeployState.Checking)

    val state: StateFlow<ProfileUiState> =
        combine(wallet.account, settings.biometrics, settings.notifications, deploy) { acc, bio, notif, dep ->
            ProfileUiState(account = acc, deployState = dep, biometrics = bio, notifications = notif)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    init { checkDeployment() }

    fun checkDeployment() {
        viewModelScope.launch {
            deploy.value = DeployState.Checking
            deploy.value = try {
                val acc = wallet.currentAccount()
                when {
                    acc == null -> DeployState.Unknown
                    rpc.getCode(acc.smartAccountAddress).isNotEmpty() -> DeployState.Deployed
                    else -> DeployState.NotDeployed
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeployState.Unknown
            }
        }
    }

    fun onBiometrics(enabled: Boolean) { viewModelScope.launch { settings.setBiometrics(enabled) } }
    fun onNotifications(enabled: Boolean) { viewModelScope.launch { settings.setNotifications(enabled) } }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { ProfileViewModel(c.walletRepository, c.settingsRepository, c.ethereumRpc) }
        }
    }
}

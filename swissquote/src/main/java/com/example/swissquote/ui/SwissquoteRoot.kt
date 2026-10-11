package com.example.swissquote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.components.BottomBarItem
import com.example.core.designsystem.components.SwissBottomBar
import com.example.swissquote.di.AppContainer
import com.example.swissquote.domain.model.Session
import com.example.swissquote.domain.repository.SessionRepository
import com.example.swissquote.domain.repository.WalletAppLauncher
import com.example.swissquote.ui.trade.TradeScreen
import com.example.swissquote.ui.trade.TradeViewModel
import com.example.swissquote.ui.wallet.WalletTabScreen
import com.example.swissquote.ui.wallet.WalletTabViewModel
import com.example.swissquote.ui.welcome.WelcomeScreen
import com.example.swissquote.ui.welcome.WelcomeViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

object SqRoutes {
    const val TRADE = "trade"
    const val WELCOME = "welcome"
    const val WALLET = "wallet"
}

private object TabKeys {
    const val HOME = "home"
    const val TRADE = "trade"
    const val BANKING = "banking"
    const val PLAN = "plan"
    const val WALLET = "wallet"
}

/** App-wide state: session + "open the external SwissWallet" action. */
class AppViewModel(
    sessionRepository: SessionRepository,
    private val launcher: WalletAppLauncher,
) : ViewModel() {

    val session: StateFlow<Session?> = sessionRepository.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun openSwissWallet() {
        val opened = runCatching { launcher.openWallet() }.getOrDefault(false)
        if (!opened) {
            _messages.trySend("SwissWallet isn't installed. Run the “swisswallet” configuration in Android Studio first.")
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { AppViewModel(c.sessionRepository, c.walletAppLauncher) }
        }
    }
}

@Composable
fun SwissquoteRoot(container: AppContainer) {
    val navController = rememberNavController()
    val appVm: AppViewModel = viewModel(factory = AppViewModel.factory(container))
    val session by appVm.session.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(appVm) {
        appVm.messages.collect { snackbarHost.showSnackbar(it) }
    }

    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBottomBar = route == SqRoutes.TRADE || route == SqRoutes.WALLET
    val walletReady = session?.walletGenerated == true

    val tabs = listOf(
        BottomBarItem(TabKeys.HOME, "Home", SwissIcons.Home),
        BottomBarItem(TabKeys.TRADE, "Trade", SwissIcons.Trade),
        BottomBarItem(TabKeys.BANKING, "Banking", SwissIcons.Banking),
        BottomBarItem(TabKeys.PLAN, "Plan", SwissIcons.Plan),
        BottomBarItem(TabKeys.WALLET, "Wallet", SwissIcons.Wallet, badge = walletReady),
    )

    Scaffold(
        containerColor = SwissColors.Background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (showBottomBar) {
                SwissBottomBar(
                    items = tabs,
                    selectedKey = if (route == SqRoutes.WALLET) TabKeys.WALLET else TabKeys.TRADE,
                    onSelect = { item ->
                        when (item.key) {
                            // Wallet already generated → redirect to the external SwissWallet app.
                            TabKeys.WALLET -> if (walletReady) {
                                appVm.openSwissWallet()
                            } else if (route != SqRoutes.WALLET) {
                                navController.navigate(SqRoutes.WALLET) { launchSingleTop = true }
                            }
                            // As in the Figma prototype: the other tabs lead to the welcome/login page.
                            else -> navController.navigate(SqRoutes.WELCOME) { launchSingleTop = true }
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = SqRoutes.TRADE,
            modifier = Modifier.padding(padding),
        ) {
            composable(SqRoutes.TRADE) {
                val vm: TradeViewModel = viewModel(factory = TradeViewModel.factory(container))
                TradeScreen(vm)
            }
            composable(SqRoutes.WELCOME) {
                val vm: WelcomeViewModel = viewModel(factory = WelcomeViewModel.factory(container))
                WelcomeScreen(
                    viewModel = vm,
                    onFinished = {
                        navController.navigate(SqRoutes.TRADE) {
                            popUpTo(SqRoutes.TRADE) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(SqRoutes.WALLET) {
                val vm: WalletTabViewModel = viewModel(factory = WalletTabViewModel.factory(container))
                WalletTabScreen(
                    viewModel = vm,
                    onSignUp = { navController.navigate(SqRoutes.WELCOME) { launchSingleTop = true } },
                    onOpenSwissWallet = appVm::openSwissWallet,
                )
            }
        }
    }
}

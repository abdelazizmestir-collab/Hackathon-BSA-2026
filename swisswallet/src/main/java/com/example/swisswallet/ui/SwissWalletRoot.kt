package com.example.swisswallet.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.components.BottomBarItem
import com.example.core.designsystem.components.SwissBottomBar
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.ui.history.HistoryScreen
import com.example.swisswallet.ui.history.HistoryViewModel
import com.example.swisswallet.ui.home.HomeScreen
import com.example.swisswallet.ui.home.HomeViewModel
import com.example.swisswallet.ui.profile.ProfileScreen
import com.example.swisswallet.ui.profile.ProfileViewModel
import com.example.swisswallet.ui.receive.ReceiveScreen
import com.example.swisswallet.ui.recovery.GuardianModeScreen
import com.example.swisswallet.ui.recovery.GuardianModeViewModel
import com.example.swisswallet.ui.recovery.GuardiansScreen
import com.example.swisswallet.ui.recovery.GuardiansViewModel
import com.example.swisswallet.ui.recovery.RecoverScreen
import com.example.swisswallet.ui.recovery.RecoverViewModel
import com.example.swisswallet.ui.receive.ReceiveViewModel
import com.example.swisswallet.ui.send.SendScreen
import com.example.swisswallet.ui.send.SendViewModel
import com.example.swisswallet.ui.splash.NoWalletScreen
import com.example.swisswallet.ui.splash.SplashScreen
import com.example.swisswallet.ui.splash.SplashViewModel
import com.example.swisswallet.ui.wallet.WalletScreen
import com.example.swisswallet.ui.wallet.WalletViewModel
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val NO_WALLET = "no_wallet"
    const val HOME = "home"
    const val WALLET = "wallet"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val SEND = "send?token={token}"
    const val RECEIVE = "receive"
    const val GUARDIANS = "guardians"
    const val GUARDIAN_MODE = "guardian_mode"
    const val RECOVER = "recover"
    fun send(token: String = "ETH") = "send?token=$token"
}

private val TAB_ROUTES = setOf(Routes.HOME, Routes.WALLET, Routes.HISTORY, Routes.PROFILE)

private fun NavController.navigateTab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun SwissWalletRoot(container: AppContainer, onLock: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val tabs = listOf(
        BottomBarItem(Routes.HOME, "Home", SwissIcons.Home),
        BottomBarItem(Routes.WALLET, "Wallet", SwissIcons.Wallet),
        BottomBarItem(Routes.HISTORY, "History", SwissIcons.History),
        BottomBarItem(Routes.PROFILE, "Profile", SwissIcons.Profile),
    )

    Scaffold(
        containerColor = SwissColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route != null && route in TAB_ROUTES) {
                SwissBottomBar(
                    items = tabs,
                    selectedKey = route,
                    onSelect = { nav.navigateTab(it.key) },
                    inactiveColor = SwissColors.TextSecondary,
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.SPLASH, modifier = Modifier.padding(padding)) {

            composable(Routes.SPLASH) {
                val vm: SplashViewModel = viewModel(factory = SplashViewModel.factory(container))
                SplashScreen(vm) { hasWallet ->
                    nav.navigate(if (hasWallet) Routes.HOME else Routes.NO_WALLET) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }
            composable(Routes.NO_WALLET) {
                val vm: SplashViewModel = viewModel(factory = SplashViewModel.factory(container))
                NoWalletScreen(
                    vm,
                    onWalletReady = { nav.navigate(Routes.HOME) { popUpTo(Routes.NO_WALLET) { inclusive = true } } },
                    onRecover = { nav.navigate(Routes.RECOVER) },
                    onGuardianMode = { nav.navigate(Routes.GUARDIAN_MODE) },
                )
            }
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                HomeScreen(
                    viewModel = vm,
                    onSend = { nav.navigate(Routes.send()) },
                    onReceive = { nav.navigate(Routes.RECEIVE) },
                    onSeeAll = { nav.navigateTab(Routes.WALLET) },
                    onSettings = { nav.navigateTab(Routes.PROFILE) },
                    onAsset = { symbol -> nav.navigate(Routes.send(symbol)) },
                    showMessage = showMessage,
                )
            }
            composable(Routes.WALLET) {
                val vm: WalletViewModel = viewModel(factory = WalletViewModel.factory(container))
                WalletScreen(
                    viewModel = vm,
                    onSend = { symbol -> nav.navigate(Routes.send(symbol)) },
                    onReceive = { nav.navigate(Routes.RECEIVE) },
                    showMessage = showMessage,
                )
            }
            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
                HistoryScreen(vm)
            }
            composable(Routes.PROFILE) {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(container))
                ProfileScreen(
                    vm,
                    showMessage = showMessage,
                    onLock = onLock,
                    onGuardians = { nav.navigate(Routes.GUARDIANS) },
                    onGuardianMode = { nav.navigate(Routes.GUARDIAN_MODE) },
                    onWalletWiped = {
                        showMessage("Passkey deleted. Recover your wallet with your guardians.")
                        nav.navigate(Routes.NO_WALLET) { popUpTo(nav.graph.id) { inclusive = true } }
                    },
                )
            }
            composable(
                Routes.SEND,
                arguments = listOf(navArgument("token") { type = NavType.StringType; defaultValue = "ETH" }),
            ) { backStack ->
                val token = backStack.arguments?.getString("token") ?: "ETH"
                val vm: SendViewModel = viewModel(factory = SendViewModel.factory(container, token))
                SendScreen(
                    viewModel = vm,
                    onClose = { nav.popBackStack() },
                    onOpenHistory = {
                        nav.popBackStack()
                        nav.navigateTab(Routes.HISTORY)
                    },
                )
            }
            composable(Routes.GUARDIANS) {
                val vm: GuardiansViewModel = viewModel(factory = GuardiansViewModel.factory(container))
                GuardiansScreen(vm, onBack = { nav.popBackStack() }, onGuardianMode = { nav.navigate(Routes.GUARDIAN_MODE) }, showMessage = showMessage)
            }
            composable(Routes.GUARDIAN_MODE) {
                val vm: GuardianModeViewModel = viewModel(factory = GuardianModeViewModel.factory(container))
                GuardianModeScreen(vm, onBack = { nav.popBackStack() }, showMessage = showMessage)
            }
            composable(Routes.RECOVER) {
                val vm: RecoverViewModel = viewModel(factory = RecoverViewModel.factory(container))
                RecoverScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onRecovered = {
                        showMessage("Wallet recovered ✓ This phone controls it again.")
                        nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }
                    },
                    showMessage = showMessage,
                )
            }
            composable(Routes.RECEIVE) {
                val vm: ReceiveViewModel = viewModel(factory = ReceiveViewModel.factory(container))
                ReceiveScreen(vm, onBack = { nav.popBackStack() }, showMessage = showMessage)
            }
        }
    }
}

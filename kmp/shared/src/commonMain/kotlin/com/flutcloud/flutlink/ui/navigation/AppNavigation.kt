package com.flutcloud.flutlink.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.ui.HomeScreen
import com.flutcloud.flutlink.ui.guest.GuestScreen
import com.flutcloud.flutlink.ui.login.LoginScreen
import kotlinx.coroutines.launch

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val GUEST = "guest"
}

@Composable
fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val session by container.sessionManager.session.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        container.sessionManager.init(container.ocsApi)
    }

    val sessionKey = session?.let { "${it.baseUrl}|${it.username}" }
    LaunchedEffect(sessionKey, navController) {
        val current = navController.currentDestination?.route
        when {
            session != null && current != Routes.HOME ->
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            // Guests stay on the guest screen even without a session.
            session == null && current == Routes.GUEST -> Unit
            session == null && current != Routes.LOGIN ->
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
        }
    }

    // Remembered guest choice: launch straight into guest mode instead of
    // asking for login/register again on every cold start.
    val startDestination = when {
        session != null -> Routes.HOME
        container.settingsStore.isGuestMode() -> Routes.GUEST
        else -> Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                container = container,
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onContinueAsGuest = {
                    scope.launch { container.settingsStore.setGuestMode(true) }
                    navController.navigate(Routes.GUEST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.GUEST) {
            GuestScreen(
                container = container,
                onExit = {
                    // Explicit exit: forget the remembered choice so the next
                    // start shows the login screen again.
                    scope.launch { container.settingsStore.setGuestMode(false) }
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.GUEST) { inclusive = true }
                    }
                },
                onSignIn = {
                    scope.launch { container.settingsStore.setGuestMode(false) }
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.GUEST) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
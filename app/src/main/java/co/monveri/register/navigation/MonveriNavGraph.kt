package co.monveri.register.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import co.monveri.register.feature.auth.AuthRoutes
import co.monveri.register.feature.auth.HomePlaceholderScreen
import co.monveri.register.feature.auth.PairingScreen
import co.monveri.register.feature.auth.PinScreen
import co.monveri.register.feature.auth.SplashScreen

/**
 * Top-level navigation graph. Splash routes based on persisted auth state; subsequent flows
 * push forward and pop back via standard Compose Navigation semantics.
 *
 * Later phases will introduce a `register` graph (catalog, cart, checkout) gated behind the
 * `auth/home` placeholder.
 */
@Composable
fun MonveriNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = AuthRoutes.SPLASH) {

        composable(AuthRoutes.SPLASH) {
            SplashScreen(
                onUnpaired = {
                    navController.navigate(AuthRoutes.PAIRING) {
                        popUpTo(AuthRoutes.SPLASH) { inclusive = true }
                    }
                },
                onPairedNoSession = {
                    navController.navigate(AuthRoutes.PIN) {
                        popUpTo(AuthRoutes.SPLASH) { inclusive = true }
                    }
                },
                onAuthenticated = {
                    navController.navigate(AuthRoutes.HOME_PLACEHOLDER) {
                        popUpTo(AuthRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(AuthRoutes.PAIRING) {
            PairingScreen(
                onPaired = {
                    navController.navigate(AuthRoutes.PIN) {
                        popUpTo(AuthRoutes.PAIRING) { inclusive = true }
                    }
                },
            )
        }

        composable(AuthRoutes.PIN) {
            PinScreen(
                onAuthenticated = {
                    navController.navigate(AuthRoutes.HOME_PLACEHOLDER) {
                        popUpTo(AuthRoutes.PIN) { inclusive = true }
                    }
                },
                onUnpair = {
                    navController.navigate(AuthRoutes.PAIRING) {
                        popUpTo(AuthRoutes.PIN) { inclusive = true }
                    }
                },
            )
        }

        composable(AuthRoutes.HOME_PLACEHOLDER) {
            HomePlaceholderScreen(
                onLoggedOut = {
                    navController.navigate(AuthRoutes.PIN) {
                        popUpTo(AuthRoutes.HOME_PLACEHOLDER) { inclusive = true }
                    }
                },
            )
        }
    }
}

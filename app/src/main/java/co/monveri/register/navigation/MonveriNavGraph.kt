package co.monveri.register.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import co.monveri.register.debug.addDebugRoutes
import co.monveri.register.debug.createStripeTestHarnessNavigator
import co.monveri.register.feature.auth.AuthRoutes
import co.monveri.register.feature.auth.PairingScreen
import co.monveri.register.feature.auth.PinScreen
import co.monveri.register.feature.auth.SplashScreen
import co.monveri.register.feature.cart.CartRoutes
import co.monveri.register.feature.cart.CartScreen
import co.monveri.register.feature.cart.CustomerLookupScreen
import co.monveri.register.feature.catalog.BarcodeScannerScreen
import co.monveri.register.feature.catalog.CatalogListScreen
import co.monveri.register.feature.catalog.CatalogRoutes
import co.monveri.register.feature.catalog.ProductDetailScreen
import co.monveri.register.feature.settings.SettingsRoutes
import co.monveri.register.feature.settings.reader.ReaderDiscoveryScreen

/**
 * Top-level navigation graph. Splash routes based on persisted auth state; subsequent flows push
 * forward and pop back via standard Compose Navigation semantics.
 *
 * Phase 3: the post-auth Home destination is now the catalog list. From there:
 *  - Tap a card → product detail
 *  - Top-bar scan icon → barcode scanner route → handoff back via SavedStateHandle
 *  - "View cart" FAB → cart screen → customer lookup or checkout
 *
 * Scanner result is plumbed back through the previous entry's SavedStateHandle so the catalog
 * VM (which owns the cart-add logic) sees a single barcode string and reacts.
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
                    navController.navigate(CatalogRoutes.LIST) {
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
                    navController.navigate(CatalogRoutes.LIST) {
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

        composable(CatalogRoutes.LIST) { backStackEntry ->
            // Scanner returns its result by setting "scanResult" on this entry's SavedStateHandle.
            // We hand it to the screen as a one-shot parameter; the screen calls back when consumed
            // so a config change doesn't replay the scan.
            val savedStateHandle = backStackEntry.savedStateHandle
            val scannedCode = savedStateHandle.get<String>(SCAN_RESULT_KEY)
            CatalogListScreen(
                onProductSelected = { id ->
                    navController.navigate(CatalogRoutes.detailFor(id))
                },
                onScanRequested = {
                    navController.navigate(SCANNER_ROUTE)
                },
                onCartRequested = {
                    navController.navigate(CartRoutes.CART)
                },
                onSettingsRequested = {
                    navController.navigate(SettingsRoutes.READER)
                },
                pendingScannedBarcode = scannedCode,
                onScannedBarcodeConsumed = {
                    savedStateHandle.remove<String>(SCAN_RESULT_KEY)
                },
            )
        }

        composable(
            route = CatalogRoutes.DETAIL,
            arguments = listOf(navArgument(CatalogRoutes.ARG_PRODUCT_ID) { type = NavType.StringType }),
        ) {
            ProductDetailScreen(
                onBack = { navController.popBackStack() },
                onAddedToCart = { navController.popBackStack() },
            )
        }

        composable(SCANNER_ROUTE) {
            BarcodeScannerScreen(
                onScanResult = { code ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(SCAN_RESULT_KEY, code)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(CartRoutes.CART) {
            CartScreen(
                onBack = { navController.popBackStack() },
                onLookUpCustomer = { navController.navigate(CartRoutes.CUSTOMER_LOOKUP) },
                onCheckout = {
                    // Phase 6 wires this to the payment routes. For Phase 3 the back stack returns
                    // to the catalog — the cart persists in memory.
                    navController.popBackStack(CatalogRoutes.LIST, inclusive = false)
                },
            )
        }

        composable(CartRoutes.CUSTOMER_LOOKUP) {
            CustomerLookupScreen(onDismiss = { navController.popBackStack() })
        }

        composable(SettingsRoutes.READER) {
            ReaderDiscoveryScreen(
                onBack = { navController.popBackStack() },
                onDebugTestHarness = createStripeTestHarnessNavigator(navController),
            )
        }

        addDebugRoutes(navController)
    }
}

/** Scanner uses its own route name (single full-screen sheet) — kept inline so :app stays consistent. */
private const val SCANNER_ROUTE = "scanner"
private const val SCAN_RESULT_KEY = "scanResult"

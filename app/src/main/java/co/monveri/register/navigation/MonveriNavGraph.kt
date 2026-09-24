package co.monveri.register.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import co.monveri.register.debug.addDebugRoutes
import co.monveri.register.debug.createStripeTestHarnessNavigator
import co.monveri.register.feature.auth.AddExpenseScreen
import co.monveri.register.feature.auth.AuthRoutes
import co.monveri.register.feature.auth.BackOfficeHomeScreen
import co.monveri.register.feature.auth.BackOfficeProductEditorScreen
import co.monveri.register.feature.auth.BackOfficeProductListScreen
import co.monveri.register.feature.auth.BackOfficeRoutes
import co.monveri.register.feature.auth.CloseRegisterScreen
import co.monveri.register.feature.auth.ExpenseDetailScreen
import co.monveri.register.feature.auth.ExpenseListScreen
import co.monveri.register.feature.auth.PairingScannerScreen
import co.monveri.register.feature.auth.PairingScreen
import co.monveri.register.feature.auth.PinScreen
import co.monveri.register.feature.auth.RegisterHomeScreen
import co.monveri.register.feature.auth.RegisterOpenScreen
import co.monveri.register.feature.auth.SplashScreen
import co.monveri.register.feature.cart.CartRoutes
import co.monveri.register.feature.cart.CartScreen
import co.monveri.register.feature.cart.CheckoutScreen
import co.monveri.register.feature.cart.CustomerLookupScreen
import co.monveri.register.feature.catalog.BarcodeScannerScreen
import co.monveri.register.feature.catalog.CatalogListScreen
import co.monveri.register.feature.catalog.CatalogRoutes
import co.monveri.register.feature.catalog.CategoryProductsScreen
import co.monveri.register.feature.catalog.ProductDetailScreen
import co.monveri.register.feature.settings.SettingsRoutes
import co.monveri.register.feature.settings.reader.ReaderDiscoveryScreen
import co.monveri.register.feature.settings.taptopay.TapToPayScreen

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
                onNeedsRegisterOpen = {
                    navController.navigate(AuthRoutes.HOME) {
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

        composable(AuthRoutes.PAIRING) { backStackEntry ->
            // Scanner returns its raw (already-validated) QR text via this entry's
            // SavedStateHandle — same handoff pattern the catalog barcode scanner uses.
            val savedStateHandle = backStackEntry.savedStateHandle
            val scannedQr = savedStateHandle.get<String>(PAIRING_SCAN_RESULT_KEY)
            PairingScreen(
                onPaired = {
                    navController.navigate(AuthRoutes.PIN) {
                        popUpTo(AuthRoutes.PAIRING) { inclusive = true }
                    }
                },
                onScanRequested = {
                    navController.navigate(AuthRoutes.PAIRING_SCAN)
                },
                pendingScannedQr = scannedQr,
                onScannedQrConsumed = {
                    savedStateHandle.remove<String>(PAIRING_SCAN_RESULT_KEY)
                },
            )
        }

        composable(AuthRoutes.PAIRING_SCAN) {
            PairingScannerScreen(
                onScanned = { raw ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(PAIRING_SCAN_RESULT_KEY, raw)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(AuthRoutes.PIN) {
            PinScreen(
                onAuthenticated = {
                    navController.navigate(CatalogRoutes.LIST) {
                        popUpTo(AuthRoutes.PIN) { inclusive = true }
                    }
                },
                onNeedsRegisterOpen = {
                    navController.navigate(AuthRoutes.HOME) {
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

        composable(AuthRoutes.HOME) {
            RegisterHomeScreen(
                onOpenRegisterRequested = {
                    navController.navigate(AuthRoutes.REGISTER_OPEN)
                },
                onBackOfficeRequested = {
                    navController.navigate(BackOfficeRoutes.HOME)
                },
                onLoggedOut = {
                    navController.navigate(AuthRoutes.PIN) {
                        popUpTo(AuthRoutes.HOME) { inclusive = true }
                    }
                },
            )
        }

        composable(BackOfficeRoutes.HOME) {
            BackOfficeHomeScreen(
                onProductsRequested = { navController.navigate(BackOfficeRoutes.PRODUCTS) },
                onExpensesRequested = { navController.navigate(BackOfficeRoutes.EXPENSES) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(BackOfficeRoutes.PRODUCTS) {
            BackOfficeProductListScreen(
                onProductSelected = { id ->
                    navController.navigate(BackOfficeRoutes.productEditFor(id))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = BackOfficeRoutes.PRODUCT_EDIT,
            arguments = listOf(navArgument(BackOfficeRoutes.ARG_PRODUCT_ID) { type = NavType.StringType }),
        ) {
            BackOfficeProductEditorScreen(onBack = { navController.popBackStack() })
        }

        composable(BackOfficeRoutes.EXPENSES) {
            ExpenseListScreen(
                onExpenseSelected = { id ->
                    navController.navigate(BackOfficeRoutes.expenseDetailFor(id))
                },
                onAddExpenseRequested = { navController.navigate(BackOfficeRoutes.EXPENSE_ADD) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(BackOfficeRoutes.EXPENSE_ADD) {
            AddExpenseScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = BackOfficeRoutes.EXPENSE_DETAIL,
            arguments = listOf(navArgument(BackOfficeRoutes.ARG_EXPENSE_ID) { type = NavType.StringType }),
        ) {
            ExpenseDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(AuthRoutes.REGISTER_OPEN) {
            RegisterOpenScreen(
                onOpened = {
                    // Pop past HOME too — reachable from either Splash or PIN, so it's the true
                    // root of this pre-catalog stack, not REGISTER_OPEN itself.
                    navController.navigate(CatalogRoutes.LIST) {
                        popUpTo(AuthRoutes.HOME) { inclusive = true }
                    }
                },
                onUnpair = {
                    navController.navigate(AuthRoutes.PAIRING) {
                        popUpTo(AuthRoutes.HOME) { inclusive = true }
                    }
                },
            )
        }

        composable(AuthRoutes.REGISTER_CLOSE) {
            CloseRegisterScreen(
                onBack = { navController.popBackStack() },
                onOpenNewRegister = {
                    navController.navigate(AuthRoutes.REGISTER_OPEN) {
                        popUpTo(CatalogRoutes.LIST) { inclusive = true }
                    }
                },
                onLoggedOut = {
                    navController.navigate(AuthRoutes.PIN) {
                        popUpTo(CatalogRoutes.LIST) { inclusive = true }
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
                onCustomerRequested = {
                    navController.navigate(CartRoutes.CUSTOMER_LOOKUP)
                },
                onCategorySelected = { categoryId ->
                    navController.navigate(CatalogRoutes.categoryProductsFor(categoryId))
                },
                onReaderRequested = {
                    navController.navigate(SettingsRoutes.READER)
                },
                onCloseRegisterRequested = {
                    navController.navigate(AuthRoutes.REGISTER_CLOSE)
                },
                onLoggedOut = {
                    navController.navigate(AuthRoutes.PIN) {
                        popUpTo(CatalogRoutes.LIST) { inclusive = true }
                    }
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

        composable(
            route = CatalogRoutes.CATEGORY_PRODUCTS,
            arguments = listOf(navArgument(CatalogRoutes.ARG_CATEGORY_ID) { type = NavType.StringType }),
        ) {
            CategoryProductsScreen(
                onProductSelected = { id -> navController.navigate(CatalogRoutes.detailFor(id)) },
                onBack = { navController.popBackStack() },
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
                onCheckout = { navController.navigate(CartRoutes.CHECKOUT) },
            )
        }

        composable(CartRoutes.CUSTOMER_LOOKUP) {
            CustomerLookupScreen(onDismiss = { navController.popBackStack() })
        }

        composable(CartRoutes.CHECKOUT) {
            CheckoutScreen(
                // Abandoning checkout returns to Cart — the items are still there to review.
                onBackToCart = { navController.popBackStack() },
                // A completed sale clears the cart, so skip past it straight to Catalog.
                onSaleComplete = { navController.popBackStack(CatalogRoutes.LIST, inclusive = false) },
            )
        }

        composable(SettingsRoutes.READER) {
            ReaderDiscoveryScreen(
                onBack = { navController.popBackStack() },
                onTapToPay = { navController.navigate(SettingsRoutes.TAP_TO_PAY) },
                onDebugTestHarness = createStripeTestHarnessNavigator(navController),
            )
        }

        composable(SettingsRoutes.TAP_TO_PAY) {
            TapToPayScreen(onBack = { navController.popBackStack() })
        }

        addDebugRoutes(navController)
    }
}

/** Scanner uses its own route name (single full-screen sheet) — kept inline so :app stays consistent. */
private const val SCANNER_ROUTE = "scanner"
private const val SCAN_RESULT_KEY = "scanResult"
private const val PAIRING_SCAN_RESULT_KEY = "pairingScanResult"

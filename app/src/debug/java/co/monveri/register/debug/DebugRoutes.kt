package co.monveri.register.debug

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Routes wired only into debug builds. Defined here in `src/debug/` with a parallel no-op
 * implementation in `src/release/` so the NavGraph in `src/main/` can call both unconditionally
 * and the release APK never ships the gallery code.
 */
object DebugRoutes {
    const val COMPONENT_GALLERY = "debug/component-gallery"
    const val STRIPE_TEST_HARNESS = "debug/stripe-test-harness"
}

/**
 * Adds the debug-only composable destinations to the nav graph. Real impl — debug variant.
 */
fun NavGraphBuilder.addDebugRoutes(navController: NavController) {
    composable(DebugRoutes.COMPONENT_GALLERY) {
        ComponentGalleryScreen(onBack = navController::popBackStack)
    }
    composable(DebugRoutes.STRIPE_TEST_HARNESS) {
        StripeTestHarnessScreen(onBack = navController::popBackStack)
    }
}

/**
 * Returns a navigator that opens the Component Gallery, or null if not available. Real impl.
 */
fun createGalleryNavigator(navController: NavController): (() -> Unit)? = {
    navController.navigate(DebugRoutes.COMPONENT_GALLERY)
}

/**
 * Navigator for the Stripe Test Harness — null in release. Surfaces the $0.50 test-charge entry
 * point on the reader settings screen only in debug builds.
 */
fun createStripeTestHarnessNavigator(navController: NavController): (() -> Unit)? = {
    navController.navigate(DebugRoutes.STRIPE_TEST_HARNESS)
}

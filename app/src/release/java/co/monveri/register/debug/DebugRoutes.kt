package co.monveri.register.debug

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/**
 * Release-variant no-op stubs. Mirror the signatures from the debug variant so the main nav
 * graph can call them without conditionals — the gallery code never reaches a release APK.
 */
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun NavGraphBuilder.addDebugRoutes(navController: NavController) {
    // No debug routes in release.
}

@Suppress("UNUSED_PARAMETER")
fun createGalleryNavigator(navController: NavController): (() -> Unit)? = null

@Suppress("UNUSED_PARAMETER")
fun createStripeTestHarnessNavigator(navController: NavController): (() -> Unit)? = null

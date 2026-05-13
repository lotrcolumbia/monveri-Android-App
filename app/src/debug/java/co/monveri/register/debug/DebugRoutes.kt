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
}

/**
 * Adds the debug-only composable destinations to the nav graph. Real impl — debug variant.
 */
fun NavGraphBuilder.addDebugRoutes(navController: NavController) {
    composable(DebugRoutes.COMPONENT_GALLERY) {
        ComponentGalleryScreen(onBack = navController::popBackStack)
    }
}

/**
 * Returns a navigator that opens the Component Gallery, or null if not available. Real impl.
 */
fun createGalleryNavigator(navController: NavController): (() -> Unit)? = {
    navController.navigate(DebugRoutes.COMPONENT_GALLERY)
}

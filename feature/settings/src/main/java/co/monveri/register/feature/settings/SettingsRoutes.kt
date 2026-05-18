package co.monveri.register.feature.settings

/**
 * Compose Navigation routes owned by the settings feature. Phase 4 introduces the reader
 * settings entry; later phases add tax / receipt / discount admin screens.
 */
object SettingsRoutes {
    const val READER = "settings/reader"

    /** Phase 5 — Tap to Pay on Android (phone-as-reader). Reached from the Card reader screen. */
    const val TAP_TO_PAY = "settings/tap-to-pay"
}

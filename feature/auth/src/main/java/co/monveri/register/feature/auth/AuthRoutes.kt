package co.monveri.register.feature.auth

/**
 * Compose Navigation routes owned by the auth feature. Centralised so :app's NavGraph and the
 * feature's own internal navigation references stay in sync.
 */
object AuthRoutes {
    const val SPLASH = "auth/splash"
    const val PAIRING = "auth/pairing"
    const val PAIRING_SCAN = "auth/pairing/scan"
    const val PIN = "auth/pin"
    const val HOME = "auth/home"
    const val REGISTER_OPEN = "auth/register/open"
    const val REGISTER_CLOSE = "auth/register/close"
}

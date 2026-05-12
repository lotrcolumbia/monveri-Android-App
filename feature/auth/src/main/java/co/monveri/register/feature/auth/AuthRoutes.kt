package co.monveri.register.feature.auth

/**
 * Compose Navigation routes owned by the auth feature. Centralised so :app's NavGraph and the
 * feature's own internal navigation references stay in sync.
 */
object AuthRoutes {
    const val SPLASH = "auth/splash"
    const val PAIRING = "auth/pairing"
    const val PIN = "auth/pin"
    const val HOME_PLACEHOLDER = "auth/home"
}

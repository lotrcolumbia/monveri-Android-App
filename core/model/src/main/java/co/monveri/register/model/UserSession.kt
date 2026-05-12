package co.monveri.register.model

/**
 * In-memory view of the currently signed-in employee plus the store binding. Not persisted directly;
 * derived from values in `SecurePrefs` on demand. ViewModels collect this through `AuthRepository`.
 */
data class UserSession(
    val storeName: String,
    val storeCode: String?,
    val baseUrl: String,
    val employee: Employee,
)

/**
 * Discrete auth states the splash screen routes from. Drives navigation in `NavGraph`.
 */
enum class AuthState {
    /** No store API key persisted — route to Pairing. */
    Unpaired,

    /** Store key present, no employee signed in — route to PIN. */
    PairedNoSession,

    /** Store key + employee present — route to Home placeholder. */
    Authenticated,
}

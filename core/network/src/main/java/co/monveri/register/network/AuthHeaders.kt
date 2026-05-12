package co.monveri.register.network

/**
 * Single source of truth for the custom auth headers consumed by `/api/register/_auth.php`.
 * Keep these constants in sync with the backend; mismatched casing is silently accepted by
 * PHP but breaks any test or proxy that parses raw header lines.
 */
object AuthHeaders {
    const val STORE_KEY = "X-Store-Key"
    const val EMPLOYEE_ID = "X-Employee-Id"
}

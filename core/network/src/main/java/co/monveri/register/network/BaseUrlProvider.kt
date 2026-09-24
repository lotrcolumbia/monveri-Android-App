package co.monveri.register.network

import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Implemented by `:core:data` so Retrofit can resolve the per-store base URL at request time
 * (the store URL is configured during pairing, not at app launch).
 *
 * Returns a URL ending in `/api/register/`. If no store has been paired yet, returns a sentinel
 * URL that callers (pairing flow) override on a per-request basis.
 */
interface BaseUrlProvider {
    fun baseUrl(): String

    companion object {
        const val UNPAIRED_PLACEHOLDER = "https://unpaired.invalid/api/register/"

        /** Parsed host portion of [UNPAIRED_PLACEHOLDER] — single source of truth. */
        val UNPAIRED_HOST: String = UNPAIRED_PLACEHOLDER.toHttpUrl().host

        /**
         * Parsed path portion of [UNPAIRED_PLACEHOLDER] (`/api/register/`) — the fixed prefix
         * every Retrofit endpoint path (e.g. `auth/employee-login.php`) resolves against. Lets
         * [HostSwitchInterceptor] recover just the endpoint-relative path so it can graft it onto
         * the real paired store's full path (which carries a `/stores/<token>` segment the
         * placeholder doesn't have) instead of discarding it.
         */
        val UNPAIRED_PATH: String = UNPAIRED_PLACEHOLDER.toHttpUrl().encodedPath
    }
}

package co.monveri.register.network

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
    }
}

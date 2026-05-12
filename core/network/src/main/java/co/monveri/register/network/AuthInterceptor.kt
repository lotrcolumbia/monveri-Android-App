package co.monveri.register.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the `X-Store-Key` and (when an employee is signed in) `X-Employee-Id` headers to
 * outbound requests bound for the **paired store host**. Both headers are validated by
 * `api/register/_auth.php` on the backend.
 *
 * Host-scoped on purpose: an absolute URL request to a third-party host (CDN image, etc.) must
 * not exfiltrate the store API key. If the headers are already present on the request (e.g.
 * pairing call sets `X-Store-Key` via `@Header` before the device is paired) we leave them alone.
 */
class AuthInterceptor @Inject constructor(
    private val provider: AuthHeaderProvider,
    private val baseUrlProvider: BaseUrlProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val pairedBaseUrl = baseUrlProvider.baseUrl()
        if (pairedBaseUrl == BaseUrlProvider.UNPAIRED_PLACEHOLDER) {
            return chain.proceed(original)
        }
        val pairedHost = pairedBaseUrl.toHttpUrlOrNull()?.host ?: return chain.proceed(original)
        if (original.url.host != pairedHost) {
            return chain.proceed(original)
        }

        val builder = original.newBuilder()
        if (original.header(AuthHeaders.STORE_KEY) == null) {
            provider.storeKey()?.let { builder.header(AuthHeaders.STORE_KEY, it) }
        }
        if (original.header(AuthHeaders.EMPLOYEE_ID) == null) {
            provider.employeeId()?.let { builder.header(AuthHeaders.EMPLOYEE_ID, it.toString()) }
        }

        return chain.proceed(builder.build())
    }
}

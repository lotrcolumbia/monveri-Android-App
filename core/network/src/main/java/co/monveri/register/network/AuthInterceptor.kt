package co.monveri.register.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the `X-Store-Key` (always when present) and `X-Employee-Id` (when an employee is
 * signed in) headers to every outbound request. Both headers are validated by
 * `api/register/_auth.php` on the backend.
 *
 * If the headers are already present on the request (e.g. set explicitly by a caller during
 * pairing where the key isn't persisted yet) we leave them alone.
 */
class AuthInterceptor @Inject constructor(
    private val provider: AuthHeaderProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
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

package co.monveri.register.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Rewrites every request built against the [BaseUrlProvider.UNPAIRED_PLACEHOLDER] base to
 * target the currently-paired store instead.
 *
 * Retrofit needs a non-null base URL at construction time, but ours is per-store and only
 * known after the user pairs. We register the client with a sentinel base URL and rewrite at
 * send time so we can hot-swap stores without rebuilding Retrofit.
 *
 * The paired store's base URL carries a path (`/stores/<token>/api/register/`) that the
 * placeholder doesn't (`/api/register/`) — swapping only scheme/host/port and keeping the
 * placeholder's path would silently drop the `/stores/<token>` segment and hit the wrong
 * store's data (or none). Instead we strip the known placeholder path prefix off the request's
 * resolved path to recover just the endpoint-relative part (e.g. `auth/employee-login.php`)
 * and graft that onto the real store's full path.
 *
 * Pairing requests pass a fully-qualified URL on the `MonveriApi.validateKey` call (Retrofit
 * `@Url` parameter), which short-circuits this interceptor — the absolute URL wins.
 */
class HostSwitchInterceptor @Inject constructor(
    private val provider: BaseUrlProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val originalUrl = original.url

        // Only rewrite when our placeholder host is in play — caller-supplied absolute URLs
        // (pairing flow) keep their own host.
        if (originalUrl.host != BaseUrlProvider.UNPAIRED_HOST) {
            return chain.proceed(original)
        }

        val target = provider.baseUrl().toHttpUrlOrNull() ?: return chain.proceed(original)

        val placeholderPath = BaseUrlProvider.UNPAIRED_PATH
        if (!originalUrl.encodedPath.startsWith(placeholderPath)) {
            // Shouldn't happen — every MonveriApi endpoint is declared relative to the same
            // placeholder base. Fail loudly against the unreachable placeholder host rather than
            // silently guessing a path, which would risk hitting the wrong store.
            return chain.proceed(original)
        }
        val endpointPath = originalUrl.encodedPath.removePrefix(placeholderPath)
        val newEncodedPath = target.encodedPath + endpointPath

        val newUrl = originalUrl.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .encodedPath(newEncodedPath)
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}

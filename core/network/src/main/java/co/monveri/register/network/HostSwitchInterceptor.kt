package co.monveri.register.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Rewrites the host of every request to the currently-paired store base URL.
 *
 * Retrofit needs a non-null base URL at construction time, but ours is per-store and only
 * known after the user pairs. We register the client with a sentinel base URL
 * (`BaseUrlProvider.UNPAIRED_PLACEHOLDER`) and rewrite at send time so we can hot-swap stores
 * without rebuilding Retrofit.
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
        val target = provider.baseUrl().toHttpUrlOrNull() ?: return chain.proceed(original)

        // Only rewrite when our placeholder host is in play — caller-supplied absolute URLs
        // (pairing flow) keep their own host.
        if (originalUrl.host != BaseUrlProvider.UNPAIRED_HOST) {
            return chain.proceed(original)
        }

        val newUrl = originalUrl.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}

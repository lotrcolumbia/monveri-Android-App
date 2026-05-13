package co.monveri.register.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import kotlin.random.Random

/**
 * Retries 5xx responses on **idempotent** HTTP methods only (GET / HEAD / OPTIONS) with
 * exponential backoff + jitter. Non-idempotent methods (POST / PATCH / DELETE) are passed
 * through untouched so we never silently duplicate a write — e.g. a 500 on `auth/employee-login`
 * could create a second session if retried.
 *
 *  - Attempt 1: immediate
 *  - Attempt 2: ~250ms + jitter
 *  - Attempt 3: ~1s + jitter
 *  - Attempt 4: ~4s + jitter
 *
 * Network IOExceptions are *not* retried — they propagate so [NetworkErrorMapper] can surface
 * them as [NetworkError.Offline] or [NetworkError.Timeout]. Repositories decide retry policy.
 */
class RetryInterceptor @Inject constructor() : Interceptor {

    @Suppress("ReturnCount") // multiple early returns keep retry control flow readable
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method !in RETRYABLE_METHODS) {
            return chain.proceed(request)
        }

        var lastResponse: Response? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            lastResponse?.close()
            val response = chain.proceed(request)
            if (response.code !in SERVER_ERROR_RANGE || attempt == MAX_ATTEMPTS) {
                return response
            }
            lastResponse = response
            sleepBackoff(attempt)
        }

        // Unreachable: the loop above always either returns or assigns lastResponse and
        // sleeps. Defensive throw rather than `error()` so OkHttp sees a clean IOException.
        throw IOException("RetryInterceptor exited loop without a response")
    }

    private fun sleepBackoff(attempt: Int) {
        val base = BACKOFF_BASE_MS shl (attempt - 1)
        val jitter = Random.nextLong(JITTER_MS)
        try {
            Thread.sleep(base + jitter)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Retry interrupted", e)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
        const val BACKOFF_BASE_MS = 250L
        const val JITTER_MS = 150L
        val SERVER_ERROR_RANGE = 500..599
        val RETRYABLE_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }
}

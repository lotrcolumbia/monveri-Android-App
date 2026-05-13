package co.monveri.register.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import kotlin.random.Random

/**
 * Retries 5xx responses with exponential backoff + jitter. The first 401/4xx/network failure is
 * propagated unchanged — retries only kick in for transient server-side failures.
 *
 *  - Attempt 1: immediate
 *  - Attempt 2: ~250ms + jitter
 *  - Attempt 3: ~1s + jitter
 *  - Attempt 4: ~4s + jitter
 *
 * After [MAX_ATTEMPTS] the last response is returned to the caller.
 */
class RetryInterceptor @Inject constructor() : Interceptor {

    @Suppress("ReturnCount") // multiple early returns keep retry control flow readable
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastResponse: Response? = null
        var lastException: IOException? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                lastResponse?.close()
                val response = chain.proceed(request)
                if (response.code !in SERVER_ERROR_RANGE || attempt == MAX_ATTEMPTS) {
                    return response
                }
                lastResponse = response
            } catch (e: IOException) {
                if (attempt == MAX_ATTEMPTS) throw e
                lastException = e
            }
            sleepBackoff(attempt)
        }

        return lastResponse ?: throw (lastException ?: IOException("Retry loop exited without response"))
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
    }
}

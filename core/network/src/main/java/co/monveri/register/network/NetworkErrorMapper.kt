package co.monveri.register.network

import co.monveri.register.model.ApiErrorBody
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Translates raw network exceptions into the [NetworkError] domain. Driven from the centralised
 * [runCatchingNetwork] helper so every repository surfaces the same taxonomy of failure.
 */
class NetworkErrorMapper(private val json: Json = Json { ignoreUnknownKeys = true }) {

    fun map(exception: Exception): NetworkError = when (exception) {
        is HttpException -> mapHttp(exception)
        is SocketTimeoutException -> NetworkError.Timeout()
        is UnknownHostException, is ConnectException -> NetworkError.Offline()
        is SerializationException -> NetworkError.Parse(exception.message ?: "Could not parse response")
        is IOException -> NetworkError.Offline(exception.message ?: "Connection failure")
        else -> NetworkError.Unknown(exception.message ?: "Unexpected error")
    }

    private fun mapHttp(e: HttpException): NetworkError {
        val errorMessage = parseErrorMessage(e)
        return when (val code = e.code()) {
            UNAUTHORIZED -> NetworkError.Unauthorized(errorMessage)
            FORBIDDEN -> NetworkError.Forbidden(errorMessage)
            NOT_FOUND -> NetworkError.NotFound(errorMessage)
            in CLIENT_RANGE -> NetworkError.ClientError(code, errorMessage)
            in SERVER_RANGE -> NetworkError.Server(code, errorMessage)
            else -> NetworkError.Unknown(errorMessage)
        }
    }

    private fun parseErrorMessage(e: HttpException): String {
        val raw = e.response()?.errorBody()?.string().orEmpty()
        if (raw.isBlank()) return e.message()
        return runCatching {
            json.decodeFromString(ApiErrorBody.serializer(), raw).message
        }.getOrNull() ?: e.message()
    }

    private companion object {
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        val CLIENT_RANGE = 400..499
        val SERVER_RANGE = 500..599
    }
}

/**
 * Run [block] and return its result as a [NetworkResult]. Coroutine cancellation propagates
 * (we never swallow [CancellationException]). All other `Exception`s are routed through
 * [NetworkErrorMapper] into a typed failure. Fatal JVM `Error`s (OOM, StackOverflow, etc.) are
 * not caught — they propagate so the process can die cleanly.
 */
suspend inline fun <T> runCatchingNetwork(
    mapper: NetworkErrorMapper,
    block: suspend () -> T,
): NetworkResult<T> = try {
    NetworkResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (e: Exception) {
    NetworkResult.Failure(mapper.map(e))
}

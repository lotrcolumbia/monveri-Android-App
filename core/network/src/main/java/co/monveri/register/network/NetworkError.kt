package co.monveri.register.network

/**
 * Domain-level network failure cause. Translated from raw HTTP / IO exceptions by
 * [runCatchingNetwork]; consumers never see retrofit/okhttp types.
 *
 * The `message` is user-presentable in English; localization happens at the UI layer.
 */
sealed class NetworkError(open val message: String) {
    /** No connectivity at all — `UnknownHostException`, `ConnectException`, etc. */
    data class Offline(override val message: String = "No internet connection") : NetworkError(message)

    /** Connection or read timeout. */
    data class Timeout(override val message: String = "Request timed out") : NetworkError(message)

    /** 401 — caller should clear the session token and route to login. */
    data class Unauthorized(override val message: String = "Session expired") : NetworkError(message)

    /** 403 — caller is authenticated but lacks permission. */
    data class Forbidden(override val message: String = "Not permitted") : NetworkError(message)

    /** 404 — endpoint or resource not found. */
    data class NotFound(override val message: String = "Not found") : NetworkError(message)

    /** Any 4xx not otherwise mapped. Carries the raw HTTP status for logging. */
    data class ClientError(val code: Int, override val message: String) : NetworkError(message)

    /** Any 5xx, after retries are exhausted. */
    data class Server(val code: Int, override val message: String) : NetworkError(message)

    /** Successful HTTP response that didn't deserialize cleanly. */
    data class Parse(override val message: String) : NetworkError(message)

    /** Any other failure — unexpected exceptions, library bugs. Surfaced as-is for logging. */
    data class Unknown(override val message: String = "Unexpected error") : NetworkError(message)
}

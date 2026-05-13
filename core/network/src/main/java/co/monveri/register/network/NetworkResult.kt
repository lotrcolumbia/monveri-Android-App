package co.monveri.register.network

/**
 * Repository return type for everything network-shaped. ViewModels branch on this instead of
 * handling thrown exceptions — keeps coroutine cancellation semantics intact and forces
 * UI-layer error handling at compile time.
 *
 * Wrap a suspend block with [runCatchingNetwork] to get a `NetworkResult` back automatically.
 */
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Failure(val error: NetworkError) : NetworkResult<Nothing>
}

/** Convenience accessor — returns the data on success, null on failure. */
fun <T> NetworkResult<T>.dataOrNull(): T? = (this as? NetworkResult.Success)?.data

/** Convenience accessor — returns the error on failure, null on success. */
fun NetworkResult<*>.errorOrNull(): NetworkError? = (this as? NetworkResult.Failure)?.error

/** Functor map — apply [transform] to the success payload, propagate failures unchanged. */
inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(data))
    is NetworkResult.Failure -> this
}

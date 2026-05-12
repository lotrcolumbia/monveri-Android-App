package co.monveri.register.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shape used by `_auth.php`'s `jsonError()` helper on the backend. Surfaced to the UI as a toast.
 */
@Serializable
data class ApiErrorBody(
    @SerialName("success") val success: Boolean = false,
    @SerialName("message") val message: String? = null,
)

/**
 * Typed throwable wrapping a non-2xx response so ViewModels can branch on cause without parsing strings.
 */
sealed class AuthFailure(message: String) : Exception(message) {
    class InvalidKey(message: String) : AuthFailure(message)
    class InvalidPin(message: String) : AuthFailure(message)
    class Network(message: String) : AuthFailure(message)
    class Server(message: String) : AuthFailure(message)
}

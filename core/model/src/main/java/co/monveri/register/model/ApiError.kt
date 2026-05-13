package co.monveri.register.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shape used by `_auth.php`'s `jsonError()` helper on the backend. Surfaced to the UI as a toast
 * via the `:core:network` `NetworkErrorMapper`.
 */
@Serializable
data class ApiErrorBody(
    @SerialName("success") val success: Boolean = false,
    @SerialName("message") val message: String? = null,
)

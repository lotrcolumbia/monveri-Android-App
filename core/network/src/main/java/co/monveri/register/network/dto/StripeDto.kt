package co.monveri.register.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /payments/connection-token.php`. `locationId` is optional — when null
 * the backend falls back to the `stripe_terminal_location_id` setting on the store.
 */
@Serializable
data class ConnectionTokenRequest(
    @SerialName("location_id") val locationId: String? = null,
)

/**
 * Response payload (inside [ApiEnvelope.data]) for the connection-token endpoint. The `secret`
 * is the short-lived token Stripe Terminal hands back to its native SDK during reader auth.
 */
@Serializable
data class ConnectionTokenDto(
    @SerialName("secret") val secret: String,
    @SerialName("location_id") val locationId: String? = null,
)

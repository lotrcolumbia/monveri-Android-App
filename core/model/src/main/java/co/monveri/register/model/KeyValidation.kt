package co.monveri.register.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of `GET /api/register/auth/validate-key.php`. Flat shape with no `data` envelope.
 * Mirrors the iOS `KeyValidation` struct so the back-office pairing UI is shared between platforms.
 */
@Serializable
data class KeyValidation(
    @SerialName("success") val success: Boolean,
    @SerialName("store_name") val storeName: String? = null,
    @SerialName("store_code") val storeCode: String? = null,
    @SerialName("store_address") val storeAddress: String? = null,
    @SerialName("store_phone") val storePhone: String? = null,
)

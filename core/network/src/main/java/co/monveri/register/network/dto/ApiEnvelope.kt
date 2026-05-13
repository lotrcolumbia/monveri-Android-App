package co.monveri.register.network.dto

import kotlinx.serialization.Serializable

/**
 * Every `/api/register/` JSON response is wrapped in `{ "success": bool, "data": T, "message"?: string }`
 * (see `api/register/_auth.php::jsonSuccess`). Endpoints that don't return a payload omit `data`.
 *
 * Repositories unwrap this and translate `success = false` into `NetworkError.Unauthorized` (or
 * similar) so ViewModels only see the inner payload.
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
)

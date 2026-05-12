package co.monveri.register.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Outer envelope of `POST /api/register/auth/employee-login.php`. The PHP helper wraps the
 * actual payload under `data` via `jsonSuccess()`.
 */
@Serializable
data class EmployeeLoginResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("data") val data: Employee? = null,
    @SerialName("message") val message: String? = null,
)

/**
 * The employee record + permission map returned on a successful PIN login.
 * `level == 1` is admin; permission keys mirror `includes/user_roles.php`.
 */
@Serializable
data class Employee(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("username") val username: String,
    @SerialName("level") val level: Int,
    @SerialName("status") val status: String,
    @SerialName("pin_hash") val pinHash: String? = null,
    @SerialName("permissions") val permissions: Map<String, Boolean> = emptyMap(),
)

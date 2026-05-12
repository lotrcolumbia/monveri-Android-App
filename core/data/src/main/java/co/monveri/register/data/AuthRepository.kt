package co.monveri.register.data

import co.monveri.register.model.ApiErrorBody
import co.monveri.register.model.AuthFailure
import co.monveri.register.model.AuthState
import co.monveri.register.model.Employee
import co.monveri.register.model.KeyValidation
import co.monveri.register.model.UserSession
import co.monveri.register.network.MonveriApi
import co.monveri.register.network.EmployeeLoginRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry-point for everything auth-shaped: pairing, employee PIN login, logout, and
 * the reactive `AuthState` the navigation graph observes.
 *
 * Backed by [SecurePrefs] for durable state and [MonveriApi] for the two backend calls.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: MonveriApi,
    private val prefs: SecurePrefs,
    private val json: Json,
) {

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** Recomputes the routing state from persisted values. Cheap; safe to call after writes. */
    fun refreshState() {
        _state.value = currentState()
    }

    private fun currentState(): AuthState = when {
        prefs.storeApiKey.isNullOrBlank() -> AuthState.Unpaired
        prefs.employeeId == null -> AuthState.PairedNoSession
        else -> AuthState.Authenticated
    }

    /**
     * Pair the device with a store. Validates the user-entered URL + API key against
     * `validate-key.php`; on success persists everything needed for future requests.
     *
     * @throws AuthFailure.InvalidKey on HTTP 401
     * @throws AuthFailure.Network on connection failure
     * @throws AuthFailure.Server on any other non-2xx
     */
    suspend fun pair(rawBaseUrl: String, apiKey: String): KeyValidation {
        val normalized = normalizeBaseUrl(rawBaseUrl)
        val validateUrl = normalized + "auth/validate-key.php"

        val response = runCatchingNetwork {
            api.validateKey(url = validateUrl, storeKey = apiKey)
        }

        if (!response.success) {
            throw AuthFailure.InvalidKey("Invalid or inactive API key")
        }

        val resolvedStoreName = response.storeName ?: "Monveri Store"
        prefs.storeApiKey = apiKey
        prefs.storeBaseUrl = normalized
        prefs.storeName = resolvedStoreName
        prefs.storeCode = response.storeCode
        refreshState()
        return response.copy(storeName = resolvedStoreName)
    }

    /**
     * Submit a PIN. On success persists the employee record and returns it; on failure throws.
     */
    suspend fun login(pin: String): Employee {
        val response = runCatchingNetwork {
            api.employeeLogin(EmployeeLoginRequest(pin = pin))
        }
        val employee = response.data
        if (!response.success || employee == null) {
            throw AuthFailure.InvalidPin(response.message ?: "Invalid PIN")
        }

        prefs.employeeId = employee.id
        prefs.employeeName = employee.name
        prefs.employeeUsername = employee.username
        prefs.employeeLevel = employee.level
        prefs.permissionsJson = json.encodeToString(
            kotlinx.serialization.serializer<Map<String, Boolean>>(),
            employee.permissions,
        )
        refreshState()
        return employee
    }

    /** Sign out the employee but keep the store pairing intact. */
    fun logout() {
        prefs.clearEmployeeSession()
        refreshState()
    }

    /** Reset the device entirely — pairing screen on next launch. */
    fun unpair() {
        prefs.clearAll()
        refreshState()
    }

    /** Hydrated view of the current session (null when not signed in). */
    fun currentSession(): UserSession? {
        val name = prefs.storeName ?: return null
        val baseUrl = prefs.storeBaseUrl ?: return null
        val employeeId = prefs.employeeId ?: return null
        val employeeName = prefs.employeeName ?: return null
        val username = prefs.employeeUsername ?: return null
        val level = prefs.employeeLevel ?: return null
        return UserSession(
            storeName = name,
            storeCode = prefs.storeCode,
            baseUrl = baseUrl,
            employee = Employee(
                id = employeeId,
                name = employeeName,
                username = username,
                level = level,
                status = "active",
                pinHash = null,
                permissions = decodePermissions(),
            ),
        )
    }

    private fun decodePermissions(): Map<String, Boolean> {
        val raw = prefs.permissionsJson ?: return emptyMap()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.serializer<Map<String, Boolean>>(),
                raw,
            )
        }.getOrElse { emptyMap() }
    }

    private fun normalizeBaseUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        // Tolerate users who paste either `https://store.example` or `https://store.example/api/register`.
        return if (trimmed.endsWith("/api/register")) {
            "$trimmed/"
        } else {
            "$trimmed/api/register/"
        }
    }

    private suspend fun <T> runCatchingNetwork(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: HttpException) {
            val errorMessage = parseErrorMessage(e)
            when (e.code()) {
                UNAUTHORIZED -> throw AuthFailure.InvalidKey(errorMessage)
                in CLIENT_ERROR_RANGE -> throw AuthFailure.InvalidKey(errorMessage)
                else -> throw AuthFailure.Server(errorMessage)
            }
        } catch (e: IOException) {
            throw AuthFailure.Network(e.message ?: "Network unavailable")
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
        val CLIENT_ERROR_RANGE = 400..499
    }
}

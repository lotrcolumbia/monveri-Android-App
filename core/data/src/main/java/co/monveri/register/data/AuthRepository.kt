package co.monveri.register.data

import co.monveri.register.model.AuthState
import co.monveri.register.model.Employee
import co.monveri.register.model.KeyValidation
import co.monveri.register.model.UserSession
import co.monveri.register.network.EmployeeLoginRequest
import co.monveri.register.network.MonveriApi
import co.monveri.register.network.NetworkError
import co.monveri.register.network.NetworkErrorMapper
import co.monveri.register.network.NetworkResult
import co.monveri.register.network.runCatchingNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry-point for everything auth-shaped: pairing, employee PIN login, logout, and
 * the reactive [AuthState] the navigation graph observes.
 *
 * Phase 2 contract: every network-touching call returns [NetworkResult] — repositories never
 * throw. Callers branch on Success / Failure at the ViewModel.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: MonveriApi,
    private val prefs: SecurePrefs,
    private val json: Json,
    private val errorMapper: NetworkErrorMapper,
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
     * `validate-key.php`. On success persists pairing state and returns the normalized response;
     * on failure returns the typed [NetworkError].
     */
    suspend fun pair(rawBaseUrl: String, apiKey: String): NetworkResult<KeyValidation> {
        val normalized = normalizeBaseUrl(rawBaseUrl)
        val validateUrl = normalized + "auth/validate-key.php"

        return when (val response = runCatchingNetwork(errorMapper) {
            api.validateKey(url = validateUrl, storeKey = apiKey)
        }) {
            is NetworkResult.Failure -> response
            is NetworkResult.Success -> {
                val body = response.data
                if (!body.success) {
                    NetworkResult.Failure(NetworkError.Unauthorized("Invalid or inactive API key"))
                } else {
                    val resolvedStoreName = body.storeName ?: "Monveri Store"
                    prefs.storeApiKey = apiKey
                    prefs.storeBaseUrl = normalized
                    prefs.storeName = resolvedStoreName
                    prefs.storeCode = body.storeCode
                    refreshState()
                    NetworkResult.Success(body.copy(storeName = resolvedStoreName))
                }
            }
        }
    }

    /**
     * Submit a PIN. On success persists the employee record and returns it; on failure returns
     * a typed [NetworkError] (typically [NetworkError.Unauthorized] for an invalid PIN).
     */
    suspend fun login(pin: String): NetworkResult<Employee> {
        return when (val response = runCatchingNetwork(errorMapper) {
            api.employeeLogin(EmployeeLoginRequest(pin = pin))
        }) {
            is NetworkResult.Failure -> response
            is NetworkResult.Success -> {
                val body = response.data
                val employee = body.data
                if (!body.success || employee == null) {
                    NetworkResult.Failure(NetworkError.Unauthorized(body.message ?: "Invalid PIN"))
                } else {
                    prefs.employeeId = employee.id
                    prefs.employeeName = employee.name
                    prefs.employeeUsername = employee.username
                    prefs.employeeLevel = employee.level
                    prefs.permissionsJson = json.encodeToString(
                        kotlinx.serialization.serializer<Map<String, Boolean>>(),
                        employee.permissions,
                    )
                    refreshState()
                    NetworkResult.Success(employee)
                }
            }
        }
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
}

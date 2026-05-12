package co.monveri.register.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.AuthRepository
import co.monveri.register.model.AuthFailure
import co.monveri.register.model.AuthState
import co.monveri.register.model.Employee
import co.monveri.register.model.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the three Phase 1 auth screens (Splash, Pairing, PIN) plus the placeholder Home.
 *
 * Splash observes [authState] to route; Pairing calls [pair]; PIN calls [login]; Home calls
 * [logout]. UI events not modeled here (text input, dialog visibility) live in screen-local
 * `remember` state.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.state

    private val _pairing = MutableStateFlow(PairingUiState())
    val pairing: StateFlow<PairingUiState> = _pairing.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    fun currentSession(): UserSession? = authRepository.currentSession()

    fun onPairingBaseUrlChanged(value: String) {
        _pairing.value = _pairing.value.copy(baseUrl = value, errorMessage = null)
    }

    fun onPairingApiKeyChanged(value: String) {
        _pairing.value = _pairing.value.copy(apiKey = value, errorMessage = null)
    }

    fun pair() {
        val snapshot = _pairing.value
        if (snapshot.baseUrl.isBlank() || snapshot.apiKey.isBlank()) {
            _pairing.value = snapshot.copy(errorMessage = "Enter both the store URL and API key.")
            return
        }
        _pairing.value = snapshot.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val result = authRepository.pair(snapshot.baseUrl, snapshot.apiKey)
                _pairing.value = PairingUiState(pairedStoreName = result.storeName)
            } catch (e: AuthFailure) {
                _pairing.value = snapshot.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun onPinDigit(digit: Char) {
        val current = _login.value
        if (current.isLoading || current.pin.length >= PIN_LENGTH) return
        _login.value = current.copy(pin = current.pin + digit, errorMessage = null)
        if (_login.value.pin.length == PIN_LENGTH) {
            submitPin()
        }
    }

    fun onPinBackspace() {
        val current = _login.value
        if (current.pin.isEmpty() || current.isLoading) return
        _login.value = current.copy(pin = current.pin.dropLast(1), errorMessage = null)
    }

    fun clearPin() {
        _login.value = _login.value.copy(pin = "", errorMessage = null)
    }

    private fun submitPin() {
        val snapshot = _login.value
        _login.value = snapshot.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val employee = authRepository.login(snapshot.pin)
                _login.value = LoginUiState(employee = employee)
            } catch (e: AuthFailure) {
                _login.value = LoginUiState(errorMessage = e.message ?: "Sign-in failed")
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _login.value = LoginUiState()
    }

    fun unpair() {
        authRepository.unpair()
        _pairing.value = PairingUiState()
        _login.value = LoginUiState()
    }

    private companion object {
        const val PIN_LENGTH = 4
    }
}

data class PairingUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pairedStoreName: String? = null,
)

data class LoginUiState(
    val pin: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val employee: Employee? = null,
)

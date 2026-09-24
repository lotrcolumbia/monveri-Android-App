package co.monveri.register.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.AuthRepository
import co.monveri.register.data.repository.RegisterSessionRepository
import co.monveri.register.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the "open a till session" screen — a starting cash count, then one network call. */
@HiltViewModel
class RegisterOpenViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val registerSessionRepository: RegisterSessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterOpenUiState())
    val state: StateFlow<RegisterOpenUiState> = _state.asStateFlow()

    fun onDigit(digit: Char) {
        val d = digit.digitToIntOrNull() ?: return
        val current = _state.value
        _state.value = current.copy(
            openingCents = (current.openingCents * 10 + d).coerceAtMost(MAX_CENTS),
            errorMessage = null,
        )
    }

    fun onBackspace() {
        _state.value = _state.value.copy(openingCents = _state.value.openingCents / 10, errorMessage = null)
    }

    fun onClear() {
        _state.value = _state.value.copy(openingCents = 0L, errorMessage = null)
    }

    fun openRegister() {
        val current = _state.value
        if (current.isLoading) return
        val employee = authRepository.currentSession()?.employee
        if (employee == null) {
            _state.value = current.copy(errorMessage = "No signed-in employee — please log in again")
            return
        }
        _state.value = current.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (
                val result = registerSessionRepository.open(
                    employeeId = employee.id.toLong(),
                    employeeName = employee.name,
                    openingCashCents = current.openingCents,
                )
            ) {
                is NetworkResult.Success -> _state.value = current.copy(isLoading = false, opened = true)
                is NetworkResult.Failure ->
                    _state.value = current.copy(isLoading = false, errorMessage = result.error.message)
            }
        }
    }

    private companion object {
        /** ~$999,999.99 — same cap the Custom item keypad uses. */
        const val MAX_CENTS: Long = 99_999_999L
    }
}

data class RegisterOpenUiState(
    val openingCents: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val opened: Boolean = false,
)

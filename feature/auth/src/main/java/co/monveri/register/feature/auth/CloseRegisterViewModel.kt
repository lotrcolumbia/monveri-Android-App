package co.monveri.register.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.AuthRepository
import co.monveri.register.data.repository.CashCount
import co.monveri.register.data.repository.RegisterSessionRepository
import co.monveri.register.data.repository.SessionReport
import co.monveri.register.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the till-close flow — count-entry face, then a variance-report face once submitted.
 * Mirrors iOS's `CloseRegisterView`: preview expected cash up front (so counting isn't blind),
 * cashier enters the counted amount + optional notes, submit closes the session for real.
 */
@HiltViewModel
class CloseRegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val registerSessionRepository: RegisterSessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CloseRegisterUiState())
    val state: StateFlow<CloseRegisterUiState> = _state.asStateFlow()

    init {
        loadPreview()
    }

    private fun loadPreview() {
        viewModelScope.launch {
            val sessionResult = registerSessionRepository.current()
            val previewResult = registerSessionRepository.previewClose()
            val sessionId = (sessionResult as? NetworkResult.Success)?.data?.id
            val expected = (previewResult as? NetworkResult.Success)?.data?.expectedCashCents
            val error = (sessionResult as? NetworkResult.Failure)?.error?.message
                ?: (previewResult as? NetworkResult.Failure)?.error?.message
            _state.value = _state.value.copy(
                isLoadingPreview = false,
                sessionId = sessionId,
                expectedCashCents = expected,
                errorMessage = if (sessionId == null && error == null) {
                    "No open register session found"
                } else {
                    error
                },
            )
        }
    }

    fun updateCashCount(transform: (CashCount) -> CashCount) {
        _state.value = _state.value.copy(cashCount = transform(_state.value.cashCount))
    }

    fun onNotesChanged(value: String) {
        _state.value = _state.value.copy(notes = value)
    }

    fun submitClose() {
        val current = _state.value
        val sessionId = current.sessionId ?: return
        if (current.isSubmitting) return
        _state.value = current.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            when (
                val result = registerSessionRepository.close(
                    sessionId = sessionId,
                    closingCashCents = current.countedCents,
                    notes = current.notes.trim().ifBlank { null },
                    breakdown = current.cashCount,
                )
            ) {
                is NetworkResult.Success ->
                    _state.value = current.copy(isSubmitting = false, report = result.data)
                is NetworkResult.Failure ->
                    _state.value = current.copy(isSubmitting = false, errorMessage = result.error.message)
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }

}

data class CloseRegisterUiState(
    val isLoadingPreview: Boolean = true,
    val sessionId: Long? = null,
    val expectedCashCents: Long? = null,
    val cashCount: CashCount = CashCount(),
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val report: SessionReport? = null,
) {
    /** Grand total counted, derived entirely from the denomination breakdown. */
    val countedCents: Long
        get() = cashCount.totalCents

    /** Exact / short / over, computed once a report exists. */
    val varianceCents: Long?
        get() = report?.let { countedCents - it.expectedCashCents }
}

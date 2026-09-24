package co.monveri.register.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.repository.Expense
import co.monveri.register.data.repository.ExpenseAuditEntry
import co.monveri.register.data.repository.ExpenseRepository
import co.monveri.register.network.AuthHeaderProvider
import co.monveri.register.network.AuthHeaders
import co.monveri.register.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Back Office → Expenses → detail. Read-only, matching iOS's v1 `ExpenseDetailView`: no edit,
 * approve, reject, or delete from mobile — those either don't have a mobile endpoint at all
 * (approve/reject/delete are web-back-office-only) or, for editing a still-pending expense,
 * belong on the create form's own re-open flow, which this Android slice doesn't build yet.
 */
@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    authHeaderProvider: AuthHeaderProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val expenseId: Long = savedStateHandle.get<String>(BackOfficeRoutes.ARG_EXPENSE_ID)?.toLongOrNull()
        ?: error("Missing or invalid ${BackOfficeRoutes.ARG_EXPENSE_ID} argument")

    val imageHeaders: Map<String, String> = buildMap {
        authHeaderProvider.storeKey()?.let { put(AuthHeaders.STORE_KEY, it) }
        authHeaderProvider.employeeId()?.let { put(AuthHeaders.EMPLOYEE_ID, it.toString()) }
    }

    private val _state = MutableStateFlow(ExpenseDetailUiState())
    val state: StateFlow<ExpenseDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun resolveImageUrl(relativeUrl: String): String = repository.resolveImageUrl(relativeUrl)

    fun load() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.get(expenseId)) {
                is NetworkResult.Success -> _state.value = ExpenseDetailUiState(
                    isLoading = false,
                    expense = result.data.expense,
                    audit = result.data.audit,
                )
                is NetworkResult.Failure -> _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }
}

data class ExpenseDetailUiState(
    val isLoading: Boolean = true,
    val expense: Expense? = null,
    val audit: List<ExpenseAuditEntry> = emptyList(),
    val errorMessage: String? = null,
)

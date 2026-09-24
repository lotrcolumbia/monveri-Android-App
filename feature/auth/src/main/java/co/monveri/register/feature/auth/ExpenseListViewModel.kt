package co.monveri.register.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.repository.Expense
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
 * Back Office → Expenses list. Paginated (`page`/`hasMore`, matching iOS's own "Load more" row —
 * no infinite-scroll auto-fetch on either platform), scoped server-side to the signed-in
 * employee's own submissions.
 */
@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    authHeaderProvider: AuthHeaderProvider,
) : ViewModel() {

    /** Headers a Coil [coil.request.ImageRequest] needs to fetch an authenticated receipt thumbnail. */
    val imageHeaders: Map<String, String> = buildMap {
        authHeaderProvider.storeKey()?.let { put(AuthHeaders.STORE_KEY, it) }
        authHeaderProvider.employeeId()?.let { put(AuthHeaders.EMPLOYEE_ID, it.toString()) }
    }

    private val _state = MutableStateFlow(ExpenseListUiState())
    val state: StateFlow<ExpenseListUiState> = _state.asStateFlow()

    // No init-time load: ExpenseListScreen triggers loadFirstPage() from an ON_RESUME lifecycle
    // observer instead, so returning from Add Expense (a separate nav destination, not a sheet —
    // there's no shared state to splice a new row into the way iOS's presented-sheet flow does)
    // re-fetches page 1 and picks up the just-created expense without a manual pull-to-refresh.

    fun resolveImageUrl(relativeUrl: String): String = repository.resolveImageUrl(relativeUrl)

    fun loadFirstPage() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.list(page = 1)) {
                is NetworkResult.Success -> _state.value = ExpenseListUiState(
                    isLoading = false,
                    items = result.data.items,
                    page = result.data.page,
                    hasMore = result.data.hasMore,
                )
                is NetworkResult.Failure -> _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }

    /**
     * Pull-to-refresh — matches iOS's `.refreshable` on `ExpensesTabView`. Separate from
     * [loadFirstPage]'s full-screen spinner: the list stays visible under the pull indicator
     * while this runs, only [ExpenseListUiState.isRefreshing] flips.
     */
    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.value = _state.value.copy(isRefreshing = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.list(page = 1)) {
                is NetworkResult.Success -> _state.value = _state.value.copy(
                    isRefreshing = false,
                    items = result.data.items,
                    page = result.data.page,
                    hasMore = result.data.hasMore,
                )
                is NetworkResult.Failure -> _state.value = _state.value.copy(
                    isRefreshing = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMore) return

        _state.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            when (val result = repository.list(page = current.page + 1)) {
                is NetworkResult.Success -> _state.value = _state.value.copy(
                    isLoadingMore = false,
                    items = _state.value.items + result.data.items,
                    page = result.data.page,
                    hasMore = result.data.hasMore,
                )
                is NetworkResult.Failure -> _state.value = _state.value.copy(
                    isLoadingMore = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }
}

data class ExpenseListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<Expense> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
)

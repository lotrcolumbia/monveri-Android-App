package co.monveri.register.feature.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.repository.CartRepository
import co.monveri.register.data.repository.Customer
import co.monveri.register.data.repository.CustomerRepository
import co.monveri.register.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the customer lookup screen. Live search with a 300ms debounce against
 * `customers/search.php`. Picking a customer calls [CartRepository.attachCustomer] and
 * dismisses the screen via [CustomerLookupUiState.selectedCustomer].
 */
@HiltViewModel
@OptIn(FlowPreview::class)
class CustomerLookupViewModel @Inject constructor(
    private val customers: CustomerRepository,
    private val cart: CartRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerLookupUiState(attached = cart.current().customer))
    val state: StateFlow<CustomerLookupUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(value: String) {
        _state.value = _state.value.copy(query = value, errorMessage = null)
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _state.value = _state.value.copy(isSearching = true)
            when (val result = customers.search(value)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(results = result.data, isSearching = false)
                }
                is NetworkResult.Failure -> {
                    _state.value = _state.value.copy(
                        results = emptyList(),
                        isSearching = false,
                        errorMessage = result.error.message,
                    )
                }
            }
        }
    }

    fun selectCustomer(customer: Customer) {
        cart.attachCustomer(customer)
        _state.value = _state.value.copy(attached = customer, selectedCustomer = customer)
    }

    fun detachCustomer() {
        cart.attachCustomer(null)
        _state.value = _state.value.copy(attached = null)
    }

    fun consumeSelection() {
        _state.value = _state.value.copy(selectedCustomer = null)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS: Long = 300
    }
}

data class CustomerLookupUiState(
    val query: String = "",
    val results: List<Customer> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val attached: Customer? = null,
    /** Non-null exactly once, when the cashier picks a row. The screen reads this to dismiss. */
    val selectedCustomer: Customer? = null,
)

package co.monveri.register.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.repository.ExpenseCategory
import co.monveri.register.data.repository.ExpensePaymentMethod
import co.monveri.register.data.repository.ExpenseRepository
import co.monveri.register.data.repository.NewExpense
import co.monveri.register.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

/**
 * Manual expense entry — mirrors the "Enter manually" half of iOS's `AddExpenseSheet` +
 * `ReceiptReviewView`. No camera/OCR path in this slice (see [AddExpenseScreen]'s doc); the form
 * fields and validation otherwise match iOS's `ExpenseDraft.validationErrors()` one-for-one.
 */
@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddExpenseUiState(dateText = LocalDate.now().toString()))
    val state: StateFlow<AddExpenseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = repository.categories()) {
                is NetworkResult.Success -> _state.value = _state.value.copy(
                    categories = result.data,
                    categoryId = _state.value.categoryId ?: result.data.firstOrNull()?.id,
                )
                is NetworkResult.Failure -> _state.value = _state.value.copy(
                    errorMessage = result.error.message,
                )
            }
        }
    }

    fun onVendorChanged(value: String) {
        _state.value = _state.value.copy(vendorName = value)
    }

    fun onDateChanged(value: String) {
        _state.value = _state.value.copy(dateText = value)
    }

    fun onTotalChanged(value: String) {
        _state.value = _state.value.copy(totalText = value)
    }

    fun onTaxChanged(value: String) {
        _state.value = _state.value.copy(taxText = value)
    }

    fun onSubtotalChanged(value: String) {
        _state.value = _state.value.copy(subtotalText = value, subtotalEdited = true)
    }

    fun onCategorySelected(categoryId: Long) {
        _state.value = _state.value.copy(categoryId = categoryId)
    }

    fun onPaymentMethodSelected(method: ExpensePaymentMethod) {
        _state.value = _state.value.copy(paymentMethod = method)
    }

    fun onNotesChanged(value: String) {
        _state.value = _state.value.copy(notes = value)
    }

    fun save() {
        val current = _state.value
        if (current.isSaving) return

        val vendor = current.vendorName.trim()
        val total = parseDollarsToCents(current.totalText)
        val categoryId = current.categoryId

        val errors = buildList {
            if (vendor.isEmpty()) add("Vendor is required")
            if (total == null || total <= 0) add("Total must be greater than zero")
            if (current.dateText.toLocalDateOrNull() == null) add("Date must be valid")
            if (categoryId == null) add("Category is required")
        }
        if (errors.isNotEmpty()) {
            _state.value = current.copy(errorMessage = errors.first())
            return
        }

        val tax = current.taxText.takeIf { it.isNotBlank() }?.let { parseDollarsToCents(it) }
        val subtotal = current.subtotalText.takeIf { it.isNotBlank() }?.let { parseDollarsToCents(it) }
            ?: tax?.let { total!! - it }

        _state.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val result = repository.create(
                NewExpense(
                    vendorName = vendor,
                    expenseDate = current.dateText,
                    amountCents = total!!,
                    taxAmountCents = tax,
                    subtotalAmountCents = subtotal,
                    categoryId = categoryId!!,
                    paymentMethod = current.paymentMethod,
                    notes = current.notes.trim(),
                ),
            )
            _state.value = when (result) {
                is NetworkResult.Success -> _state.value.copy(isSaving = false, saved = true)
                is NetworkResult.Failure -> _state.value.copy(isSaving = false, errorMessage = result.error.message)
            }
        }
    }

    private fun parseDollarsToCents(text: String): Long? {
        val value = text.trim().toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

data class AddExpenseUiState(
    val vendorName: String = "",
    val dateText: String = "",
    val totalText: String = "",
    val taxText: String = "",
    val subtotalText: String = "",
    val subtotalEdited: Boolean = false,
    val categories: List<ExpenseCategory> = emptyList(),
    val categoryId: Long? = null,
    val paymentMethod: ExpensePaymentMethod = ExpensePaymentMethod.CREDIT,
    val notes: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

package co.monveri.register.feature.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.repository.Cart
import co.monveri.register.data.repository.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Cart screen state holder. Pulls the [Cart] directly from [CartRepository.observeCart] — all
 * pricing math (subtotal, discount, tax, total) lives inside [Cart.totals] via [PricingEngine]
 * so the ViewModel never duplicates the calculation.
 */
@HiltViewModel
class CartViewModel @Inject constructor(
    private val cart: CartRepository,
) : ViewModel() {

    val cartState: StateFlow<Cart> = cart.observeCart().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = cart.current(),
    )

    fun updateQuantity(lineId: String, quantity: Int) = cart.updateQuantity(lineId, quantity)

    fun removeLine(lineId: String) = cart.removeLine(lineId)

    fun setDiscount(cents: Long) = cart.setDiscount(cents)

    fun detachCustomer() = cart.attachCustomer(null)

    fun clearCart() = cart.clear()

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}

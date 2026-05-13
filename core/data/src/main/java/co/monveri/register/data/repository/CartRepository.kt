package co.monveri.register.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cart state. Mirrors the iOS v1 decision to skip draft persistence — Phase 9 adds
 * a Room-backed snapshot when offline mode lands. Until then the cart resets on process death,
 * matching iOS.
 */
interface CartRepository {
    fun observeCart(): Flow<Cart>
    fun addItem(item: CartLine)
    fun removeItem(productId: Long)
    fun updateQuantity(productId: Long, quantity: Int)
    fun applyDiscount(discount: CartDiscount?)
    fun attachCustomer(customerId: Long?)
    fun clear()
}

data class Cart(
    val lines: List<CartLine> = emptyList(),
    val discount: CartDiscount? = null,
    val customerId: Long? = null,
) {
    val subtotalCents: Long get() = lines.sumOf { it.unitPriceCents * it.quantity }
}

data class CartLine(
    val productId: Long,
    val name: String,
    val unitPriceCents: Long,
    val quantity: Int,
)

data class CartDiscount(
    val label: String,
    val amountCents: Long,
)

@Singleton
class CartRepositoryImpl @Inject constructor() : CartRepository {

    private val _cart = MutableStateFlow(Cart())

    override fun observeCart(): Flow<Cart> = _cart.asStateFlow()

    override fun addItem(item: CartLine) {
        _cart.value = _cart.value.copy(
            lines = mergeLine(_cart.value.lines, item),
        )
    }

    override fun removeItem(productId: Long) {
        _cart.value = _cart.value.copy(
            lines = _cart.value.lines.filterNot { it.productId == productId },
        )
    }

    override fun updateQuantity(productId: Long, quantity: Int) {
        if (quantity <= 0) {
            removeItem(productId)
            return
        }
        _cart.value = _cart.value.copy(
            lines = _cart.value.lines.map { line ->
                if (line.productId == productId) line.copy(quantity = quantity) else line
            },
        )
    }

    override fun applyDiscount(discount: CartDiscount?) {
        _cart.value = _cart.value.copy(discount = discount)
    }

    override fun attachCustomer(customerId: Long?) {
        _cart.value = _cart.value.copy(customerId = customerId)
    }

    override fun clear() {
        _cart.value = Cart()
    }

    private fun mergeLine(existing: List<CartLine>, incoming: CartLine): List<CartLine> {
        val match = existing.firstOrNull { it.productId == incoming.productId }
        return if (match == null) {
            existing + incoming
        } else {
            existing.map { line ->
                if (line.productId == incoming.productId) {
                    line.copy(quantity = line.quantity + incoming.quantity)
                } else {
                    line
                }
            }
        }
    }
}

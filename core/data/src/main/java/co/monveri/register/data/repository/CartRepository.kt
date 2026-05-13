package co.monveri.register.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cart state. Mirrors the iOS v1 decision to skip draft persistence — Phase 9 adds
 * a Room-backed snapshot when offline mode lands. Until then the cart resets on process death,
 * matching iOS.
 *
 * All mutators use [MutableStateFlow.update] (CAS semantics) so concurrent writers can't drop
 * each other's changes. Non-positive quantities are rejected at the input boundary and any
 * stale state with `quantity <= 0` is filtered out at merge time.
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
        if (item.quantity <= 0) return
        _cart.update { current ->
            current.copy(lines = mergeLine(current.lines, item))
        }
    }

    override fun removeItem(productId: Long) {
        _cart.update { current ->
            current.copy(lines = current.lines.filterNot { it.productId == productId })
        }
    }

    override fun updateQuantity(productId: Long, quantity: Int) {
        if (quantity <= 0) {
            removeItem(productId)
            return
        }
        _cart.update { current ->
            current.copy(
                lines = current.lines.map { line ->
                    if (line.productId == productId) line.copy(quantity = quantity) else line
                },
            )
        }
    }

    override fun applyDiscount(discount: CartDiscount?) {
        _cart.update { current -> current.copy(discount = discount) }
    }

    override fun attachCustomer(customerId: Long?) {
        _cart.update { current -> current.copy(customerId = customerId) }
    }

    override fun clear() {
        _cart.update { Cart() }
    }

    private fun mergeLine(existing: List<CartLine>, incoming: CartLine): List<CartLine> {
        // Defensive: filter out any pre-existing rows with non-positive quantity.
        val sanitized = existing.filter { it.quantity > 0 }
        val match = sanitized.firstOrNull { it.productId == incoming.productId }
        return if (match == null) {
            sanitized + incoming
        } else {
            sanitized.mapNotNull { line ->
                if (line.productId == incoming.productId) {
                    val merged = line.quantity + incoming.quantity
                    if (merged > 0) line.copy(quantity = merged) else null
                } else {
                    line
                }
            }
        }
    }
}

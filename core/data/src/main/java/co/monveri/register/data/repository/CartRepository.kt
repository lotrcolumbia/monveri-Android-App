package co.monveri.register.data.repository

import co.monveri.register.pricing.CartTotals
import co.monveri.register.pricing.PricingEngine
import co.monveri.register.pricing.PricingLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cart state. Mirrors the iOS v1 decision to skip draft persistence — Phase 9 adds a
 * Room-backed snapshot when offline mode lands. Until then the cart resets on process death,
 * matching iOS.
 *
 * Phase 3 expands the cart model with:
 *  - per-line variant + taxable flag (so [PricingEngine] sees the right inputs)
 *  - cart-level customer snapshot (so the chip survives a backend customer edit mid-checkout)
 *  - cart-level [discountCents] (whole-ticket discount preview)
 *
 * All mutators use [MutableStateFlow.update] (CAS semantics) so concurrent writers can't drop
 * each other's changes. Non-positive quantities are filtered at the input boundary and at merge
 * time.
 */
interface CartRepository {
    fun observeCart(): Flow<Cart>
    fun current(): Cart

    /**
     * Add or merge a line. Identity is `productId + variantId`; variants are independent rows from
     * their parent product so a cart can contain "Red" and "Blue" side-by-side.
     */
    fun addItem(line: CartLine)

    /** Remove a specific cart line by its identity. */
    fun removeLine(lineId: String)

    /** Set a new quantity for a line. `quantity <= 0` removes the line. */
    fun updateQuantity(lineId: String, quantity: Int)

    /** Replace the whole-ticket discount in cents. Pass `0` to clear. */
    fun setDiscount(discountCents: Long)

    /** Attach or detach the customer snapshot used by the cart header chip. */
    fun attachCustomer(customer: Customer?)

    /** Reset the cart back to empty — invoked after a successful checkout. */
    fun clear()
}

/**
 * Cart state at a point in time. [totals] is computed via [PricingEngine] every time the cart
 * changes so collectors get the new subtotal/tax/total atomically.
 */
data class Cart(
    val lines: List<CartLine> = emptyList(),
    val discountCents: Long = 0L,
    val customer: Customer? = null,
    val taxRateBps: Int = DEFAULT_TAX_RATE_BPS,
) {
    val totals: CartTotals
        get() = PricingEngine.calculate(
            lines = lines.map { PricingLine(it.unitPriceCents, it.quantity, it.isTaxable) },
            taxRateBps = taxRateBps,
            ticketDiscountCents = discountCents,
        )

    val itemCount: Int get() = lines.sumOf { it.quantity }

    companion object {
        /** Carolina Thread Place tax rate, used as a preview default until Phase 4 fetches it. */
        const val DEFAULT_TAX_RATE_BPS: Int = 875
    }
}

/**
 * A single line in the cart. `lineId` is stable for the row's lifetime — used as the key for
 * removal and quantity updates so re-ordering or merging doesn't shift selection.
 */
data class CartLine(
    val lineId: String = UUID.randomUUID().toString(),
    val productId: Long,
    val variantId: Long?,
    val name: String,
    val variantLabel: String?,
    val sku: String?,
    val unitPriceCents: Long,
    val quantity: Int,
    val isTaxable: Boolean,
)

@Singleton
class CartRepositoryImpl @Inject constructor() : CartRepository {

    private val cart = MutableStateFlow(Cart())

    override fun observeCart(): Flow<Cart> = cart.asStateFlow()

    override fun current(): Cart = cart.value

    override fun addItem(line: CartLine) {
        if (line.quantity <= 0) return
        cart.update { current -> current.copy(lines = merge(current.lines, line)) }
    }

    override fun removeLine(lineId: String) {
        cart.update { current ->
            current.copy(lines = current.lines.filterNot { it.lineId == lineId })
        }
    }

    override fun updateQuantity(lineId: String, quantity: Int) {
        if (quantity <= 0) {
            removeLine(lineId)
            return
        }
        cart.update { current ->
            current.copy(
                lines = current.lines.map { line ->
                    if (line.lineId == lineId) line.copy(quantity = quantity) else line
                },
            )
        }
    }

    override fun setDiscount(discountCents: Long) {
        cart.update { current -> current.copy(discountCents = discountCents.coerceAtLeast(0L)) }
    }

    override fun attachCustomer(customer: Customer?) {
        cart.update { current -> current.copy(customer = customer) }
    }

    override fun clear() {
        cart.update { Cart() }
    }

    private fun merge(existing: List<CartLine>, incoming: CartLine): List<CartLine> {
        val sanitized = existing.filter { it.quantity > 0 }
        val match = sanitized.firstOrNull {
            it.productId == incoming.productId && it.variantId == incoming.variantId
        }
        return if (match == null) {
            sanitized + incoming
        } else {
            sanitized.mapNotNull { line ->
                if (line.lineId == match.lineId) {
                    val merged = line.quantity + incoming.quantity
                    if (merged > 0) line.copy(quantity = merged) else null
                } else {
                    line
                }
            }
        }
    }
}

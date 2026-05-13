package co.monveri.register.pricing

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure-Kotlin pricing math. Mirrors the iOS `TaxCalculator` enum so a cart with the same lines
 * produces the same totals on iPhone and Android. No Android types here — keep it JVM-only and
 * unit-testable.
 *
 * Order of operations (the same as the backend `sections/check_discounts.php`):
 *   1. Per-line `extended = unitPriceCents * quantity` (no intermediate rounding)
 *   2. Cart discount clamped to [0, subtotal]
 *   3. Allocate the discount across taxable vs non-taxable lines pro-rata
 *   4. Tax applies to (taxable subtotal − allocated taxable discount)
 *   5. Total = subtotal − discount + tax
 *
 * All inputs/outputs are integer cents — rounding happens once, at the final tax step, half-up.
 */
object PricingEngine {

    /**
     * Calculate totals for [lines] under [taxRateBps] (basis points, e.g. 875 = 8.75%) and an
     * optional [ticketDiscountCents] (whole-cart discount). When [customerTaxExempt] is true the
     * tax line is zeroed; the discount allocation still runs (so reports remain consistent).
     */
    fun calculate(
        lines: List<PricingLine>,
        taxRateBps: Int,
        ticketDiscountCents: Long = 0L,
        customerTaxExempt: Boolean = false,
    ): CartTotals {
        val subtotal = lines.sumOf { it.extendedCents }
        val nonTaxableSubtotal = lines.filterNot { it.isTaxable }.sumOf { it.extendedCents }
        val taxableSubtotal = subtotal - nonTaxableSubtotal

        val clampedDiscount = ticketDiscountCents.coerceIn(0L, subtotal.coerceAtLeast(0L))

        // Pro-rata: allocate the discount onto taxable lines in proportion to their share of the
        // pre-discount subtotal. Avoids the trap where a $10 discount on a $5-taxable / $20-total
        // cart wipes out the entire taxable amount instead of $2.
        val allocatedToTaxable = if (subtotal > 0) {
            BigDecimal(clampedDiscount)
                .multiply(BigDecimal(taxableSubtotal))
                .divide(BigDecimal(subtotal), 10, RoundingMode.HALF_UP)
                .toLong()
        } else {
            0L
        }

        val taxableAfterDiscount = (taxableSubtotal - allocatedToTaxable).coerceAtLeast(0L)
        val tax = if (customerTaxExempt) {
            0L
        } else {
            BigDecimal(taxableAfterDiscount)
                .multiply(BigDecimal(taxRateBps))
                .divide(BigDecimal(BASIS_POINTS), 0, RoundingMode.HALF_UP)
                .toLong()
        }

        val discountedSubtotal = (subtotal - clampedDiscount).coerceAtLeast(0L)
        val total = discountedSubtotal + tax

        return CartTotals(
            subtotalCents = subtotal,
            taxableSubtotalCents = taxableSubtotal,
            nonTaxableSubtotalCents = nonTaxableSubtotal,
            discountCents = clampedDiscount,
            allocatedTaxableDiscountCents = allocatedToTaxable,
            taxableAfterDiscountCents = taxableAfterDiscount,
            taxCents = tax,
            totalCents = total,
        )
    }

    private const val BASIS_POINTS: Long = 10_000
}

/** Snapshot of a single cart line fed into [PricingEngine.calculate]. */
data class PricingLine(
    val unitPriceCents: Long,
    val quantity: Int,
    val isTaxable: Boolean,
) {
    init {
        require(quantity > 0) { "PricingLine.quantity must be positive, was $quantity" }
        require(unitPriceCents >= 0) { "PricingLine.unitPriceCents must be non-negative, was $unitPriceCents" }
    }

    val extendedCents: Long get() = unitPriceCents * quantity
}

/**
 * Result returned by [PricingEngine.calculate]. Every intermediate is exposed so the UI can render
 * "subtotal / discount / tax / total" without recomputing anything.
 */
data class CartTotals(
    val subtotalCents: Long,
    val taxableSubtotalCents: Long,
    val nonTaxableSubtotalCents: Long,
    val discountCents: Long,
    val allocatedTaxableDiscountCents: Long,
    val taxableAfterDiscountCents: Long,
    val taxCents: Long,
    val totalCents: Long,
) {
    companion object {
        val EMPTY = CartTotals(0, 0, 0, 0, 0, 0, 0, 0)
    }
}

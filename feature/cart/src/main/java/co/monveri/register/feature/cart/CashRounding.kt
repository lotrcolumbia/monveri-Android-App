package co.monveri.register.feature.cart

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rounds a cash total to the nearest nickel (U.S. penny phase-out) — mirrors iOS's
 * `CashRounding.swift`. Symmetric for negative totals (refunds). Card/Other/Split tenders never
 * round; only cash does.
 */
object CashRounding {
    fun roundedTotalCents(totalCents: Long): Long =
        BigDecimal(totalCents)
            .divide(NICKEL, 0, RoundingMode.HALF_UP)
            .multiply(NICKEL)
            .toLong()

    private val NICKEL = BigDecimal(5)
}

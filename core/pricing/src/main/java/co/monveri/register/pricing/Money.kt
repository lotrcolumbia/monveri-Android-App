package co.monveri.register.pricing

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency lives in integer cents everywhere except the network boundary, where backend prices
 * arrive as floats (e.g. `19.99`). [centsOf] is the *only* place the floating-point → cents
 * conversion happens, so half-up rounding stays consistent with the iOS NumberFormatter behavior
 * and the PHP backend's `number_format($amount, 2)` writes.
 */
fun centsOf(dollars: Double): Long =
    BigDecimal(dollars.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

/** Convenience for tests / repository layers building cart lines from already-rounded cents. */
fun centsOf(dollars: String): Long =
    BigDecimal(dollars).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

/** Formats `cents` as `$1,234.56`. UI presentation only — never the rounding boundary. */
fun formatCents(cents: Long): String {
    val negative = cents < 0
    val abs = if (negative) -cents else cents
    val whole = abs / CENTS_PER_DOLLAR
    val frac = abs % CENTS_PER_DOLLAR
    val withCommas = StringBuilder()
    val wholeStr = whole.toString()
    for ((index, char) in wholeStr.withIndex()) {
        val remaining = wholeStr.length - index
        withCommas.append(char)
        if (remaining > 1 && remaining % 3 == 1) {
            withCommas.append(',')
        }
    }
    val sign = if (negative) "-" else ""
    return "$sign$$withCommas.${frac.toString().padStart(2, '0')}"
}

private const val CENTS_PER_DOLLAR: Long = 100

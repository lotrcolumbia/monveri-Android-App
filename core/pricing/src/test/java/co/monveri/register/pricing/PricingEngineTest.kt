package co.monveri.register.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PricingEngineTest {

    private fun line(price: Long, qty: Int, taxable: Boolean = true) =
        PricingLine(unitPriceCents = price, quantity = qty, isTaxable = taxable)

    @Test
    fun `empty cart returns zeros`() {
        val totals = PricingEngine.calculate(lines = emptyList(), taxRateBps = TAX_8_75)
        assertEquals(CartTotals.EMPTY, totals)
    }

    @Test
    fun `single taxable line subtotal equals extended price`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(price = 1_999, qty = 1)),
            taxRateBps = TAX_8_75,
        )
        assertEquals(1_999, totals.subtotalCents)
        assertEquals(1_999, totals.taxableSubtotalCents)
        assertEquals(0, totals.nonTaxableSubtotalCents)
    }

    @Test
    fun `multiple lines sum subtotal`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(1_000, 2), line(500, 3), line(250, 1)),
            taxRateBps = TAX_8_75,
        )
        assertEquals(3_750, totals.subtotalCents) // 2000 + 1500 + 250
    }

    @Test
    fun `tax applies only to taxable subtotal`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(1_000, 1, taxable = true), line(1_000, 1, taxable = false)),
            taxRateBps = TAX_10_PCT,
        )
        assertEquals(2_000, totals.subtotalCents)
        assertEquals(1_000, totals.taxableSubtotalCents)
        assertEquals(1_000, totals.nonTaxableSubtotalCents)
        // 10% of $10 taxable = $1.00
        assertEquals(100, totals.taxCents)
        assertEquals(2_100, totals.totalCents)
    }

    @Test
    fun `discount clamps to zero when negative`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(1_000, 1)),
            taxRateBps = TAX_8_75,
            ticketDiscountCents = -500,
        )
        assertEquals(0, totals.discountCents)
        assertEquals(1_000, totals.subtotalCents)
    }

    @Test
    fun `discount clamps to subtotal when exceeding it`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(1_000, 1)),
            taxRateBps = TAX_8_75,
            ticketDiscountCents = 9_999,
        )
        assertEquals(1_000, totals.discountCents)
        assertEquals(0, totals.totalCents)
    }

    @Test
    fun `discount fully on taxable cart reduces taxable amount before tax`() {
        // $50 cart, $10 discount, 10% tax → tax on $40 = $4 → total = $40 + $4 = $44
        val totals = PricingEngine.calculate(
            lines = listOf(line(5_000, 1)),
            taxRateBps = TAX_10_PCT,
            ticketDiscountCents = 1_000,
        )
        assertEquals(5_000, totals.subtotalCents)
        assertEquals(1_000, totals.discountCents)
        assertEquals(4_000, totals.taxableAfterDiscountCents)
        assertEquals(400, totals.taxCents)
        assertEquals(4_400, totals.totalCents)
    }

    @Test
    fun `discount allocates pro-rata across taxable and non-taxable lines`() {
        // Cart: $50 taxable + $50 non-taxable = $100 subtotal. Discount = $20.
        // Allocated to taxable: $20 * ($50/$100) = $10. Taxable after = $40. Tax = $4.
        // Total = $100 - $20 + $4 = $84.
        val totals = PricingEngine.calculate(
            lines = listOf(
                line(price = 5_000, qty = 1, taxable = true),
                line(price = 5_000, qty = 1, taxable = false),
            ),
            taxRateBps = TAX_10_PCT,
            ticketDiscountCents = 2_000,
        )
        assertEquals(10_000, totals.subtotalCents)
        assertEquals(5_000, totals.taxableSubtotalCents)
        assertEquals(2_000, totals.discountCents)
        assertEquals(1_000, totals.allocatedTaxableDiscountCents)
        assertEquals(4_000, totals.taxableAfterDiscountCents)
        assertEquals(400, totals.taxCents)
        assertEquals(8_400, totals.totalCents)
    }

    @Test
    fun `customer tax exempt zeros out tax even when taxable lines exist`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(1_000, 1, taxable = true)),
            taxRateBps = TAX_10_PCT,
            customerTaxExempt = true,
        )
        assertEquals(1_000, totals.subtotalCents)
        assertEquals(0, totals.taxCents)
        assertEquals(1_000, totals.totalCents)
    }

    @Test
    fun `zero tax rate produces no tax`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(1_000, 3)),
            taxRateBps = 0,
        )
        assertEquals(0, totals.taxCents)
        assertEquals(3_000, totals.totalCents)
    }

    @Test
    fun `eight point seven five percent rounds half up`() {
        // $10.00 × 8.75% = $0.875 → rounds to $0.88
        val totals = PricingEngine.calculate(
            lines = listOf(line(1_000, 1)),
            taxRateBps = TAX_8_75,
        )
        assertEquals(88, totals.taxCents)
        assertEquals(1_088, totals.totalCents)
    }

    @Test
    fun `single penny tax rounds correctly at boundary`() {
        // $0.06 × 8.75% = $0.00525 → rounds to $0.01 (half-up)
        val totals = PricingEngine.calculate(
            lines = listOf(line(6, 1)),
            taxRateBps = TAX_8_75,
        )
        assertEquals(1, totals.taxCents)
    }

    @Test
    fun `quantity multiplies unit price exactly`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(price = 333, qty = 7)),
            taxRateBps = TAX_8_75,
        )
        assertEquals(2_331, totals.subtotalCents) // 333 × 7
    }

    @Test
    fun `discount equal to subtotal zeros total before tax`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(2_500, 1)),
            taxRateBps = TAX_10_PCT,
            ticketDiscountCents = 2_500,
        )
        assertEquals(0, totals.taxableAfterDiscountCents)
        assertEquals(0, totals.taxCents)
        assertEquals(0, totals.totalCents)
    }

    @Test
    fun `all non-taxable lines never tax even with high rate`() {
        val totals = PricingEngine.calculate(
            lines = listOf(line(1_000, 1, taxable = false), line(500, 2, taxable = false)),
            taxRateBps = TAX_8_75,
        )
        assertEquals(2_000, totals.subtotalCents)
        assertEquals(0, totals.taxableSubtotalCents)
        assertEquals(0, totals.taxCents)
        assertEquals(2_000, totals.totalCents)
    }

    @Test
    fun `mixed cart with discount and exempt customer matches expected formula`() {
        // $40 taxable + $10 non-taxable = $50, discount $5, exempt → no tax.
        // Total = $50 - $5 = $45.
        val totals = PricingEngine.calculate(
            lines = listOf(line(4_000, 1, taxable = true), line(1_000, 1, taxable = false)),
            taxRateBps = TAX_10_PCT,
            ticketDiscountCents = 500,
            customerTaxExempt = true,
        )
        assertEquals(5_000, totals.subtotalCents)
        assertEquals(500, totals.discountCents)
        assertEquals(0, totals.taxCents)
        assertEquals(4_500, totals.totalCents)
    }

    @Test
    fun `pricing line rejects zero or negative quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            PricingLine(unitPriceCents = 100, quantity = 0, isTaxable = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PricingLine(unitPriceCents = 100, quantity = -1, isTaxable = true)
        }
    }

    @Test
    fun `pricing line rejects negative price`() {
        assertThrows(IllegalArgumentException::class.java) {
            PricingLine(unitPriceCents = -1, quantity = 1, isTaxable = true)
        }
    }

    @Test
    fun `formatCents renders standard money string`() {
        assertEquals("$0.00", formatCents(0))
        assertEquals("$0.05", formatCents(5))
        assertEquals("$1.00", formatCents(100))
        assertEquals("$1,234.56", formatCents(123_456))
        assertEquals("$1,000,000.00", formatCents(100_000_000))
        assertEquals("-$1.50", formatCents(-150))
    }

    @Test
    fun `centsOf rounds half up from float dollars`() {
        assertEquals(1_999, centsOf(19.99))
        assertEquals(1_000, centsOf(10.0))
        assertEquals(1, centsOf(0.005))
        assertEquals(100, centsOf("1.00"))
    }

    private companion object {
        const val TAX_8_75: Int = 875
        const val TAX_10_PCT: Int = 1_000
    }
}

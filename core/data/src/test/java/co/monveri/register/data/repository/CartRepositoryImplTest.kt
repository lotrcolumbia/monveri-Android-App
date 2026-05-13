package co.monveri.register.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartRepositoryImplTest {

    private fun line(
        productId: Long,
        qty: Int = 1,
        variantId: Long? = null,
        priceCents: Long = 1_000,
        taxable: Boolean = true,
    ) = CartLine(
        productId = productId,
        variantId = variantId,
        name = "Product $productId",
        variantLabel = null,
        sku = null,
        unitPriceCents = priceCents,
        quantity = qty,
        isTaxable = taxable,
    )

    @Test
    fun `adding a new line appends it`() = runTest {
        val repo = CartRepositoryImpl()
        repo.addItem(line(productId = 1L))

        assertEquals(1, repo.current().lines.size)
        assertEquals(1, repo.current().lines.first().quantity)
    }

    @Test
    fun `adding the same product merges quantities`() = runTest {
        val repo = CartRepositoryImpl()
        repo.addItem(line(productId = 1L, qty = 2))
        repo.addItem(line(productId = 1L, qty = 3))

        assertEquals(1, repo.current().lines.size)
        assertEquals(5, repo.current().lines.first().quantity)
    }

    @Test
    fun `same product different variants are independent lines`() = runTest {
        val repo = CartRepositoryImpl()
        repo.addItem(line(productId = 1L, variantId = 10L))
        repo.addItem(line(productId = 1L, variantId = 20L))

        assertEquals(2, repo.current().lines.size)
    }

    @Test
    fun `non-positive quantity is rejected at add time`() = runTest {
        val repo = CartRepositoryImpl()
        repo.addItem(line(productId = 1L, qty = 0))
        repo.addItem(line(productId = 2L, qty = -1))

        assertTrue(repo.current().lines.isEmpty())
    }

    @Test
    fun `updateQuantity to zero removes the line`() = runTest {
        val repo = CartRepositoryImpl()
        repo.addItem(line(productId = 1L, qty = 2))
        val id = repo.current().lines.first().lineId
        repo.updateQuantity(id, 0)

        assertTrue(repo.current().lines.isEmpty())
    }

    @Test
    fun `updateQuantity changes only the matched line`() = runTest {
        val repo = CartRepositoryImpl()
        repo.addItem(line(productId = 1L, qty = 1))
        repo.addItem(line(productId = 2L, qty = 1))
        val firstId = repo.current().lines.first().lineId
        repo.updateQuantity(firstId, 4)

        assertEquals(4, repo.current().lines.first().quantity)
        assertEquals(1, repo.current().lines.last().quantity)
    }

    @Test
    fun `clear empties everything including customer`() = runTest {
        val repo = CartRepositoryImpl()
        repo.addItem(line(productId = 1L))
        repo.attachCustomer(
            Customer(
                id = 1L,
                firstName = "Jane",
                lastName = "Doe",
                company = null,
                phone = null,
                email = null,
                loyaltyCardNumber = null,
                currentPoints = 0,
                tierName = null,
            ),
        )
        repo.setDiscount(500)
        repo.clear()

        val cart = repo.current()
        assertTrue(cart.lines.isEmpty())
        assertNull(cart.customer)
        assertEquals(0L, cart.discountCents)
    }

    @Test
    fun `cart totals reflect pricing engine outputs`() = runTest {
        val repo = CartRepositoryImpl()
        repo.addItem(line(productId = 1L, qty = 1, priceCents = 1_000))
        repo.addItem(line(productId = 2L, qty = 2, priceCents = 500))

        val cart = repo.current()
        // $10 + 2×$5 = $20 subtotal. Default 8.75% on full taxable cart: tax = round($20×0.0875) = 175 cents → $1.75
        assertEquals(2_000, cart.totals.subtotalCents)
        assertEquals(175, cart.totals.taxCents)
        assertEquals(2_175, cart.totals.totalCents)
    }

    @Test
    fun `setDiscount clamps negatives to zero`() = runTest {
        val repo = CartRepositoryImpl()
        repo.addItem(line(productId = 1L))
        repo.setDiscount(-50)

        assertEquals(0L, repo.current().discountCents)
    }

    @Test
    fun `observeCart emits on every mutation`() = runTest {
        val repo = CartRepositoryImpl()
        repo.observeCart().test {
            assertTrue(awaitItem().lines.isEmpty())
            repo.addItem(line(productId = 1L))
            assertEquals(1, awaitItem().lines.size)
            repo.removeLine(repo.current().lines.first().lineId)
            assertTrue(awaitItem().lines.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

package co.monveri.register.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import co.monveri.register.data.repository.CartLine
import co.monveri.register.data.repository.CartRepository
import co.monveri.register.data.repository.CatalogRepository
import co.monveri.register.data.repository.Product
import co.monveri.register.data.repository.ProductVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Drives the product detail screen — variant selection, quantity stepper, add-to-cart.
 *
 * Phase 3 sources detail data from the catalog snapshot already in memory. Phase 9 will swap to
 * the local Room snapshot; either way the ViewModel reads via [CatalogRepository.current] so the
 * call site doesn't change.
 */
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val cart: CartRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val productId: Long = savedStateHandle.get<String>(CatalogRoutes.ARG_PRODUCT_ID)?.toLongOrNull()
        ?: error("Missing or invalid ${CatalogRoutes.ARG_PRODUCT_ID} argument")

    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

    fun onVariantSelected(variant: ProductVariant?) {
        _state.value = _state.value.copy(selectedVariant = variant)
    }

    fun onQuantityChange(delta: Int) {
        val next = (_state.value.quantity + delta).coerceAtLeast(MIN_QUANTITY)
        _state.value = _state.value.copy(quantity = next)
    }

    fun setQuantity(quantity: Int) {
        _state.value = _state.value.copy(quantity = quantity.coerceAtLeast(MIN_QUANTITY))
    }

    fun addToCart(): Boolean {
        val current = _state.value
        val product = current.product ?: return false
        val variant = current.selectedVariant
        val line = CartLine(
            productId = product.id,
            variantId = variant?.id,
            name = product.name,
            variantLabel = variant?.displayLabel,
            sku = variant?.sku ?: product.sku,
            unitPriceCents = variant?.priceCents ?: product.priceCents,
            quantity = current.quantity,
            isTaxable = product.isTaxable,
        )
        cart.addItem(line)
        return true
    }

    private fun buildInitialState(): ProductDetailUiState {
        val snapshot = catalog.current()
        val product = snapshot.findProduct(productId)
        val variants = snapshot.variantsOf(productId)
        return ProductDetailUiState(
            product = product,
            variants = variants,
            selectedVariant = variants.firstOrNull(),
            quantity = 1,
            notFound = product == null,
        )
    }

    private companion object {
        const val MIN_QUANTITY: Int = 1
    }
}

/** Immutable view-model state for the product detail screen. */
data class ProductDetailUiState(
    val product: Product? = null,
    val variants: List<ProductVariant> = emptyList(),
    val selectedVariant: ProductVariant? = null,
    val quantity: Int = 1,
    val notFound: Boolean = false,
) {
    val effectivePriceCents: Long
        get() = selectedVariant?.priceCents ?: product?.priceCents ?: 0L
    val effectiveStock: Int
        get() = selectedVariant?.stockQuantity ?: product?.stockQuantity ?: 0
}

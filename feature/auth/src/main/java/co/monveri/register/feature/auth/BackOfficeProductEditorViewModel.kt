package co.monveri.register.feature.auth

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.repository.BackOfficeProduct
import co.monveri.register.data.repository.BackOfficeProductVariant
import co.monveri.register.data.repository.BackOfficeRepository
import co.monveri.register.data.repository.ProductImage
import co.monveri.register.data.repository.VariantOp
import co.monveri.register.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

enum class EditorTab { BASICS, DETAILS, PRICING, IMAGES, VARIANTS }

/**
 * Edits every field iOS's tabbed editor exposes (Basics/Details/Pricing/Variants) plus product
 * photos (Images tab). Basics/Details/Pricing/Variants are staged locally and committed together
 * on [save] — one full-row-overwrite `save.php` call, now also carrying a `variants` op-list in
 * the same request. Images are their own immediate-effect calls (upload/delete/set-primary each
 * hit the server right away, independent of the Save button) — mirrors the split in the real iOS
 * + backend behavior (see [BackOfficeRepository]'s doc).
 *
 * Field setters are collapsed into [updateState] (a raw `copy`-transform) rather than one method
 * per field — with ~25 editable fields across four tabs, dedicated setters would blow well past
 * detekt's per-class function-count limit for no real benefit; the few fields needing input
 * filtering (digit-only quantities) or side effects (images, variants, save) keep dedicated
 * methods below.
 */
@HiltViewModel
class BackOfficeProductEditorViewModel @Inject constructor(
    private val repository: BackOfficeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val productId: Long = savedStateHandle.get<String>(BackOfficeRoutes.ARG_PRODUCT_ID)?.toLongOrNull()
        ?: error("Missing or invalid ${BackOfficeRoutes.ARG_PRODUCT_ID} argument")

    /** The full record as fetched — carries every field the form doesn't edit. */
    private var original: BackOfficeProduct? = null
    private var nextTempVariantKey = -1L

    private val _state = MutableStateFlow(BackOfficeProductEditorUiState())
    val state: StateFlow<BackOfficeProductEditorUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun resolveImageUrl(filename: String): String = repository.productImageUrl(filename)

    /** Generic field mutator — see the class doc for why this replaces per-field setters. */
    fun updateState(transform: (BackOfficeProductEditorUiState) -> BackOfficeProductEditorUiState) {
        _state.value = transform(_state.value)
    }

    fun onQuantityChanged(value: String) = updateState { it.copy(quantityText = value.filter(Char::isDigit)) }
    fun onReorderPointChanged(value: String) = updateState { it.copy(reorderPointText = value.filter(Char::isDigit)) }
    fun onOrderQuantityChanged(value: String) = updateState { it.copy(orderQuantityText = value.filter(Char::isDigit)) }

    private fun load() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.getProduct(productId)) {
                is NetworkResult.Success -> {
                    val product = result.data
                    original = product
                    _state.value = product.toUiState(activeTab = _state.value.activeTab)
                }
                is NetworkResult.Failure ->
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.error.message)
            }
        }
    }

    // ---- Images ---------------------------------------------------------------------------

    /** Runs [ImageCompressor] off the main thread, then uploads immediately. */
    fun onImagePicked(resolver: ContentResolver, uri: Uri) {
        _state.value = _state.value.copy(isUploadingImage = true, errorMessage = null)
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.Default) { ImageCompressor.compress(resolver, uri) }
            if (bytes == null) {
                _state.value = _state.value.copy(isUploadingImage = false, errorMessage = "Could not read that photo")
                return@launch
            }
            when (val result = repository.uploadProductImage(productId, bytes)) {
                is NetworkResult.Success -> _state.value = _state.value.copy(
                    isUploadingImage = false,
                    images = _state.value.images + result.data,
                )
                is NetworkResult.Failure -> _state.value = _state.value.copy(
                    isUploadingImage = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }

    fun onDeleteImage(imageId: Long) {
        viewModelScope.launch {
            when (val result = repository.deleteProductImage(productId, imageId)) {
                is NetworkResult.Success -> _state.value = _state.value.copy(images = result.data)
                is NetworkResult.Failure -> _state.value = _state.value.copy(errorMessage = result.error.message)
            }
        }
    }

    fun onSetPrimaryImage(imageId: Long) {
        viewModelScope.launch {
            when (val result = repository.setPrimaryProductImage(productId, imageId)) {
                is NetworkResult.Success -> _state.value = _state.value.copy(images = result.data)
                is NetworkResult.Failure -> _state.value = _state.value.copy(errorMessage = result.error.message)
            }
        }
    }

    // ---- Variants -------------------------------------------------------------------------

    /** Appends a blank variant and returns its temp key, so the caller can open it for editing. */
    fun onAddVariant(): Long {
        val key = nextTempVariantKey--
        val blank = VariantUiModel(localKey = key, variantId = null)
        updateState { it.copy(variants = it.variants + blank) }
        return key
    }

    fun onVariantChanged(localKey: Long, transform: (VariantUiModel) -> VariantUiModel) {
        updateState { state ->
            state.copy(variants = state.variants.map { if (it.localKey == localKey) transform(it) else it })
        }
    }

    fun onDeleteVariant(localKey: Long) {
        updateState { state ->
            val target = state.variants.firstOrNull { it.localKey == localKey } ?: return@updateState state
            val deletedIds = target.variantId?.let { state.deletedVariantIds + it } ?: state.deletedVariantIds
            state.copy(
                variants = state.variants.filterNot { it.localKey == localKey },
                deletedVariantIds = deletedIds,
            )
        }
    }

    // ---- Save -----------------------------------------------------------------------------

    fun save() {
        val base = original ?: return
        val current = _state.value
        if (current.isSaving) return

        val name = current.name.trim()
        val sku = current.sku.trim()
        val upc = current.upc.trim()
        if (name.isEmpty() || sku.isEmpty() || upc.isEmpty()) {
            _state.value = current.copy(errorMessage = "Name, SKU, and UPC are all required")
            return
        }

        val updated = base.mergeFrom(current, name, sku, upc)
        val variantOps = current.variants.map { it.toOp() } +
            current.deletedVariantIds.map { VariantOp(kind = VariantOp.Kind.DELETE, variantId = it) }

        _state.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.saveProduct(updated, variantOps)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isSaving = false, saved = true)
                    load()
                }
                is NetworkResult.Failure ->
                    _state.value = _state.value.copy(isSaving = false, errorMessage = result.error.message)
            }
        }
    }

    fun dismissSavedFlag() {
        _state.value = _state.value.copy(saved = false)
    }
}

private fun formatDollars(cents: Long): String =
    BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

private fun parseDollarsToCents(text: String): Long? {
    val value = text.trim().toDoubleOrNull() ?: return null
    return BigDecimal(value.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

private fun BackOfficeProduct.toUiState(activeTab: EditorTab): BackOfficeProductEditorUiState =
    BackOfficeProductEditorUiState(
        isLoading = false,
        activeTab = activeTab,
        name = name,
        sku = sku,
        upc = upc,
        categoryName = categoryName,
        quantityText = quantity.toString(),
        isActive = isActive,
        publishToMobilePos = publishToMobilePos,
        shortDesc = shortDesc.orEmpty(),
        longDesc = longDesc.orEmpty(),
        vendor = vendor.orEmpty(),
        vendorSku = vendorSku.orEmpty(),
        url = url.orEmpty(),
        binLocation = binLocation.orEmpty(),
        productType = productType,
        unitOfSale = unitOfSale,
        pricePerUnitText = pricePerUnitCents?.let { formatDollars(it) }.orEmpty(),
        tracksStock = tracksStock,
        reorderPointText = reorderPoint.toString(),
        orderQuantityText = orderQuantity.toString(),
        priceText = formatDollars(priceCents),
        costText = costCents?.let { formatDollars(it) }.orEmpty(),
        salePriceText = formatDollars(salePriceCents),
        images = images,
        variants = variants.map { it.toUiModel() },
    )

/** Merges the editable-tab fields from [state] onto this fetched record for a save.php call. */
private fun BackOfficeProduct.mergeFrom(
    state: BackOfficeProductEditorUiState,
    name: String,
    sku: String,
    upc: String,
): BackOfficeProduct = copy(
    name = name,
    sku = sku,
    upc = upc,
    shortDesc = state.shortDesc,
    longDesc = state.longDesc,
    vendor = state.vendor,
    vendorSku = state.vendorSku,
    url = state.url,
    binLocation = state.binLocation,
    productType = state.productType,
    unitOfSale = state.unitOfSale,
    pricePerUnitCents = state.pricePerUnitText.takeIf { it.isNotBlank() }?.let { parseDollarsToCents(it) },
    quantity = state.quantityText.toIntOrNull() ?: quantity,
    reorderPoint = state.reorderPointText.toIntOrNull() ?: reorderPoint,
    orderQuantity = state.orderQuantityText.toIntOrNull() ?: orderQuantity,
    tracksStock = state.tracksStock,
    priceCents = parseDollarsToCents(state.priceText) ?: priceCents,
    costCents = state.costText.takeIf { it.isNotBlank() }?.let { parseDollarsToCents(it) },
    salePriceCents = parseDollarsToCents(state.salePriceText) ?: 0L,
    isActive = state.isActive,
    publishToMobilePos = state.publishToMobilePos,
)

private fun BackOfficeProductVariant.toUiModel(): VariantUiModel = VariantUiModel(
    localKey = variantId,
    variantId = variantId,
    variantName = variantName,
    variantValue = variantValue,
    sku = sku,
    upc = upc,
    priceText = priceCents?.let { formatDollars(it) }.orEmpty(),
    costText = costCents?.let { formatDollars(it) }.orEmpty(),
    quantityText = quantity.toString(),
    isActive = isActive,
)

private fun VariantUiModel.toOp(): VariantOp = VariantOp(
    kind = if (variantId == null) VariantOp.Kind.CREATE else VariantOp.Kind.UPDATE,
    variantId = variantId,
    variantName = variantName,
    variantValue = variantValue,
    sku = sku.takeIf { it.isNotBlank() },
    upc = upc.takeIf { it.isNotBlank() },
    priceCents = priceText.takeIf { it.isNotBlank() }?.let { parseDollarsToCents(it) },
    costCents = costText.takeIf { it.isNotBlank() }?.let { parseDollarsToCents(it) },
    quantity = quantityText.toIntOrNull() ?: 0,
    isActive = isActive,
)

data class BackOfficeProductEditorUiState(
    val isLoading: Boolean = true,
    val activeTab: EditorTab = EditorTab.BASICS,
    // Basics
    val name: String = "",
    val sku: String = "",
    val upc: String = "",
    val categoryName: String? = null,
    val quantityText: String = "0",
    val isActive: Boolean = true,
    val publishToMobilePos: Boolean = true,
    // Details
    val shortDesc: String = "",
    val longDesc: String = "",
    val vendor: String = "",
    val vendorSku: String = "",
    val url: String = "",
    val binLocation: String = "",
    val productType: String = "physical",
    val unitOfSale: String = "piece",
    val pricePerUnitText: String = "",
    val tracksStock: Boolean = true,
    val reorderPointText: String = "0",
    val orderQuantityText: String = "1",
    // Pricing
    val priceText: String = "",
    val costText: String = "",
    val salePriceText: String = "",
    // Images
    val images: List<ProductImage> = emptyList(),
    val isUploadingImage: Boolean = false,
    // Variants
    val variants: List<VariantUiModel> = emptyList(),
    val deletedVariantIds: Set<Long> = emptySet(),
    // Shared
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

data class VariantUiModel(
    val localKey: Long,
    val variantId: Long?,
    val variantName: String = "",
    val variantValue: String = "",
    val sku: String = "",
    val upc: String = "",
    val priceText: String = "",
    val costText: String = "",
    val quantityText: String = "0",
    val isActive: Boolean = true,
)

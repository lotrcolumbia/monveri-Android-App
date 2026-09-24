package co.monveri.register.data.repository

import co.monveri.register.network.NetworkResult

/**
 * Admin product editing — the same `manage_products`-gated endpoints iOS's Back Office uses.
 * Mirrors iOS's full multi-tab editor: Basics/Details/Pricing/Variants round-trip through
 * [getProduct]/[saveProduct] (one full-row-overwrite call), while images are their own
 * immediate-effect calls independent of the product's Save button — same split iOS uses.
 */
interface BackOfficeRepository {
    suspend fun getProduct(id: Long): NetworkResult<BackOfficeProduct>
    suspend fun saveProduct(product: BackOfficeProduct, variantOps: List<VariantOp>): NetworkResult<Long>
    suspend fun uploadProductImage(productId: Long, jpegBytes: ByteArray): NetworkResult<ProductImage>
    suspend fun deleteProductImage(productId: Long, imageId: Long): NetworkResult<List<ProductImage>>
    suspend fun setPrimaryProductImage(productId: Long, imageId: Long): NetworkResult<List<ProductImage>>

    /** Full URL for a product image's static filename — unauthenticated, served directly. */
    fun productImageUrl(filename: String): String
}

/**
 * Full admin product record — carries every field `save.php` writes, not just the ones the
 * editor UI exposes. `save.php` overwrites the whole row from the request body, so fields the UI
 * doesn't show still have to round-trip through here unchanged or they'd get silently blanked.
 */
data class BackOfficeProduct(
    val id: Long,
    val name: String,
    val sku: String,
    val upc: String,
    val vendorSku: String?,
    val categoryId: String?,
    val categoryName: String?,
    val shortDesc: String?,
    val longDesc: String?,
    val vendor: String?,
    val url: String?,
    val binLocation: String?,
    val productType: String,
    val unitOfSale: String,
    val pricePerUnitCents: Long?,
    val quantity: Int,
    val reorderPoint: Int,
    val orderQuantity: Int,
    val tracksStock: Boolean,
    val priceCents: Long,
    val costCents: Long?,
    val salePriceCents: Long,
    val isActive: Boolean,
    val publishToMobilePos: Boolean,
    val images: List<ProductImage> = emptyList(),
    val variants: List<BackOfficeProductVariant> = emptyList(),
)

data class ProductImage(
    val imageId: Long,
    val filename: String,
    val sortOrder: Int,
    val isPrimary: Boolean,
)

/** A variant as the admin editor sees it — distinct from the cashier catalog's [ProductVariant]. */
data class BackOfficeProductVariant(
    val variantId: Long,
    val variantName: String,
    val variantValue: String,
    val sku: String,
    val upc: String,
    val priceCents: Long?,
    val costCents: Long?,
    val quantity: Int,
    val isActive: Boolean,
)

/**
 * A staged change to submit with the next [BackOfficeRepository.saveProduct] call — variants are
 * batched with the parent product save (one atomic `save.php` call), unlike images which take
 * effect immediately. `variantId` is required for [Kind.UPDATE]/[Kind.DELETE], ignored for
 * [Kind.CREATE] (the server assigns one).
 */
data class VariantOp(
    val kind: Kind,
    val variantId: Long? = null,
    val variantName: String? = null,
    val variantValue: String? = null,
    val sku: String? = null,
    val upc: String? = null,
    val priceCents: Long? = null,
    val costCents: Long? = null,
    val quantity: Int? = null,
    val isActive: Boolean? = null,
) {
    enum class Kind { CREATE, UPDATE, DELETE }
}

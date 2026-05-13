package co.monveri.register.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape of `GET /products/sync.php`. Holds the three lists the backend returns
 * (`products`, `variants`, `barcode_relationships`). Future incremental sync will add a `since`
 * cursor — the field is already accepted server-side; Phase 3 fetches the full snapshot.
 */
@Serializable
data class CatalogSyncDto(
    @SerialName("products") val products: List<ProductDto> = emptyList(),
    @SerialName("variants") val variants: List<ProductVariantDto> = emptyList(),
    @SerialName("barcode_relationships") val barcodeRelationships: List<BarcodeRelationshipDto> = emptyList(),
)

/**
 * Response shape of `GET /products/search.php`. Backend already debounces and caps at 100; the
 * client clamps requested `limit` to that range.
 */
@Serializable
data class ProductSearchDto(
    @SerialName("products") val products: List<ProductDto> = emptyList(),
    @SerialName("q") val query: String = "",
    @SerialName("limit") val limit: Int = 0,
    @SerialName("count") val count: Int = 0,
)

/**
 * Common product projection used by sync, search, and barcode endpoints. `price` arrives as a
 * float (PHP `floatval`); we normalize to cents at the repository boundary via [centsOf].
 *
 * `is_taxable` is only populated by `sync.php` (it's always 1 today); other endpoints omit it
 * and we default to taxable to match the backend.
 */
@Serializable
data class ProductDto(
    @SerialName("product_id") val productId: Long,
    @SerialName("sku") val sku: String? = null,
    @SerialName("name") val name: String,
    @SerialName("price") val price: Double = 0.0,
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("upc") val upc: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("unit_of_sale") val unitOfSale: String = "piece",
    @SerialName("price_per_unit") val pricePerUnit: Double? = null,
    @SerialName("subtract") val subtract: Int = 1,
    @SerialName("is_taxable") val isTaxable: Int = 1,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ProductVariantDto(
    @SerialName("variant_id") val variantId: Long,
    @SerialName("product_id") val productId: Long,
    @SerialName("sku") val sku: String? = null,
    @SerialName("upc") val upc: String? = null,
    @SerialName("variant_name") val variantName: String? = null,
    @SerialName("variant_value") val variantValue: String? = null,
    @SerialName("price") val price: Double = 0.0,
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("parent_name") val parentName: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
)

@Serializable
data class BarcodeRelationshipDto(
    @SerialName("scanned_barcode") val scannedBarcode: String,
    @SerialName("parent_sku") val parentSku: String,
    @SerialName("qty_count") val qtyCount: Int = 1,
)

@Serializable
data class CategoryDto(
    @SerialName("category_id") val categoryId: String,
    @SerialName("name") val name: String,
    @SerialName("parent_id") val parentId: String? = null,
)

/** Response shape of `GET /products/barcode.php` — also used by the cart's scan-to-add flow. */
@Serializable
data class BarcodeMatchDto(
    @SerialName("match_type") val matchType: String,
    @SerialName("product") val product: ProductDto,
    @SerialName("variant") val variant: ProductVariantDto? = null,
    @SerialName("qty_count") val qtyCount: Int = 1,
)

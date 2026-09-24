package co.monveri.register.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape of `GET /products/get.php` — the full admin-editable product record. Carries
 * every field `save.php` accepts because that endpoint does a full-row `UPDATE` (it overwrites
 * every column from the request body, not just the ones a client chooses to send) — so a caller
 * must round-trip every field it doesn't expose in its own UI or risk silently blanking them.
 * `images`/`variants` are intentionally not modeled here — out of scope for this editor slice.
 */
@Serializable
data class BackOfficeProductDto(
    @SerialName("product_id") val productId: Long,
    @SerialName("sku") val sku: String? = null,
    @SerialName("upc") val upc: String? = null,
    @SerialName("vendor_sku") val vendorSku: String? = null,
    @SerialName("name") val name: String,
    @SerialName("short_desc") val shortDesc: String? = null,
    @SerialName("long_desc") val longDesc: String? = null,
    @SerialName("vendor") val vendor: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("bin_location") val binLocation: String? = null,
    @SerialName("product_type") val productType: String = "physical",
    @SerialName("unit_of_sale") val unitOfSale: String = "piece",
    @SerialName("price_per_unit") val pricePerUnit: Double? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("reorder_point") val reorderPoint: Int = 0,
    @SerialName("order_quantity") val orderQuantity: Int = 1,
    @SerialName("subtract") val subtract: Int = 1,
    @SerialName("price") val price: Double = 0.0,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("saleprice") val salePrice: Double = 0.0,
    @SerialName("status") val status: Int = 1,
    @SerialName("publish_to_mobile_pos") val publishToMobilePos: Int = 1,
    @SerialName("images") val images: List<ProductImageDto> = emptyList(),
    @SerialName("variants") val variants: List<BackOfficeProductVariantDto> = emptyList(),
)

/**
 * A single row from `product_images` (or a synthesized one from the legacy `product.image`
 * column when the gallery is empty — see `products/get.php`'s doc). `imageId == 0` marks that
 * synthesized case: not a real row yet, so it can't be targeted by delete/set-primary ops.
 */
@Serializable
data class ProductImageDto(
    @SerialName("image_id") val imageId: Long,
    @SerialName("filename") val filename: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_primary") val isPrimary: Int = 0,
)

/** A row from `product_variant`, as returned to the Back Office editor (distinct shape from the
 * cashier-facing catalog's [ProductVariantDto] — this one carries admin fields like `cost` and
 * `status` that the checkout-time variant lookup doesn't need). */
@Serializable
data class BackOfficeProductVariantDto(
    @SerialName("variant_id") val variantId: Long,
    @SerialName("variant_name") val variantName: String,
    @SerialName("variant_value") val variantValue: String,
    @SerialName("sku") val sku: String = "",
    @SerialName("upc") val upc: String = "",
    @SerialName("price") val price: Double? = null,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("status") val status: Int = 1,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/**
 * One entry in `save.php`'s `variants` array — `op` is `create`/`update`/`delete`. `variantId` is
 * required for `update`/`delete`, omitted for `create` (the server assigns one).
 */
@Serializable
data class VariantOpDto(
    @SerialName("op") val op: String,
    @SerialName("variant_id") val variantId: Long? = null,
    @SerialName("variant_name") val variantName: String? = null,
    @SerialName("variant_value") val variantValue: String? = null,
    @SerialName("sku") val sku: String? = null,
    @SerialName("upc") val upc: String? = null,
    @SerialName("price") val price: Double? = null,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("quantity") val quantity: Int? = null,
    @SerialName("status") val status: Int? = null,
)

/** Body of `POST /products/save.php` — see [BackOfficeProductDto]'s doc on full-row overwrite. */
@Serializable
data class SaveProductRequest(
    @SerialName("product_id") val productId: Long,
    @SerialName("name") val name: String,
    @SerialName("sku") val sku: String,
    @SerialName("upc") val upc: String,
    @SerialName("vendor_sku") val vendorSku: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("short_desc") val shortDesc: String? = null,
    @SerialName("long_desc") val longDesc: String? = null,
    @SerialName("vendor") val vendor: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("bin_location") val binLocation: String? = null,
    @SerialName("product_type") val productType: String = "physical",
    @SerialName("unit_of_sale") val unitOfSale: String = "piece",
    @SerialName("price_per_unit") val pricePerUnit: Double? = null,
    @SerialName("quantity") val quantity: Int,
    @SerialName("reorder_point") val reorderPoint: Int = 0,
    @SerialName("order_quantity") val orderQuantity: Int = 1,
    @SerialName("subtract") val subtract: Int = 1,
    @SerialName("price") val price: Double,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("saleprice") val saleprice: Double = 0.0,
    @SerialName("status") val status: Int = 1,
    @SerialName("publish_to_mobile_pos") val publishToMobilePos: Int = 1,
    @SerialName("variants") val variants: List<VariantOpDto> = emptyList(),
)

@Serializable
data class SaveProductResponseDto(
    @SerialName("product_id") val productId: Long,
    @SerialName("message") val message: String? = null,
)

/** Body of `POST /products/image-upload.php`. */
@Serializable
data class UploadProductImageRequest(
    @SerialName("product_id") val productId: Long,
    @SerialName("image_base64") val imageBase64: String,
)

/** Body of `POST /products/image-manage.php` — `id` for delete/primary, `order` for reorder. */
@Serializable
data class ImageOpDto(
    @SerialName("op") val op: String,
    @SerialName("id") val id: Long? = null,
    @SerialName("order") val order: List<Long>? = null,
)

@Serializable
data class ManageProductImagesRequest(
    @SerialName("product_id") val productId: Long,
    @SerialName("ops") val ops: List<ImageOpDto>,
)

/** `image-manage.php` always returns the full canonical gallery, not just the changed row. */
@Serializable
data class ManageProductImagesResponseDto(
    @SerialName("product_id") val productId: Long,
    @SerialName("images") val images: List<ProductImageDto> = emptyList(),
)

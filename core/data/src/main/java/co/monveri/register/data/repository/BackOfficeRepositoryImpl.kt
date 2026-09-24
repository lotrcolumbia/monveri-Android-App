package co.monveri.register.data.repository

import co.monveri.register.network.BaseUrlProvider
import co.monveri.register.network.MonveriApi
import co.monveri.register.network.NetworkError
import co.monveri.register.network.NetworkErrorMapper
import co.monveri.register.network.NetworkResult
import co.monveri.register.network.dto.BackOfficeProductDto
import co.monveri.register.network.dto.BackOfficeProductVariantDto
import co.monveri.register.network.dto.ImageOpDto
import co.monveri.register.network.dto.ManageProductImagesRequest
import co.monveri.register.network.dto.ProductImageDto
import co.monveri.register.network.dto.SaveProductRequest
import co.monveri.register.network.dto.UploadProductImageRequest
import co.monveri.register.network.dto.VariantOpDto
import co.monveri.register.network.runCatchingNetwork
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackOfficeRepositoryImpl @Inject constructor(
    private val api: MonveriApi,
    private val errorMapper: NetworkErrorMapper,
    private val baseUrlProvider: BaseUrlProvider,
) : BackOfficeRepository {

    override suspend fun getProduct(id: Long): NetworkResult<BackOfficeProduct> {
        val result = runCatchingNetwork(errorMapper) { api.getBackOfficeProduct(id) }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(
                        NetworkError.Server(MAX_HTTP_CODE, envelope.message ?: "Could not load product"),
                    )
                } else {
                    NetworkResult.Success(payload.toDomain())
                }
            }
        }
    }

    override suspend fun saveProduct(product: BackOfficeProduct, variantOps: List<VariantOp>): NetworkResult<Long> {
        val result = runCatchingNetwork(errorMapper) {
            api.saveBackOfficeProduct(product.toRequest(variantOps))
        }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(
                        NetworkError.Server(MAX_HTTP_CODE, envelope.message ?: "Could not save product"),
                    )
                } else {
                    NetworkResult.Success(payload.productId)
                }
            }
        }
    }

    override suspend fun uploadProductImage(productId: Long, jpegBytes: ByteArray): NetworkResult<ProductImage> {
        val request = UploadProductImageRequest(
            productId = productId,
            imageBase64 = Base64.getEncoder().encodeToString(jpegBytes),
        )
        val result = runCatchingNetwork(errorMapper) { api.uploadProductImage(request) }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(
                        NetworkError.Server(MAX_HTTP_CODE, envelope.message ?: "Could not upload image"),
                    )
                } else {
                    NetworkResult.Success(payload.toDomain())
                }
            }
        }
    }

    override suspend fun deleteProductImage(productId: Long, imageId: Long): NetworkResult<List<ProductImage>> =
        manageImages(productId, ImageOpDto(op = "delete", id = imageId))

    override suspend fun setPrimaryProductImage(productId: Long, imageId: Long): NetworkResult<List<ProductImage>> =
        manageImages(productId, ImageOpDto(op = "primary", id = imageId))

    private suspend fun manageImages(productId: Long, op: ImageOpDto): NetworkResult<List<ProductImage>> {
        val request = ManageProductImagesRequest(productId = productId, ops = listOf(op))
        val result = runCatchingNetwork(errorMapper) { api.manageProductImages(request) }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(
                        NetworkError.Server(MAX_HTTP_CODE, envelope.message ?: "Could not update images"),
                    )
                } else {
                    NetworkResult.Success(payload.images.map { it.toDomain() })
                }
            }
        }
    }

    override fun productImageUrl(filename: String): String {
        val storeRoot = baseUrlProvider.baseUrl().removeSuffix(API_REGISTER_SUFFIX)
        return "$storeRoot$PRODUCT_IMAGES_PATH$filename"
    }

    private companion object {
        const val MAX_HTTP_CODE: Int = 500
        const val API_REGISTER_SUFFIX: String = "api/register/"
        const val PRODUCT_IMAGES_PATH: String = "images/products/"
    }
}

private fun BackOfficeProductDto.toDomain(): BackOfficeProduct = BackOfficeProduct(
    id = productId,
    name = name,
    sku = sku.orEmpty(),
    upc = upc.orEmpty(),
    vendorSku = vendorSku,
    categoryId = categoryId,
    categoryName = categoryName,
    shortDesc = shortDesc,
    longDesc = longDesc,
    vendor = vendor,
    url = url,
    binLocation = binLocation,
    productType = productType,
    unitOfSale = unitOfSale,
    pricePerUnitCents = pricePerUnit?.let { toCents(it) },
    quantity = quantity,
    reorderPoint = reorderPoint,
    orderQuantity = orderQuantity,
    tracksStock = subtract != 0,
    priceCents = toCents(price),
    costCents = cost?.let { toCents(it) },
    salePriceCents = toCents(salePrice),
    isActive = status != 0,
    publishToMobilePos = publishToMobilePos != 0,
    images = images.map { it.toDomain() },
    variants = variants.map { it.toDomain() },
)

private fun BackOfficeProduct.toRequest(variantOps: List<VariantOp>): SaveProductRequest = SaveProductRequest(
    productId = id,
    name = name,
    sku = sku,
    upc = upc,
    vendorSku = vendorSku,
    categoryId = categoryId,
    shortDesc = shortDesc,
    longDesc = longDesc,
    vendor = vendor,
    url = url,
    binLocation = binLocation,
    productType = productType,
    unitOfSale = unitOfSale,
    pricePerUnit = pricePerUnitCents?.let { toDollars(it) },
    quantity = quantity,
    reorderPoint = reorderPoint,
    orderQuantity = orderQuantity,
    subtract = if (tracksStock) 1 else 0,
    price = toDollars(priceCents),
    cost = costCents?.let { toDollars(it) },
    saleprice = toDollars(salePriceCents),
    status = if (isActive) 1 else 0,
    publishToMobilePos = if (publishToMobilePos) 1 else 0,
    variants = variantOps.map { it.toDto() },
)

private fun ProductImageDto.toDomain(): ProductImage = ProductImage(
    imageId = imageId,
    filename = filename,
    sortOrder = sortOrder,
    isPrimary = isPrimary != 0,
)

private fun BackOfficeProductVariantDto.toDomain(): BackOfficeProductVariant = BackOfficeProductVariant(
    variantId = variantId,
    variantName = variantName,
    variantValue = variantValue,
    sku = sku,
    upc = upc,
    priceCents = price?.let { toCents(it) },
    costCents = cost?.let { toCents(it) },
    quantity = quantity,
    isActive = status != 0,
)

private fun VariantOp.toDto(): VariantOpDto = VariantOpDto(
    op = kind.name.lowercase(),
    variantId = variantId,
    variantName = variantName,
    variantValue = variantValue,
    sku = sku,
    upc = upc,
    price = priceCents?.let { toDollars(it) },
    cost = costCents?.let { toDollars(it) },
    quantity = quantity,
    status = isActive?.let { if (it) 1 else 0 },
)

/** Mirrors `CatalogRepositoryImpl.toCents` — half-up rounding via a string-seeded BigDecimal. */
private fun toCents(dollars: Double): Long =
    BigDecimal(dollars.toString())
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()

private fun toDollars(cents: Long): Double =
    BigDecimal(cents).movePointLeft(2).toDouble()

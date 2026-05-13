package co.monveri.register.data.repository

import co.monveri.register.network.MonveriApi
import co.monveri.register.network.NetworkError
import co.monveri.register.network.NetworkErrorMapper
import co.monveri.register.network.NetworkResult
import co.monveri.register.network.dto.CategoryDto
import co.monveri.register.network.dto.ProductDto
import co.monveri.register.network.dto.ProductVariantDto
import co.monveri.register.network.map
import co.monveri.register.network.runCatchingNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backed by Retrofit + an in-memory [MutableStateFlow]. Mirrors the iOS sync-once flow: `sync()`
 * grabs the full catalog + categories in parallel, normalizes prices to cents at this single
 * boundary, and pushes the merged snapshot onto the StateFlow. ViewModels collect via
 * [observeCatalog] and re-render automatically.
 *
 * Why we keep `categories` and `products` in one snapshot: filter chips read from `categories`
 * the same instant the grid reads from `products`. Holding them in two separate flows would let
 * the UI render a category that no longer exists in this catalog.
 */
@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val api: MonveriApi,
    private val errorMapper: NetworkErrorMapper,
) : CatalogRepository {

    private val state = MutableStateFlow(CatalogSnapshot())

    override fun observeCatalog(): Flow<CatalogSnapshot> = state.asStateFlow()

    override fun current(): CatalogSnapshot = state.value

    override suspend fun sync(): NetworkResult<CatalogSnapshot> {
        val syncResult = runCatchingNetwork(errorMapper) { api.catalogSync() }
        val categoryResult = runCatchingNetwork(errorMapper) { api.categories() }

        return when {
            syncResult is NetworkResult.Failure -> syncResult
            categoryResult is NetworkResult.Failure -> categoryResult
            else -> {
                val syncBody = (syncResult as NetworkResult.Success).data
                val categoryBody = (categoryResult as NetworkResult.Success).data
                if (!syncBody.success || syncBody.data == null) {
                    NetworkResult.Failure(NetworkError.Server(MAX_HTTP_CODE, syncBody.message ?: "Catalog sync failed"))
                } else if (!categoryBody.success || categoryBody.data == null) {
                    NetworkResult.Failure(
                        NetworkError.Server(MAX_HTTP_CODE, categoryBody.message ?: "Category sync failed"),
                    )
                } else {
                    val snapshot = CatalogSnapshot(
                        products = syncBody.data.products.map { it.toDomain() },
                        categories = categoryBody.data.map { it.toDomain() },
                        variantsByProductId = syncBody.data.variants
                            .map { it.toDomain() }
                            .groupBy { it.productId },
                        lastSyncEpochMillis = System.currentTimeMillis(),
                    )
                    state.value = snapshot
                    NetworkResult.Success(snapshot)
                }
            }
        }
    }

    override suspend fun search(query: String, limit: Int): NetworkResult<List<Product>> {
        val safeLimit = limit.coerceIn(1, MAX_SEARCH_LIMIT)
        return runCatchingNetwork(errorMapper) {
            api.searchProducts(query = query, limit = safeLimit)
        }.map { envelope ->
            envelope.data?.products.orEmpty().map { it.toDomain() }
        }
    }

    override suspend fun lookupBarcode(code: String): NetworkResult<BarcodeMatch?> {
        val result = runCatchingNetwork(errorMapper) { api.barcodeLookup(code) }
        return when (result) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Success(null)
                } else {
                    NetworkResult.Success(
                        BarcodeMatch(
                            product = payload.product.toDomain(),
                            variant = payload.variant?.toDomain(),
                            qtyCount = payload.qtyCount.coerceAtLeast(1),
                        ),
                    )
                }
            }
            // A 404 from `barcode.php` means "no match" — surface as null, not an error toast.
            is NetworkResult.Failure -> when (result.error) {
                is NetworkError.NotFound -> NetworkResult.Success(null)
                else -> result
            }
        }
    }

    private companion object {
        const val MAX_SEARCH_LIMIT: Int = 100
        const val MAX_HTTP_CODE: Int = 500
    }
}

private fun ProductDto.toDomain(): Product = Product(
    id = productId,
    sku = sku,
    name = name,
    priceCents = toCents(price),
    stockQuantity = quantity,
    upc = upc,
    categoryId = categoryId,
    categoryName = categoryName,
    unitOfSale = unitOfSale,
    pricePerUnitCents = pricePerUnit?.let { toCents(it) },
    tracksStock = subtract != 0,
    isTaxable = isTaxable != 0,
)

private fun ProductVariantDto.toDomain(): ProductVariant = ProductVariant(
    id = variantId,
    productId = productId,
    sku = sku,
    upc = upc,
    name = variantName,
    value = variantValue,
    priceCents = toCents(price),
    stockQuantity = quantity,
)

private fun CategoryDto.toDomain(): Category = Category(
    id = categoryId,
    name = name,
    parentId = parentId.takeUnless { it.isNullOrBlank() || it == "0" },
)

/**
 * Single float-to-cents conversion site so rounding rules can't drift between callers. We mirror
 * the iOS NumberFormatter behavior (half-up) and the PHP `number_format($n, 2)` writes.
 *
 * The intermediate `BigDecimal(price.toString())` avoids the IEEE-754 silliness where `19.99.toBigDecimal()`
 * resolves to `19.989999...`.
 */
private fun toCents(price: Double): Long =
    BigDecimal(price.toString())
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()

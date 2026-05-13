package co.monveri.register.data.repository

import co.monveri.register.network.NetworkResult
import kotlinx.coroutines.flow.Flow

/**
 * Catalog read surface. Phase 3 holds the snapshot in memory (mirrors iOS v1); Phase 9 will
 * back it with Room + an incremental `since=` cursor.
 *
 * Conventions:
 *  - All currency lives in integer cents — domain models never carry float dollars.
 *  - `categoryId` is a `String?` because the backend column is varchar (legacy schema).
 *  - `sync()` is the canonical refresh path; the in-memory store ages out only on app death.
 */
interface CatalogRepository {

    /** Reactive view of the locally-cached catalog. Empty until [sync] succeeds at least once. */
    fun observeCatalog(): Flow<CatalogSnapshot>

    /** Snapshot accessor for non-Flow consumers (e.g., scanner → quick lookup by id). */
    fun current(): CatalogSnapshot

    /** Force-fetch the full catalog from the backend and replace the snapshot on success. */
    suspend fun sync(): NetworkResult<CatalogSnapshot>

    /** Server-side keyword search — independent of the cached snapshot. */
    suspend fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): NetworkResult<List<Product>>

    /** Resolve a scanned barcode against the backend. Returns null when the backend 404s. */
    suspend fun lookupBarcode(code: String): NetworkResult<BarcodeMatch?>

    companion object {
        const val DEFAULT_SEARCH_LIMIT: Int = 25
    }
}

/** Immutable view of the locally-cached catalog at a point in time. */
data class CatalogSnapshot(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val variantsByProductId: Map<Long, List<ProductVariant>> = emptyMap(),
    val lastSyncEpochMillis: Long? = null,
) {
    fun findProduct(id: Long): Product? = products.firstOrNull { it.id == id }
    fun variantsOf(productId: Long): List<ProductVariant> = variantsByProductId[productId].orEmpty()
}

/** Domain model for a register-facing product. Currency in cents; stock as integer count. */
data class Product(
    val id: Long,
    val sku: String?,
    val name: String,
    val priceCents: Long,
    val stockQuantity: Int,
    val upc: String?,
    val categoryId: String?,
    val categoryName: String?,
    val unitOfSale: String,
    val pricePerUnitCents: Long?,
    val tracksStock: Boolean,
    val isTaxable: Boolean,
)

data class ProductVariant(
    val id: Long,
    val productId: Long,
    val sku: String?,
    val upc: String?,
    val name: String?,
    val value: String?,
    val priceCents: Long,
    val stockQuantity: Int,
) {
    val displayLabel: String
        get() = listOfNotNull(name, value)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { sku.orEmpty() }
}

data class Category(
    val id: String,
    val name: String,
    val parentId: String?,
)

/** Result of a barcode scan. `qtyCount` lets multi-pack barcodes auto-multiply the add-to-cart. */
data class BarcodeMatch(
    val product: Product,
    val variant: ProductVariant?,
    val qtyCount: Int,
)

package co.monveri.register.data.repository

import co.monveri.register.network.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalog access surface. Phase 2 is a stub — every method returns empty/Unauthorized. Phase 3
 * fills these in with the real Retrofit + Room implementation (cached snapshot, search, etc.).
 *
 * Keeping the interface live in Phase 2 means ViewModels and tests can compile against it now.
 */
interface CatalogRepository {
    /** Streams the current catalog as a single flow. Driven by the Room snapshot in Phase 3. */
    fun observeCatalog(): Flow<List<CatalogSummary>>

    /** Forces a sync against the backend. No-op in Phase 2. */
    suspend fun sync(): NetworkResult<Unit>

    /** Lookup by barcode — Phase 3 wires this through `products/barcode.php`. */
    suspend fun findByBarcode(code: String): NetworkResult<CatalogSummary?>
}

/** Lightweight projection for list rendering. Full product detail is a separate fetch in Phase 3. */
data class CatalogSummary(
    val id: Long,
    val name: String,
    val priceCents: Long,
    val sku: String?,
)

@Singleton
class CatalogRepositoryImpl @Inject constructor() : CatalogRepository {
    override fun observeCatalog(): Flow<List<CatalogSummary>> = flowOf(emptyList())
    override suspend fun sync(): NetworkResult<Unit> = NetworkResult.Success(Unit)
    override suspend fun findByBarcode(code: String): NetworkResult<CatalogSummary?> =
        NetworkResult.Success(null)
}

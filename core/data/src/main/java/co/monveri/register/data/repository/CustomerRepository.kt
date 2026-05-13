package co.monveri.register.data.repository

import co.monveri.register.network.NetworkResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Customer + loyalty lookup. Phase 2 stub; Phase 3 wires this against
 * `customers/search.php` + the loyalty AJAX endpoints.
 */
interface CustomerRepository {
    /** Lookup by phone, email, or loyalty card. Returns null when no match. */
    suspend fun lookup(query: String): NetworkResult<CustomerSummary?>
}

data class CustomerSummary(
    val id: Long,
    val name: String,
    val phone: String?,
    val email: String?,
    val loyaltyCard: String?,
    val pointsBalance: Long,
)

@Singleton
class CustomerRepositoryImpl @Inject constructor() : CustomerRepository {
    override suspend fun lookup(query: String): NetworkResult<CustomerSummary?> =
        NetworkResult.Success(null)
}

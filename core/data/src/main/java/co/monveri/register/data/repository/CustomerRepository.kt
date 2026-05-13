package co.monveri.register.data.repository

import co.monveri.register.network.NetworkResult

/**
 * Customer + loyalty lookup. v1 supports lookup only — back-office handles CRUD.
 */
interface CustomerRepository {

    /** Server-side search across name / phone / email / loyalty card. */
    suspend fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): NetworkResult<List<Customer>>

    companion object {
        const val DEFAULT_SEARCH_LIMIT: Int = 25
    }
}

/**
 * Domain model for a register-facing customer. The display name falls back through the obvious
 * candidates so we always have a string for the cart chip.
 */
data class Customer(
    val id: Long,
    val firstName: String?,
    val lastName: String?,
    val company: String?,
    val phone: String?,
    val email: String?,
    val loyaltyCardNumber: String?,
    val currentPoints: Int,
    val tierName: String?,
) {
    val displayName: String
        get() = listOfNotNull(firstName, lastName)
            .joinToString(" ")
            .ifBlank { company }
            ?.ifBlank { null }
            ?: email
            ?: phone
            ?: "Customer #$id"
}

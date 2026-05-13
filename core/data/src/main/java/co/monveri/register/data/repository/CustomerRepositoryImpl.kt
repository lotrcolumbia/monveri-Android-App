package co.monveri.register.data.repository

import co.monveri.register.network.MonveriApi
import co.monveri.register.network.NetworkErrorMapper
import co.monveri.register.network.NetworkResult
import co.monveri.register.network.dto.CustomerDto
import co.monveri.register.network.map
import co.monveri.register.network.runCatchingNetwork
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit-backed [CustomerRepository]. Returns a flat list of matches — the UI picks the
 * customer to attach. The backend already de-dupes by `customer_id` so the list never contains
 * duplicates.
 */
@Singleton
class CustomerRepositoryImpl @Inject constructor(
    private val api: MonveriApi,
    private val errorMapper: NetworkErrorMapper,
) : CustomerRepository {

    override suspend fun search(query: String, limit: Int): NetworkResult<List<Customer>> {
        if (query.isBlank()) return NetworkResult.Success(emptyList())
        val safeLimit = limit.coerceIn(1, MAX_SEARCH_LIMIT)
        return runCatchingNetwork(errorMapper) {
            api.searchCustomers(query = query, limit = safeLimit)
        }.map { envelope ->
            envelope.data?.customers.orEmpty().map { it.toDomain() }
        }
    }

    private companion object {
        const val MAX_SEARCH_LIMIT: Int = 100
    }
}

private fun CustomerDto.toDomain(): Customer = Customer(
    id = customerId,
    firstName = firstName,
    lastName = lastName,
    company = company,
    phone = phone,
    email = email,
    loyaltyCardNumber = loyaltyCardNumber,
    currentPoints = currentPoints,
    tierName = tierName,
)

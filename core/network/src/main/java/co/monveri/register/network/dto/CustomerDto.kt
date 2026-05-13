package co.monveri.register.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape of `GET /customers/search.php`. Matches the iOS `CustomerSearchResponse` so the
 * backend can be evolved once and both clients pick up the change.
 */
@Serializable
data class CustomerSearchDto(
    @SerialName("customers") val customers: List<CustomerDto> = emptyList(),
    @SerialName("q") val query: String = "",
    @SerialName("limit") val limit: Int = 0,
    @SerialName("count") val count: Int = 0,
)

@Serializable
data class CustomerDto(
    @SerialName("customer_id") val customerId: Long,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("company") val company: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("loyalty_card_number") val loyaltyCardNumber: String? = null,
    @SerialName("current_points") val currentPoints: Int = 0,
    @SerialName("tier_name") val tierName: String? = null,
)

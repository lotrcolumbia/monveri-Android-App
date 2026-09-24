package co.monveri.register.payments

import co.monveri.register.network.MonveriApi
import co.monveri.register.network.NetworkError
import co.monveri.register.network.NetworkErrorMapper
import co.monveri.register.network.NetworkResult
import co.monveri.register.network.dto.CancelIntentRequest
import co.monveri.register.network.dto.CaptureIntentRequest
import co.monveri.register.network.dto.CreateIntentRequest
import co.monveri.register.network.runCatchingNetwork
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server-side PaymentIntent lifecycle for Stripe Terminal card-present payments. **Always** create
 * the intent here — never via a local/on-device SDK call — so the ticket can be linked back to its
 * Stripe charge (`metadata.ticket_id`, and `ticket.stripe_payment_intent_id` on submit) and so fee
 * reconciliation (`stripe_fee_sync`) can find it later. Mirrors iOS's `PaymentRepository`.
 */
interface PaymentIntentRepository {
    suspend fun createIntent(
        amountCents: Long,
        idempotencyKey: String,
        ticketId: Long? = null,
        metadata: Map<String, String>? = null,
    ): NetworkResult<PendingIntent>

    suspend fun captureIntent(paymentIntentId: String): NetworkResult<String>
    suspend fun cancelIntent(paymentIntentId: String, reason: String? = "abandoned"): NetworkResult<String>
}

/** A freshly created (or idempotently re-returned) PaymentIntent, ready for the SDK to collect against. */
data class PendingIntent(
    val paymentIntentId: String,
    val clientSecret: String,
    val status: String,
)

@Singleton
class PaymentIntentRepositoryImpl @Inject constructor(
    private val api: MonveriApi,
    private val errorMapper: NetworkErrorMapper,
) : PaymentIntentRepository {

    override suspend fun createIntent(
        amountCents: Long,
        idempotencyKey: String,
        ticketId: Long?,
        metadata: Map<String, String>?,
    ): NetworkResult<PendingIntent> {
        val request = CreateIntentRequest(
            amount = amountCents.toDouble() / CENTS_PER_DOLLAR,
            ticketId = ticketId,
            metadata = metadata,
            idempotencyKey = idempotencyKey,
        )
        val result = runCatchingNetwork(errorMapper) { api.createPaymentIntent(request) }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(serverError(envelope.message, "Could not start card payment"))
                } else {
                    NetworkResult.Success(
                        PendingIntent(
                            paymentIntentId = payload.paymentIntentId,
                            clientSecret = payload.clientSecret,
                            status = payload.status,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun captureIntent(paymentIntentId: String): NetworkResult<String> {
        val result = runCatchingNetwork(errorMapper) {
            api.capturePaymentIntent(CaptureIntentRequest(paymentIntentId))
        }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(serverError(envelope.message, "Could not capture payment"))
                } else {
                    NetworkResult.Success(payload.status)
                }
            }
        }
    }

    override suspend fun cancelIntent(paymentIntentId: String, reason: String?): NetworkResult<String> {
        val result = runCatchingNetwork(errorMapper) {
            api.cancelPaymentIntent(CancelIntentRequest(paymentIntentId, reason))
        }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(serverError(envelope.message, "Could not cancel payment"))
                } else {
                    NetworkResult.Success(payload.status)
                }
            }
        }
    }

    private fun serverError(message: String?, fallback: String) =
        NetworkError.Server(MAX_HTTP_CODE, message ?: fallback)

    private companion object {
        const val MAX_HTTP_CODE: Int = 500
        const val CENTS_PER_DOLLAR: Double = 100.0
    }
}

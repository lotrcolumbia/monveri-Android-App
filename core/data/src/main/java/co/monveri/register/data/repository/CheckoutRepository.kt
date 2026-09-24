package co.monveri.register.data.repository

import co.monveri.register.network.MonveriApi
import co.monveri.register.network.NetworkError
import co.monveri.register.network.NetworkErrorMapper
import co.monveri.register.network.NetworkResult
import co.monveri.register.network.dto.EmailReceiptRequest
import co.monveri.register.network.dto.SplitPaymentLegDto
import co.monveri.register.network.dto.TicketItemDto
import co.monveri.register.network.dto.TicketSubmitRequest
import co.monveri.register.network.runCatchingNetwork
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finalizes a sale — ticket submission and the post-sale receipt (fetch, branding, email).
 * Mirrors iOS's `TicketSubmitter` + the `tickets/` endpoints. Card-payment collection itself
 * lives in `:core:payments`' `PaymentSession` — this repository only records the *result*.
 */
interface CheckoutRepository {
    suspend fun submitTicket(request: TicketSubmission): NetworkResult<SubmittedTicket>
    suspend fun getReceipt(ticketId: Long): NetworkResult<TicketReceipt>
    suspend fun getReceiptBranding(): NetworkResult<ReceiptBranding>
    suspend fun emailReceipt(ticketId: Long, email: String, customerId: Long?): NetworkResult<String>
}

data class TicketSubmission(
    val employeeId: Long,
    val customerId: Long?,
    val payment: String,
    val paymentDetail: String?,
    val subtotalCents: Long,
    val discountCents: Long,
    val discountType: String?,
    val taxCents: Long,
    val taxRateBps: Int,
    val totalCents: Long,
    val tenderedCents: Long?,
    val changeCents: Long?,
    val isSplitPayment: Boolean,
    val sessionId: Long?,
    val idempotencyKey: String,
    val items: List<TicketSubmissionItem>,
    val splitLegs: List<TicketSplitLeg>? = null,
    val paymentIntentId: String? = null,
    val cardBrand: String? = null,
    val cardLastFour: String? = null,
)

data class TicketSubmissionItem(
    val sku: String?,
    val name: String,
    val upc: String?,
    val priceCents: Long,
    val qty: Int,
    val variantId: Long?,
    val unitOfSale: String,
)

data class TicketSplitLeg(val paymentMethod: String, val amountCents: Long, val reference: String?)

data class SubmittedTicket(val id: Long, val token: String)

data class TicketReceipt(
    val id: Long,
    val receiptToken: String?,
    val timestamp: String?,
    val employeeName: String?,
    val customerName: String?,
    val customerEmail: String?,
    val subtotalCents: Long,
    val discountCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val payment: String,
    val paymentDetail: String?,
    val cardBrand: String?,
    val cardLastFour: String?,
    val items: List<TicketReceiptLine>,
)

data class TicketReceiptLine(val name: String, val sku: String?, val priceCents: Long, val qty: Int)

data class ReceiptBranding(
    val storeName: String,
    val addressLine1: String,
    val addressLine2: String,
    val cityStateZip: String,
    val phone: String,
    val headerMessage: String,
    val footerMessage: String,
    val footerSubmessage: String,
)

@Singleton
class CheckoutRepositoryImpl @Inject constructor(
    private val api: MonveriApi,
    private val errorMapper: NetworkErrorMapper,
) : CheckoutRepository {

    override suspend fun submitTicket(request: TicketSubmission): NetworkResult<SubmittedTicket> {
        val body = TicketSubmitRequest(
            employeeId = request.employeeId,
            customerId = request.customerId,
            payment = request.payment,
            paymentDetail = request.paymentDetail,
            subtotal = toDollars(request.subtotalCents),
            discount = toDollars(request.discountCents),
            discountType = request.discountType,
            tax = toDollars(request.taxCents),
            taxRate = request.taxRateBps / BPS_PER_PERCENT,
            total = toDollars(request.totalCents),
            tendered = request.tenderedCents?.let { toDollars(it) },
            changeAmount = request.changeCents?.let { toDollars(it) },
            isSplitPayment = if (request.isSplitPayment) 1 else 0,
            sessionId = request.sessionId,
            idempotencyKey = request.idempotencyKey,
            items = request.items.map {
                TicketItemDto(
                    sku = it.sku,
                    name = it.name,
                    upc = it.upc,
                    price = toDollars(it.priceCents),
                    qty = it.qty,
                    variantId = it.variantId,
                    unitOfSale = it.unitOfSale,
                )
            },
            splitPayments = request.splitLegs?.map {
                SplitPaymentLegDto(it.paymentMethod, toDollars(it.amountCents), it.reference)
            },
            paymentIntentId = request.paymentIntentId,
            cardBrand = request.cardBrand,
            cardLastFour = request.cardLastFour,
        )
        val result = runCatchingNetwork(errorMapper) { api.submitTicket(body) }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(serverError(envelope.message, "Could not record this sale"))
                } else {
                    NetworkResult.Success(SubmittedTicket(payload.id, payload.token))
                }
            }
        }
    }

    override suspend fun getReceipt(ticketId: Long): NetworkResult<TicketReceipt> {
        val result = runCatchingNetwork(errorMapper) { api.getTicketReceipt(ticketId) }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(serverError(envelope.message, "Could not load receipt"))
                } else {
                    val ticket = payload.ticket
                    NetworkResult.Success(
                        TicketReceipt(
                            id = ticket.id,
                            receiptToken = ticket.receiptToken,
                            timestamp = ticket.timestamp,
                            employeeName = ticket.employeeName,
                            customerName = listOfNotNull(ticket.customerFirstName, ticket.customerLastName)
                                .joinToString(" ").ifBlank { null },
                            customerEmail = ticket.customerEmail,
                            subtotalCents = toCents(ticket.subtotal),
                            discountCents = toCents(ticket.discount),
                            taxCents = toCents(ticket.tax),
                            totalCents = toCents(ticket.total),
                            payment = ticket.payment,
                            paymentDetail = ticket.paymentDetail,
                            cardBrand = ticket.cardBrand,
                            cardLastFour = ticket.cardLastFour,
                            items = payload.items.map {
                                TicketReceiptLine(it.name, it.sku, toCents(it.price), it.qty)
                            },
                        ),
                    )
                }
            }
        }
    }

    override suspend fun getReceiptBranding(): NetworkResult<ReceiptBranding> {
        val result = runCatchingNetwork(errorMapper) { api.getReceiptBranding() }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(serverError(envelope.message, "Could not load receipt branding"))
                } else {
                    NetworkResult.Success(
                        ReceiptBranding(
                            storeName = payload.storeName,
                            addressLine1 = payload.addressLine1,
                            addressLine2 = payload.addressLine2,
                            cityStateZip = payload.cityStateZip,
                            phone = payload.phone,
                            headerMessage = payload.headerMessage,
                            footerMessage = payload.footerMessage,
                            footerSubmessage = payload.footerSubmessage,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun emailReceipt(ticketId: Long, email: String, customerId: Long?): NetworkResult<String> {
        val result = runCatchingNetwork(errorMapper) {
            api.emailReceipt(EmailReceiptRequest(ticketId, email, customerId))
        }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val payload = envelope.data
                if (!envelope.success || payload == null) {
                    NetworkResult.Failure(serverError(envelope.message, "Could not email receipt"))
                } else {
                    NetworkResult.Success(payload.email)
                }
            }
        }
    }

    private fun serverError(message: String?, fallback: String) =
        NetworkError.Server(MAX_HTTP_CODE, message ?: fallback)

    private companion object {
        const val MAX_HTTP_CODE: Int = 500
        const val BPS_PER_PERCENT: Double = 100.0
    }
}

private fun toDollars(cents: Long): Double = BigDecimal(cents).movePointLeft(2).toDouble()

private fun toCents(dollars: Double): Long =
    BigDecimal(dollars.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

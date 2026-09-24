package co.monveri.register.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Stripe PaymentIntent (server-created, per `payments/intent-*.php`) --------------------

/** Body of `POST payments/intent-create.php`. `amount` is dollars, matching the backend's `floatval()`. */
@Serializable
data class CreateIntentRequest(
    @SerialName("amount") val amount: Double,
    @SerialName("capture_method") val captureMethod: String = "automatic",
    @SerialName("currency") val currency: String = "usd",
    @SerialName("ticket_id") val ticketId: Long? = null,
    @SerialName("metadata") val metadata: Map<String, String>? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
)

@Serializable
data class PaymentIntentResponseDto(
    @SerialName("payment_intent_id") val paymentIntentId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("status") val status: String,
    @SerialName("amount") val amount: Double = 0.0,
    @SerialName("currency") val currency: String = "usd",
    @SerialName("capture_method") val captureMethod: String = "automatic",
)

@Serializable
data class CaptureIntentRequest(@SerialName("payment_intent_id") val paymentIntentId: String)

@Serializable
data class CancelIntentRequest(
    @SerialName("payment_intent_id") val paymentIntentId: String,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
)

@Serializable
data class IntentStatusResponseDto(
    @SerialName("payment_intent_id") val paymentIntentId: String,
    @SerialName("status") val status: String,
)

// ---- Ticket submission (`transactions/submit.php`) ------------------------------------------

@Serializable
data class TicketItemDto(
    @SerialName("sku") val sku: String? = null,
    @SerialName("name") val name: String,
    @SerialName("upc") val upc: String? = null,
    @SerialName("price") val price: Double,
    @SerialName("qty") val qty: Int,
    @SerialName("variant_id") val variantId: Long? = null,
    @SerialName("unit_of_sale") val unitOfSale: String = "piece",
)

@Serializable
data class SplitPaymentLegDto(
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("amount") val amount: Double,
    @SerialName("reference") val reference: String? = null,
)

/**
 * Body of `POST transactions/submit.php`. All amounts are plain dollar `Double`s — unlike iOS,
 * Kotlin's serializer has no `Decimal`-synthesis bug forcing a string workaround, and the backend
 * `floatval()`s either shape identically.
 */
@Serializable
data class TicketSubmitRequest(
    @SerialName("employee_id") val employeeId: Long,
    @SerialName("customer_id") val customerId: Long? = null,
    @SerialName("type") val type: String = "Sale",
    @SerialName("payment") val payment: String,
    @SerialName("payment_detail") val paymentDetail: String? = null,
    @SerialName("subtotal") val subtotal: Double,
    @SerialName("discount") val discount: Double = 0.0,
    @SerialName("discount_type") val discountType: String? = null,
    @SerialName("tax") val tax: Double,
    @SerialName("tax_rate") val taxRate: Double,
    @SerialName("total") val total: Double,
    @SerialName("tendered") val tendered: Double? = null,
    @SerialName("change_amount") val changeAmount: Double? = null,
    @SerialName("is_split_payment") val isSplitPayment: Int = 0,
    @SerialName("session_id") val sessionId: Long? = null,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("items") val items: List<TicketItemDto>,
    @SerialName("split_payments") val splitPayments: List<SplitPaymentLegDto>? = null,
    @SerialName("payment_intent_id") val paymentIntentId: String? = null,
    @SerialName("card_brand") val cardBrand: String? = null,
    @SerialName("card_last_four") val cardLastFour: String? = null,
)

@Serializable
data class TicketSubmitResponseDto(
    @SerialName("id") val id: Long,
    @SerialName("token") val token: String = "",
)

// ---- Receipt (`tickets/get.php`, `tickets/receipt-branding.php`, `tickets/email-receipt.php`) --

@Serializable
data class TicketReceiptItemDto(
    @SerialName("name") val name: String,
    @SerialName("sku") val sku: String? = null,
    @SerialName("price") val price: Double,
    @SerialName("qty") val qty: Int,
)

@Serializable
data class SplitPaymentLegResponseDto(
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("amount") val amount: Double,
    @SerialName("reference") val reference: String? = null,
)

@Serializable
data class TicketReceiptDto(
    @SerialName("id") val id: Long,
    @SerialName("receipt_token") val receiptToken: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("employee_name") val employeeName: String? = null,
    @SerialName("customer_first_name") val customerFirstName: String? = null,
    @SerialName("customer_last_name") val customerLastName: String? = null,
    @SerialName("customer_email") val customerEmail: String? = null,
    @SerialName("subtotal") val subtotal: Double = 0.0,
    @SerialName("discount") val discount: Double = 0.0,
    @SerialName("discount_type") val discountType: String? = null,
    @SerialName("tax") val tax: Double = 0.0,
    @SerialName("total") val total: Double = 0.0,
    @SerialName("payment") val payment: String = "",
    @SerialName("payment_detail") val paymentDetail: String? = null,
    @SerialName("is_split_payment") val isSplitPayment: Int = 0,
    @SerialName("card_brand") val cardBrand: String? = null,
    @SerialName("card_last_four") val cardLastFour: String? = null,
)

@Serializable
data class TicketDetailResponseDto(
    @SerialName("ticket") val ticket: TicketReceiptDto,
    @SerialName("items") val items: List<TicketReceiptItemDto> = emptyList(),
    @SerialName("split_payments") val splitPayments: List<SplitPaymentLegResponseDto> = emptyList(),
)

@Serializable
data class ReceiptBrandingDto(
    @SerialName("store_name") val storeName: String = "",
    @SerialName("address_line1") val addressLine1: String = "",
    @SerialName("address_line2") val addressLine2: String = "",
    @SerialName("city_state_zip") val cityStateZip: String = "",
    @SerialName("phone") val phone: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("website") val website: String = "",
    @SerialName("header_message") val headerMessage: String = "",
    @SerialName("footer_message") val footerMessage: String = "",
    @SerialName("footer_submessage") val footerSubmessage: String = "",
)

@Serializable
data class EmailReceiptRequest(
    @SerialName("ticket_id") val ticketId: Long,
    @SerialName("email") val email: String,
    @SerialName("customer_id") val customerId: Long? = null,
)

@Serializable
data class EmailReceiptResponseDto(@SerialName("email") val email: String)

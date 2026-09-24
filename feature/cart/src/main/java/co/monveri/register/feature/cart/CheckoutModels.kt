package co.monveri.register.feature.cart

/** Steps of the checkout state machine — mirrors iOS's `CheckoutView.Step` enum. */
enum class CheckoutStep { TENDER_PICKER, CASH, CARD, OTHER, SPLIT, SUBMITTING, SUCCEEDED, FAILED }

/** Third-party tender services for the "Other" picker — matches iOS's `OtherTenderService`. */
enum class OtherTenderService(val label: String) {
    CASH_APP("Cash App"),
    ZELLE("Zelle"),
    PAYPAL("PayPal"),
    APPLE_PAY("Apple Pay"),
    CHECK("Check"),
    OTHER("Other"),
}

/** A finalized tender, ready to submit. Mirrors iOS's `PaymentOutcome`. */
sealed class PaymentOutcome {
    data class Cash(val tenderedCents: Long, val changeCents: Long, val roundedTotalCents: Long) : PaymentOutcome()
    data class Card(val intentId: String, val brand: String?, val lastFour: String?) : PaymentOutcome()
    data class Other(val service: String, val reference: String?) : PaymentOutcome()
    data class Split(val legs: List<SplitLeg>) : PaymentOutcome()

    /** The `ticket.payment` string this outcome writes — see `PaymentDetail.swift`'s enum on iOS. */
    val paymentMethod: String
        get() = when (this) {
            is Cash -> "Cash"
            is Card -> "Credit Card"
            is Other -> "Other"
            is Split -> "Split"
        }

    /** The `payment_detail` string — a human-readable summary shown on the receipt/back office. */
    val paymentDetail: String?
        get() = when (this) {
            is Cash -> null
            is Card -> listOfNotNull(intentId, brand, lastFour?.let { "****$it" }).joinToString(" · ")
            is Other -> listOfNotNull(service, reference).joinToString(" · ")
            is Split -> null
        }
}

data class SplitLeg(val method: String, val amountCents: Long, val reference: String?)

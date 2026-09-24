package co.monveri.register.feature.cart

import co.monveri.register.data.repository.CartRepository
import co.monveri.register.data.repository.CatalogRepository
import co.monveri.register.data.repository.CheckoutRepository
import co.monveri.register.data.repository.RegisterSessionRepository
import co.monveri.register.data.repository.TicketSplitLeg
import co.monveri.register.data.repository.TicketSubmission
import co.monveri.register.data.repository.TicketSubmissionItem
import co.monveri.register.network.AuthHeaderProvider
import co.monveri.register.network.NetworkResult
import co.monveri.register.payments.PaymentSession
import co.monveri.register.payments.PaymentSessionState
import co.monveri.register.payments.TerminalManager
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.models.ConnectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Drives the whole checkout flow as a single state machine — matches iOS's `CheckoutView`, which
 * owns navigation itself via a `step` switch rather than pushing a separate screen per tender.
 * Card collection delegates to [PaymentSession] (`:core:payments`); this class owns tender
 * selection, cash rounding, ticket submission, and split-leg sequencing.
 */
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cart: CartRepository,
    private val checkoutRepository: CheckoutRepository,
    private val sessionRepository: RegisterSessionRepository,
    private val catalog: CatalogRepository,
    private val paymentSession: PaymentSession,
    private val terminalManager: TerminalManager,
    private val authHeaderProvider: AuthHeaderProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutUiState(totalCents = cart.current().totals.totalCents))
    val state: StateFlow<CheckoutUiState> = combine(
        _state,
        paymentSession.state,
        terminalManager.connectionStatus,
    ) { local, sessionState, connectionStatus ->
        local.copy(
            cardSessionState = sessionState,
            readerConnected = connectionStatus == ConnectionStatus.CONNECTED,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = _state.value,
    )

    /** Generic field mutator — see [co.monveri.register.feature.auth.BackOfficeProductEditorViewModel]
     * for why plain fields don't get one setter each. */
    fun updateState(transform: (CheckoutUiState) -> CheckoutUiState) {
        _state.value = transform(_state.value)
    }

    fun selectTender(step: CheckoutStep) {
        updateState { it.copy(step = step, errorMessage = null) }
    }

    fun backToTenderPicker() {
        paymentSession.cancel()
        updateState {
            it.copy(
                step = CheckoutStep.TENDER_PICKER,
                errorMessage = null,
                splitLegAState = LegState.Pending,
                splitLegBState = LegState.Pending,
            )
        }
    }

    // ---- Card (standalone tender) ----------------------------------------------------------

    fun beginCardPayment() {
        viewModelScope.launch {
            val result = paymentSession.begin(_state.value.totalCents, metadata = mapOf(METADATA_SOURCE_KEY to METADATA_SOURCE_VALUE))
            if (result is PaymentSessionState.Succeeded) {
                submit(PaymentOutcome.Card(result.paymentIntentId, result.cardBrand, result.cardLastFour))
            }
        }
    }

    fun retryCardPayment() {
        viewModelScope.launch {
            val result = paymentSession.retryAfterFailure()
            if (result is PaymentSessionState.Succeeded) {
                submit(PaymentOutcome.Card(result.paymentIntentId, result.cardBrand, result.cardLastFour))
            }
        }
    }

    fun cancelCardPayment() = paymentSession.cancel()

    // ---- Cash / Other (instant tenders) ----------------------------------------------------

    fun confirmCash(tenderedCents: Long) {
        val roundedTotal = CashRounding.roundedTotalCents(_state.value.totalCents)
        val change = (tenderedCents - roundedTotal).coerceAtLeast(0L)
        submit(PaymentOutcome.Cash(tenderedCents, change, roundedTotal))
    }

    fun confirmOther(service: String, reference: String?) {
        submit(PaymentOutcome.Other(service, reference?.takeIf { it.isNotBlank() }))
    }

    // ---- Split (two legs) -------------------------------------------------------------------

    fun confirmSplitLeg(legIndex: Int, outcome: PaymentOutcome) {
        updateState { state ->
            if (legIndex == FIRST_LEG) {
                state.copy(splitLegAState = LegState.Done(outcome))
            } else {
                state.copy(splitLegBState = LegState.Done(outcome))
            }
        }
    }

    fun beginSplitLegCard(legIndex: Int, amountCents: Long) {
        updateState { state ->
            if (legIndex == FIRST_LEG) state.copy(splitLegAState = LegState.Charging) else state.copy(splitLegBState = LegState.Charging)
        }
        viewModelScope.launch {
            val result = paymentSession.begin(amountCents, metadata = mapOf(METADATA_SOURCE_KEY to METADATA_SOURCE_VALUE))
            if (result is PaymentSessionState.Succeeded) {
                confirmSplitLeg(legIndex, PaymentOutcome.Card(result.paymentIntentId, result.cardBrand, result.cardLastFour))
            } else {
                updateState { state ->
                    if (legIndex == FIRST_LEG) state.copy(splitLegAState = LegState.Pending) else state.copy(splitLegBState = LegState.Pending)
                }
            }
        }
    }

    fun confirmSplit() {
        val state = _state.value
        val legA = (state.splitLegAState as? LegState.Done)?.outcome ?: return
        val legB = (state.splitLegBState as? LegState.Done)?.outcome ?: return
        val legs = listOf(
            outcomeToSplitLeg(legA, state.splitLegAAmountCents(state.totalCents)),
            outcomeToSplitLeg(legB, state.totalCents - state.splitLegAAmountCents(state.totalCents)),
        )
        submit(PaymentOutcome.Split(legs))
    }

    // ---- Submission ------------------------------------------------------------------------

    /** One idempotency key per checkout attempt — a retry after a submit failure reuses it. */
    private var submitIdempotencyKey: String = UUID.randomUUID().toString()

    private fun submit(outcome: PaymentOutcome) {
        updateState { it.copy(step = CheckoutStep.SUBMITTING, lastOutcome = outcome, errorMessage = null) }
        viewModelScope.launch { runSubmit(outcome) }
    }

    fun retrySubmit() {
        val outcome = _state.value.lastOutcome ?: return
        updateState { it.copy(step = CheckoutStep.SUBMITTING, errorMessage = null) }
        viewModelScope.launch { runSubmit(outcome) }
    }

    private suspend fun runSubmit(outcome: PaymentOutcome) {
        val snapshot = cart.current()
        val employeeId = authHeaderProvider.employeeId()?.toLong()
        if (employeeId == null) {
            updateState { it.copy(step = CheckoutStep.FAILED, errorMessage = "No signed-in employee") }
            return
        }
        val sessionId = (sessionRepository.current() as? NetworkResult.Success)?.data?.id

        val request = TicketSubmission(
            employeeId = employeeId,
            customerId = snapshot.customer?.id,
            payment = outcome.paymentMethod,
            paymentDetail = outcome.paymentDetail,
            subtotalCents = snapshot.totals.subtotalCents,
            discountCents = snapshot.totals.discountCents,
            discountType = null,
            taxCents = snapshot.totals.taxCents,
            taxRateBps = snapshot.taxRateBps,
            totalCents = (outcome as? PaymentOutcome.Cash)?.roundedTotalCents ?: snapshot.totals.totalCents,
            tenderedCents = (outcome as? PaymentOutcome.Cash)?.tenderedCents,
            changeCents = (outcome as? PaymentOutcome.Cash)?.changeCents,
            isSplitPayment = outcome is PaymentOutcome.Split,
            sessionId = sessionId,
            idempotencyKey = submitIdempotencyKey,
            items = snapshot.lines.map {
                TicketSubmissionItem(
                    sku = it.sku,
                    name = it.name,
                    upc = it.upc,
                    priceCents = it.unitPriceCents,
                    qty = it.quantity,
                    variantId = it.variantId,
                    unitOfSale = it.unitOfSale,
                )
            },
            splitLegs = (outcome as? PaymentOutcome.Split)?.legs?.map { TicketSplitLeg(it.method, it.amountCents, it.reference) },
            paymentIntentId = (outcome as? PaymentOutcome.Card)?.intentId,
            cardBrand = (outcome as? PaymentOutcome.Card)?.brand,
            cardLastFour = (outcome as? PaymentOutcome.Card)?.lastFour,
        )

        when (val result = checkoutRepository.submitTicket(request)) {
            is NetworkResult.Success -> {
                updateState {
                    it.copy(step = CheckoutStep.SUCCEEDED, ticketId = result.data.id, ticketToken = result.data.token)
                }
                viewModelScope.launch { catalog.sync() }
            }
            is NetworkResult.Failure -> {
                val cardIntentId = (outcome as? PaymentOutcome.Card)?.intentId
                updateState {
                    it.copy(step = CheckoutStep.FAILED, errorMessage = result.error.message, cardAuthorizedIntentId = cardIntentId)
                }
            }
        }
    }

    fun dismissError() {
        updateState { it.copy(errorMessage = null) }
    }

    fun emailReceipt(email: String) {
        val ticketId = _state.value.ticketId ?: return
        updateState { it.copy(isEmailingReceipt = true, emailReceiptResult = null) }
        viewModelScope.launch {
            val result = checkoutRepository.emailReceipt(ticketId, email, cart.current().customer?.id)
            updateState {
                it.copy(
                    isEmailingReceipt = false,
                    emailReceiptResult = when (result) {
                        is NetworkResult.Success -> "Receipt emailed to ${result.data}"
                        is NetworkResult.Failure -> result.error.message
                    },
                )
            }
        }
    }

    /** Cart clears and checkout resets — call when the cashier taps "Done" on the receipt screen. */
    fun finish() {
        cart.clear()
        submitIdempotencyKey = UUID.randomUUID().toString()
        updateState { CheckoutUiState(totalCents = 0L) }
    }

    private fun outcomeToSplitLeg(outcome: PaymentOutcome, amountCents: Long): SplitLeg = when (outcome) {
        is PaymentOutcome.Cash -> SplitLeg("Cash", amountCents, null)
        is PaymentOutcome.Card -> SplitLeg("Credit Card", amountCents, outcome.paymentDetail)
        is PaymentOutcome.Other -> SplitLeg(outcome.service, amountCents, outcome.reference)
        is PaymentOutcome.Split -> SplitLeg("Split", amountCents, null)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
        const val FIRST_LEG: Int = 0
        const val METADATA_SOURCE_KEY: String = "source"
        const val METADATA_SOURCE_VALUE: String = "monveri_register_android"
    }
}

/** Which side of the tap-to-charge lifecycle a split leg is in. */
sealed class LegState {
    data object Pending : LegState()
    data object Charging : LegState()
    data class Done(val outcome: PaymentOutcome) : LegState()
}

data class CheckoutUiState(
    val step: CheckoutStep = CheckoutStep.TENDER_PICKER,
    val totalCents: Long = 0L,
    val readerConnected: Boolean = false,
    val cardSessionState: PaymentSessionState = PaymentSessionState.Idle,
    val splitLegAMethod: String = "Cash",
    val splitLegAAmountText: String = "",
    val splitLegBMethod: String = "Card",
    val splitLegAState: LegState = LegState.Pending,
    val splitLegBState: LegState = LegState.Pending,
    val lastOutcome: PaymentOutcome? = null,
    val ticketId: Long? = null,
    val ticketToken: String? = null,
    val errorMessage: String? = null,
    val cardAuthorizedIntentId: String? = null,
    val isEmailingReceipt: Boolean = false,
    val emailReceiptResult: String? = null,
) {
    fun splitLegAAmountCents(totalCents: Long): Long =
        splitLegAAmountText.toLongOrNull()?.coerceIn(0L, totalCents) ?: 0L
}

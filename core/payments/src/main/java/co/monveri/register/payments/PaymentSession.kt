package co.monveri.register.payments

import co.monveri.register.network.NetworkResult
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.TerminalErrorCode
import com.stripe.stripeterminal.external.models.TerminalException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Orchestrates a card-present payment through Stripe Terminal, mirroring iOS's `PaymentSession`:
 *
 *   1. [PaymentIntentRepository.createIntent] — **server-side** create (never a local/on-device
 *      SDK create — see that interface's doc for why), producing a `client_secret`.
 *   2. `Terminal.retrievePaymentIntent(clientSecret)` — hydrates the SDK's intent object.
 *   3. `collectPaymentMethod` — reader prompts for tap/insert; cashier-cancellable.
 *   4. `confirmPaymentIntent` — sends the collected method to Stripe.
 *
 * One [PaymentSession] instance is scoped to a single checkout's card attempt (see
 * `CheckoutViewModel`). [retryAfterFailure] reuses the same idempotency key as [begin], so a
 * decline-then-retry lands on the *same* PaymentIntent instead of creating a duplicate charge
 * attempt. [cancel] always tries to release the server-side auth so an abandoned collect doesn't
 * leave a lingering hold on the customer's card.
 */
@Singleton
class PaymentSession @Inject constructor(
    private val terminalManager: TerminalManager,
    private val repository: PaymentIntentRepository,
) {

    private val _state = MutableStateFlow<PaymentSessionState>(PaymentSessionState.Idle)
    val state: Flow<PaymentSessionState> = _state.asStateFlow()

    private var inFlight: Cancelable? = null
    // Serializes concurrent begin()/retryAfterFailure() calls so two callers can't trample
    // `inFlight` or interleave state transitions.
    private val sessionMutex = Mutex()

    // Fire-and-forget scope for the server-side cancel call — cancel() itself must return
    // immediately so the UI doesn't block on a network round-trip to dismiss.
    private val cancelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Retained across a retryAfterFailure() so the retry targets the same PaymentIntent.
    private var pendingIdempotencyKey: String? = null
    private var pendingAmountCents: Long = 0L
    private var pendingTicketId: Long? = null
    private var pendingMetadata: Map<String, String>? = null
    private var pendingIntentId: String? = null

    /** Starts a fresh card attempt for [amountCents], minting a new idempotency key. */
    suspend fun begin(
        amountCents: Long,
        ticketId: Long? = null,
        metadata: Map<String, String>? = null,
    ): PaymentSessionState = sessionMutex.withLock {
        val key = UUID.randomUUID().toString()
        pendingIdempotencyKey = key
        pendingAmountCents = amountCents
        pendingTicketId = ticketId
        pendingMetadata = metadata
        runAttempt(key, amountCents, ticketId, metadata)
    }

    /** Re-runs the most recent [begin] attempt (same idempotency key) after a decline/failure. */
    suspend fun retryAfterFailure(): PaymentSessionState = sessionMutex.withLock {
        val key = pendingIdempotencyKey ?: return@withLock setState(PaymentSessionState.Idle)
        runAttempt(key, pendingAmountCents, pendingTicketId, pendingMetadata)
    }

    /** Cancels the in-flight collect/confirm step (if any) and releases the server-side auth. */
    fun cancel() {
        inFlight?.cancel(NoopPaymentCallback)
        inFlight = null
        val intentId = pendingIntentId
        _state.value = PaymentSessionState.Canceled
        if (intentId != null) {
            cancelScope.launch { repository.cancelIntent(intentId, CANCEL_REASON_ABANDONED) }
        }
        pendingIntentId = null
        pendingIdempotencyKey = null
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runAttempt(
        idempotencyKey: String,
        amountCents: Long,
        ticketId: Long?,
        metadata: Map<String, String>?,
    ): PaymentSessionState {
        terminalManager.ensureInitialized()
        if (Terminal.getInstance().connectedReader == null) {
            return setState(PaymentSessionState.Failed("No reader connected"))
        }
        try {
            setState(PaymentSessionState.CreatingIntent)
            val pending = when (val created = repository.createIntent(amountCents, idempotencyKey, ticketId, metadata)) {
                is NetworkResult.Success -> created.data
                is NetworkResult.Failure -> return setState(PaymentSessionState.Failed(created.error.message))
            }
            pendingIntentId = pending.paymentIntentId

            val sdkIntent = retrievePaymentIntent(pending.clientSecret)

            setState(PaymentSessionState.AwaitingCard)
            val collected = collectPaymentMethod(sdkIntent)

            setState(PaymentSessionState.Processing)
            val confirmed = confirmPaymentIntent(collected)

            val cardDetails = confirmed.paymentMethod?.cardPresentDetails
            return setState(
                PaymentSessionState.Succeeded(pending.paymentIntentId, cardDetails?.brand, cardDetails?.last4),
            )
        } catch (e: CancellationException) {
            _state.value = PaymentSessionState.Idle
            throw e
        } catch (e: TerminalException) {
            val isDecline = e.errorCode == TerminalErrorCode.DECLINED_BY_STRIPE_API ||
                e.errorCode == TerminalErrorCode.DECLINED_BY_READER
            return setState(
                if (isDecline) PaymentSessionState.Declined(e.errorMessage) else PaymentSessionState.Failed(e.errorMessage),
            )
        } catch (e: Exception) {
            return setState(PaymentSessionState.Failed(e.message ?: "Payment failed"))
        } finally {
            inFlight = null
        }
    }

    private suspend fun retrievePaymentIntent(clientSecret: String): PaymentIntent =
        suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().retrievePaymentIntent(clientSecret, object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    if (continuation.isActive) continuation.resume(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            })
        }

    private suspend fun collectPaymentMethod(intent: PaymentIntent): PaymentIntent =
        suspendCancellableCoroutine { continuation ->
            inFlight = Terminal.getInstance().collectPaymentMethod(
                intent,
                object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        if (continuation.isActive) continuation.resume(paymentIntent)
                    }

                    override fun onFailure(e: TerminalException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                },
            )
            continuation.invokeOnCancellation { inFlight?.cancel(NoopPaymentCallback) }
        }

    private suspend fun confirmPaymentIntent(intent: PaymentIntent): PaymentIntent =
        suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().confirmPaymentIntent(intent, object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    if (continuation.isActive) continuation.resume(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            })
        }

    private fun setState(next: PaymentSessionState): PaymentSessionState {
        _state.value = next
        return next
    }

    private companion object {
        const val CANCEL_REASON_ABANDONED: String = "abandoned"
    }
}

/** UI-facing state machine for a single card payment attempt. */
sealed class PaymentSessionState {
    data object Idle : PaymentSessionState()
    data object CreatingIntent : PaymentSessionState()
    data object AwaitingCard : PaymentSessionState()
    data object Processing : PaymentSessionState()
    data class Succeeded(val paymentIntentId: String, val cardBrand: String?, val cardLastFour: String?) :
        PaymentSessionState()
    data class Declined(val message: String) : PaymentSessionState()
    data class Failed(val message: String) : PaymentSessionState()
    data object Canceled : PaymentSessionState()
}

private object NoopPaymentCallback : com.stripe.stripeterminal.external.callable.Callback {
    override fun onSuccess() = Unit
    override fun onFailure(e: TerminalException) = Unit
}

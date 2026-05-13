package co.monveri.register.payments

import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.models.CaptureMethod
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentIntentParameters
import com.stripe.stripeterminal.external.models.TerminalException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Orchestrates the three-step Stripe Terminal payment flow:
 *
 *   1. `createPaymentIntent` — local SDK call to mint a PaymentIntent against the connected reader
 *   2. `collectPaymentMethod` — reader prompts for tap / insert; cashier-cancellable
 *   3. `confirmPaymentIntent` — sends the collected method to Stripe for capture
 *
 * State exposed via [state] so the UI can render "Tap to pay" / "Processing…" / "Approved" /
 * "Declined" without ever knowing about the SDK callbacks.
 *
 * Phase 4 only uses this for the debug Test Harness ($0.50). Phase 6 will plug it into the real
 * checkout flow where the amount comes from the cart.
 */
@Singleton
class PaymentSession @Inject constructor(
    private val terminalManager: TerminalManager,
) {

    private val _state = MutableStateFlow<PaymentSessionState>(PaymentSessionState.Idle)
    val state: Flow<PaymentSessionState> = _state.asStateFlow()

    private var inFlight: Cancelable? = null

    /**
     * Run a one-shot payment for [amountCents] in [currency]. Returns the final state — the same
     * value that gets emitted on [state]. Throws nothing; failures are wrapped in
     * [PaymentSessionState.Failed].
     */
    suspend fun charge(amountCents: Long, currency: String = "usd"): PaymentSessionState {
        terminalManager.ensureInitialized()
        if (Terminal.getInstance().connectedReader == null) {
            return setState(PaymentSessionState.Failed("No reader connected"))
        }
        return try {
            setState(PaymentSessionState.CreatingIntent)
            val params = PaymentIntentParameters.Builder()
                .setAmount(amountCents)
                .setCurrency(currency)
                // Automatic capture mirrors the iOS test harness ($1.00) flow — no manual capture call.
                .setCaptureMethod(CaptureMethod.Automatic)
                .build()
            val intent = createIntent(params)

            setState(PaymentSessionState.AwaitingCard)
            val collected = collectPaymentMethod(intent)

            setState(PaymentSessionState.Processing)
            val confirmed = confirmPaymentIntent(collected)

            setState(PaymentSessionState.Succeeded(confirmed.id ?: ""))
        } catch (e: TerminalException) {
            setState(PaymentSessionState.Failed(e.errorMessage))
        } catch (e: Exception) {
            // Defensive — collectPaymentMethod can throw IllegalStateException etc.
            setState(PaymentSessionState.Failed(e.message ?: "Payment failed"))
        } finally {
            inFlight = null
        }
    }

    /** Cancel the in-flight collect step. No-op if nothing is collecting. */
    fun cancel() {
        inFlight?.cancel(NoopPaymentCallback)
        inFlight = null
        _state.value = PaymentSessionState.Idle
    }

    private suspend fun createIntent(params: PaymentIntentParameters): PaymentIntent =
        suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().createPaymentIntent(params, object : PaymentIntentCallback {
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
}

/** UI-facing state machine for a single payment attempt. */
sealed class PaymentSessionState {
    data object Idle : PaymentSessionState()
    data object CreatingIntent : PaymentSessionState()
    data object AwaitingCard : PaymentSessionState()
    data object Processing : PaymentSessionState()
    data class Succeeded(val paymentIntentId: String) : PaymentSessionState()
    data class Failed(val message: String) : PaymentSessionState()
}

private object NoopPaymentCallback : com.stripe.stripeterminal.external.callable.Callback {
    override fun onSuccess() = Unit
    override fun onFailure(e: TerminalException) = Unit
}

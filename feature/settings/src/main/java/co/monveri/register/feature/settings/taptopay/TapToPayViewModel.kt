package co.monveri.register.feature.settings.taptopay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.network.MonveriApi
import co.monveri.register.payments.DeviceCapability
import co.monveri.register.payments.PaymentSession
import co.monveri.register.payments.PaymentSessionState
import co.monveri.register.payments.TapToPayService
import co.monveri.register.payments.TapToPayReadiness
import co.monveri.register.payments.TerminalManager
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.TerminalException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Drives the Tap to Pay screen: runs the readiness check, connects the local reader, and (in the
 * debug-style $1.00 test charge) exercises the shared [PaymentSession] so the end-to-end path can
 * be validated on real NFC hardware before Phase 6 wires it into checkout.
 *
 * Architecture mirrors [co.monveri.register.feature.settings.reader.ReaderDiscoveryViewModel] —
 * SDK callback flows are composed into one render-ready [TapToPayUiState]; no Stripe callback
 * leaks past this class.
 */
@HiltViewModel
class TapToPayViewModel @Inject constructor(
    private val terminalManager: TerminalManager,
    private val deviceCapability: DeviceCapability,
    private val tapToPayService: TapToPayService,
    private val paymentSession: PaymentSession,
    private val api: MonveriApi,
) : ViewModel() {

    // Seed with the cheap local-only signals so the screen renders something useful on first
    // frame; the authoritative Stripe check fills `stripeSupported` in once it returns.
    private val readiness = MutableStateFlow(deviceCapability.localReadiness())
    private val isConnecting = MutableStateFlow(false)
    private val isCharging = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val lastResult = MutableStateFlow<TestChargeResult?>(null)

    val state: StateFlow<TapToPayUiState> = combine(
        terminalManager.connectionStatus,
        paymentSession.state,
        readiness,
        combine(isConnecting, isCharging, errorMessage, lastResult) { connecting, charging, error, result ->
            LocalUi(connecting, charging, error, result)
        },
    ) { connectionStatus, sessionState, ready, local ->
        TapToPayUiState(
            readiness = ready,
            connectionStatus = connectionStatus,
            statusLine = sessionState.toLine(),
            isConnecting = local.connecting,
            isCharging = local.charging,
            canConnect = ready.isReady &&
                connectionStatus != ConnectionStatus.CONNECTED &&
                !local.connecting,
            canCharge = connectionStatus == ConnectionStatus.CONNECTED && !local.charging,
            errorMessage = local.error,
            lastResult = local.result,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TapToPayUiState(readiness = deviceCapability.localReadiness()),
    )

    init {
        terminalManager.ensureInitialized()
        refreshReadiness()
    }

    /** Re-run the full readiness check (e.g. after the cashier toggles NFC and returns). */
    fun refreshReadiness() {
        viewModelScope.launch {
            // fullReadiness() initialises Terminal + hits the Stripe device allow-list — push it
            // off the main thread so the screen stays responsive while it resolves.
            readiness.value = withContext(Dispatchers.IO) { deviceCapability.fullReadiness() }
        }
    }

    fun connect() {
        if (isConnecting.value) return
        isConnecting.value = true
        errorMessage.value = null
        viewModelScope.launch {
            try {
                val locationId = fetchLocationId()
                if (locationId == null) {
                    errorMessage.value = "No Stripe location configured for this store"
                    return@launch
                }
                tapToPayService.connect(locationId)
            } catch (e: TerminalException) {
                errorMessage.value = e.errorMessage
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Could not start Tap to Pay"
            } finally {
                isConnecting.value = false
            }
        }
    }

    fun charge() {
        if (isCharging.value) return
        isCharging.value = true
        lastResult.value = null
        errorMessage.value = null
        viewModelScope.launch {
            val result = paymentSession.charge(amountCents = TEST_CHARGE_CENTS)
            isCharging.value = false
            lastResult.value = when (result) {
                is PaymentSessionState.Succeeded -> TestChargeResult(
                    "Approved · ${result.paymentIntentId}",
                    isError = false,
                )
                is PaymentSessionState.Failed -> TestChargeResult(result.message, isError = true)
                else -> null
            }
        }
    }

    fun cancel() {
        paymentSession.cancel()
        isCharging.value = false
    }

    fun disconnect() {
        viewModelScope.launch { runCatching { tapToPayService.disconnect() } }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    /**
     * Same trick as the Bluetooth reader VM: the connection-token endpoint echoes the configured
     * `location_id`, so mint-and-discard rather than adding a second backend call. Exceptions
     * propagate to [connect]'s catch so transport failure reads differently from "no location".
     */
    private suspend fun fetchLocationId(): String? {
        val envelope = api.stripeConnectionToken()
        return envelope.data?.locationId
    }

    private companion object {
        // $1.00 — Tap to Pay has a higher Stripe test-charge floor than the M2's $0.50; a sub-$1
        // tap is rejected by the network in test mode on some card brands.
        const val TEST_CHARGE_CENTS: Long = 100
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}

/** Inner-combine bag — keeps the outer combine within its 5-arg arity overload. */
private data class LocalUi(
    val connecting: Boolean,
    val charging: Boolean,
    val error: String?,
    val result: TestChargeResult?,
)

/** Result of the on-screen test charge. */
data class TestChargeResult(val message: String, val isError: Boolean)

/** Render-ready snapshot for the Tap to Pay screen. No SDK callbacks leak through. */
data class TapToPayUiState(
    val readiness: TapToPayReadiness,
    val connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED,
    val statusLine: String = "Ready",
    val isConnecting: Boolean = false,
    val isCharging: Boolean = false,
    val canConnect: Boolean = false,
    val canCharge: Boolean = false,
    val errorMessage: String? = null,
    val lastResult: TestChargeResult? = null,
)

private fun PaymentSessionState.toLine(): String = when (this) {
    PaymentSessionState.Idle -> "Ready to tap"
    PaymentSessionState.CreatingIntent -> "Preparing…"
    PaymentSessionState.AwaitingCard -> "Hold card to the back of the phone"
    PaymentSessionState.Processing -> "Reading card…"
    is PaymentSessionState.Succeeded -> "Approved"
    is PaymentSessionState.Failed -> "Declined"
}

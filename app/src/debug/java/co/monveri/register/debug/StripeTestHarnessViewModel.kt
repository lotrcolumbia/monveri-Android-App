package co.monveri.register.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.payments.PaymentSession
import co.monveri.register.payments.PaymentSessionState
import co.monveri.register.payments.TerminalManager
import com.stripe.stripeterminal.external.models.ConnectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the debug Test Harness screen. Composes the live SDK connection / payment status with
 * a single "lastResult" string the screen renders (Approved / Declined / Cancelled).
 */
@HiltViewModel
class StripeTestHarnessViewModel @Inject constructor(
    private val terminalManager: TerminalManager,
    private val paymentSession: PaymentSession,
) : ViewModel() {

    private val isCharging = MutableStateFlow(false)
    private val lastResult = MutableStateFlow<Pair<String, Boolean>?>(null)

    val state: StateFlow<StripeTestHarnessUiState> = combine(
        terminalManager.connectionStatus,
        terminalManager.connectedReader,
        paymentSession.state,
        isCharging,
        lastResult,
    ) { status, reader, sessionState, charging, last ->
        StripeTestHarnessUiState(
            connectionStatus = status,
            readerSerial = reader?.serialNumber,
            statusLine = sessionState.toLine(),
            canCharge = status == ConnectionStatus.CONNECTED && !charging,
            isCharging = charging,
            lastResult = last?.first,
            lastResultIsError = last?.second ?: false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StripeTestHarnessUiState(),
    )

    init {
        // Forward terminal init in case the user lands here before the catalog flow has run.
        terminalManager.ensureInitialized()
        paymentSession.state
            .onEach { handleTerminalState(it) }
            .launchIn(viewModelScope)
    }

    fun charge() {
        if (isCharging.value) return
        isCharging.value = true
        lastResult.value = null
        viewModelScope.launch {
            val result = paymentSession.charge(amountCents = TEST_CHARGE_CENTS)
            isCharging.value = false
            lastResult.value = when (result) {
                is PaymentSessionState.Succeeded -> "Approved · ${result.paymentIntentId}" to false
                is PaymentSessionState.Failed -> result.message to true
                else -> null
            }
        }
    }

    fun cancel() {
        paymentSession.cancel()
        isCharging.value = false
    }

    private fun handleTerminalState(state: PaymentSessionState) {
        if (state is PaymentSessionState.Failed && lastResult.value == null) {
            lastResult.value = state.message to true
        }
    }

    private companion object {
        const val TEST_CHARGE_CENTS: Long = 50
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}

private fun PaymentSessionState.toLine(): String = when (this) {
    PaymentSessionState.Idle -> "Ready"
    PaymentSessionState.CreatingIntent -> "Creating PaymentIntent…"
    PaymentSessionState.AwaitingCard -> "Tap or insert card on the reader"
    PaymentSessionState.Processing -> "Processing payment…"
    is PaymentSessionState.Succeeded -> "Approved"
    is PaymentSessionState.Failed -> "Failed"
}

data class StripeTestHarnessUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED,
    val readerSerial: String? = null,
    val statusLine: String = "Ready",
    val canCharge: Boolean = false,
    val isCharging: Boolean = false,
    val lastResult: String? = null,
    val lastResultIsError: Boolean = false,
)

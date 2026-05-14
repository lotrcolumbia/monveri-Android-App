package co.monveri.register.payments

import android.content.Context
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.log.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single facade for the Stripe Terminal SDK. **All** SDK interaction in the app should route
 * through this class — feature modules never `import com.stripe.stripeterminal.*` directly.
 *
 * Lifetime: process-singleton, kept alive by Hilt. The underlying [Terminal.getInstance] is also
 * a process singleton; double-init is guarded so calling [ensureInitialized] from both `MonveriApp`
 * and a unit test doesn't blow up.
 *
 * The exposed [connectionStatus] / [paymentStatus] / [connectedReader] StateFlows let UI layers
 * collect via `collectAsStateWithLifecycle()` without ever touching SDK callbacks.
 */
@Singleton
class TerminalManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenProvider: MonveriConnectionTokenProvider,
) : TerminalListener {

    // Guards `ensureInitialized()` so two cold-start callers (MonveriApp.onCreate + a ViewModel
    // injecting TerminalManager) can't both race past the isInitialized() fast-path and call
    // initTerminal twice — Stripe throws IllegalStateException on the second init.
    private val initLock = Any()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.NOT_CONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _paymentStatus = MutableStateFlow(PaymentStatus.NOT_READY)
    val paymentStatus: StateFlow<PaymentStatus> = _paymentStatus.asStateFlow()

    private val _connectedReader = MutableStateFlow<Reader?>(null)
    val connectedReader: StateFlow<Reader?> = _connectedReader.asStateFlow()

    /**
     * Idempotent. Safe to call from `MonveriApp.onCreate()` and from tests; only the first call
     * does real work because [Terminal.isInitialized] is a singleton check.
     */
    fun ensureInitialized() {
        if (Terminal.isInitialized()) return
        synchronized(initLock) {
            if (Terminal.isInitialized()) return
            try {
                Terminal.initTerminal(
                    context,
                    // VERBOSE in debug, NONE in release — the LogLevel enum doesn't expose `Off`
                    // so we pass `NONE` explicitly. Sensitive auth payloads stay scrubbed by the
                    // SDK either way.
                    if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.NONE,
                    tokenProvider,
                    this,
                )
            } catch (e: IllegalStateException) {
                // SDK reports duplicate init via IllegalStateException. With the double-check
                // above this shouldn't happen, but defending against it keeps an unusual class
                // loader / multi-process scenario from crashing onCreate.
                if (!Terminal.isInitialized()) throw e
            }
        }
    }

    override fun onUnexpectedReaderDisconnect(reader: Reader) {
        // The SDK has dropped the reader without a corresponding disconnect call from us.
        // Surface the change immediately; auto-reconnect is handled at the UI layer (settings screen).
        _connectedReader.value = null
        _connectionStatus.value = ConnectionStatus.NOT_CONNECTED
    }

    override fun onConnectionStatusChange(status: ConnectionStatus) {
        _connectionStatus.value = status
        if (status == ConnectionStatus.NOT_CONNECTED) {
            _connectedReader.value = null
        } else if (status == ConnectionStatus.CONNECTED) {
            _connectedReader.value = Terminal.getInstance().connectedReader
        }
    }

    override fun onPaymentStatusChange(status: PaymentStatus) {
        _paymentStatus.value = status
    }

    /** Convenience accessor — the SDK's connected reader, never holds onto stale references. */
    fun currentReader(): Reader? = if (Terminal.isInitialized()) {
        Terminal.getInstance().connectedReader
    } else {
        null
    }
}

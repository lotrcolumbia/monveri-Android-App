package co.monveri.register.payments

import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.callable.TapToPayReaderListener
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.TapToPayConnectionConfiguration
import com.stripe.stripeterminal.external.models.DisconnectReason
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Tap to Pay on Android equivalent of [ReaderDiscovery]. The "reader" here is the merchant's own
 * phone — there is no pairing UI, no Bluetooth, no nearby-device list to choose from. Discovery
 * returns exactly one synthetic [Reader] (the local device); we connect to it and hand off to the
 * shared [PaymentSession] which is reader-agnostic.
 *
 * Kept separate from [ReaderDiscovery] rather than folded in: the connection configs, listeners,
 * and discovery semantics differ enough between Bluetooth M2 and Tap to Pay that one branchy class
 * would be harder to follow than two focused ones. Both lean on [TerminalManager] for init/status.
 *
 * **SDK note (v4.x):** Stripe renamed the v2-era *LocalMobile* surface to *Tap to Pay* in v4.0.0,
 * so the concrete types are `TapToPayDiscoveryConfiguration` / `TapToPayConnectionConfiguration` /
 * `TapToPayReaderListener`. v4 also consolidated every `connect*Reader()` into a single
 * `Terminal.connectReader()` (the connection config's concrete subtype selects the transport).
 */
@Singleton
class TapToPayService @Inject constructor(
    private val terminalManager: TerminalManager,
) {

    /**
     * Discover the local Tap to Pay reader and connect it. One-shot suspend call: resolves with
     * the connected [Reader] or throws [TerminalException]. `locationId` is required by Stripe
     * exactly as for Bluetooth — sourced from the connection token's `location_id`.
     *
     * Discovery and connect are chained: Tap to Pay discovery emits a single local reader almost
     * immediately, so we take the first one and connect rather than exposing a picker.
     */
    suspend fun connect(locationId: String): Reader {
        terminalManager.ensureInitialized()
        val reader = discoverLocalReader()
        return connectReader(reader, locationId)
    }

    /** Suspend disconnect — used by the diagnostics "Disconnect" action and sign-out. */
    suspend fun disconnect(): Unit = suspendCancellableCoroutine { continuation ->
        if (!Terminal.isInitialized() || Terminal.getInstance().connectedReader == null) {
            if (continuation.isActive) continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        Terminal.getInstance().disconnectReader(object : Callback {
            override fun onSuccess() {
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onFailure(e: TerminalException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        })
    }

    /**
     * Tap to Pay discovery surfaces the phone itself as a single [Reader]. We resolve on the first
     * non-empty emission and cancel discovery — there is never a second device to wait for, and
     * holding discovery open would keep the SDK in scan mode unnecessarily.
     */
    private suspend fun discoverLocalReader(): Reader = suspendCancellableCoroutine { continuation ->
        // isSimulated = BuildConfig.DEBUG: the production Tap to Pay reader refuses to run inside
        // any debuggable APK (an explicit Stripe SDK error, not a device limitation) — a release
        // build always asks for the real reader. See DeviceCapability.fullReadiness for the
        // matching readiness-check flag.
        val config = DiscoveryConfiguration.TapToPayDiscoveryConfiguration(isSimulated = BuildConfig.DEBUG)
        var cancelable: Cancelable? = null
        val listener = object : DiscoveryListener {
            override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                val first = readers.firstOrNull() ?: return
                if (continuation.isActive) {
                    continuation.resume(first)
                    // Stop scanning the instant we have the local reader.
                    cancelable?.cancel(NoopCallback)
                }
            }
        }
        cancelable = Terminal.getInstance().discoverReaders(
            config,
            listener,
            object : Callback {
                override fun onSuccess() {
                    // Discovery completed without ever emitting a reader — treat as unsupported
                    // rather than hanging the caller forever.
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("No Tap to Pay reader available on this device"),
                        )
                    }
                }

                override fun onFailure(e: TerminalException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            },
        )
        continuation.invokeOnCancellation { cancelable?.cancel(NoopCallback) }
    }

    private suspend fun connectReader(reader: Reader, locationId: String): Reader =
        suspendCancellableCoroutine { continuation ->
            // The Tap to Pay listener carries reader-event callbacks (e.g. "remove card") — we
            // don't surface those in Phase 5 (the full-screen tap UI is driven by PaymentSession
            // state), so an empty impl is correct. autoReconnect=false: Tap to Pay re-discovers
            // instantly on the next charge, no persistent link to keep alive.
            val config = TapToPayConnectionConfiguration(
                locationId = locationId,
                autoReconnectOnUnexpectedDisconnect = false,
                tapToPayReaderListener = object : TapToPayReaderListener {
                    override fun onDisconnect(reason: DisconnectReason) {
                        // Mirrors TerminalManager.onUnexpectedReaderDisconnect — the manager's
                        // ConnectionStatus flow already drives the UI, nothing extra to do here.
                    }
                },
            )
            Terminal.getInstance().connectReader(
                reader,
                config,
                object : ReaderCallback {
                    override fun onSuccess(reader: Reader) {
                        if (continuation.isActive) continuation.resume(reader)
                    }

                    override fun onFailure(e: TerminalException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                },
            )
        }
}

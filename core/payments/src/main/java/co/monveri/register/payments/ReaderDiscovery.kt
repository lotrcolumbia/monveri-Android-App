package co.monveri.register.payments

import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.MobileReaderListener
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.BluetoothConnectionConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.ReaderSoftwareUpdate
import com.stripe.stripeterminal.external.models.TerminalException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Discovery + connect helpers built on top of [Terminal]. Two surfaces:
 *
 *  - [discoverReaders] — a Flow of "current nearby readers" lists. Cancelling the collector
 *    cancels the underlying SDK discovery via the returned [Cancelable].
 *  - [connect] — suspend wrapper around `Terminal.connectReader` so the caller can `await`
 *    the result. The minimal [MobileReaderListener] forwards firmware-update progress to the
 *    optional [updateListener] (cashier sees "Updating reader 47%" while it's working).
 *
 * v4 notes: `DiscoveryConfiguration` is a sealed class with nested concrete types; pick
 * `BluetoothDiscoveryConfiguration`. v4 consolidated all `connect*Reader()` into one
 * `Terminal.connectReader()`, and the reader listener now lives inside
 * `BluetoothConnectionConfiguration` (the `bluetoothReaderListener` argument) rather than being a
 * separate connect parameter.
 */
@Singleton
class ReaderDiscovery @Inject constructor(
    private val terminalManager: TerminalManager,
) {

    /** Live nearby-reader list. Each emission replaces the previous one (Stripe's contract). */
    fun discoverReaders(timeoutSeconds: Int = DISCOVERY_TIMEOUT_SECONDS): Flow<List<Reader>> = callbackFlow {
        terminalManager.ensureInitialized()
        val config = DiscoveryConfiguration.BluetoothDiscoveryConfiguration(
            timeout = timeoutSeconds,
            // Simulated readers gated to the debug Test Harness — never enabled here.
            isSimulated = false,
        )
        val listener = object : DiscoveryListener {
            override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                // `trySend` is non-blocking; if the collector is slow we drop intermediate frames
                // (acceptable — we only care about the latest list).
                trySend(readers)
            }
        }
        val cancelable = Terminal.getInstance().discoverReaders(
            config,
            listener,
            object : Callback {
                override fun onSuccess() {
                    // SDK reports discovery completed (timeout reached). Close the flow so
                    // collectors get a terminal signal and can reset their `isDiscovering` UI
                    // flag — otherwise the spinner sticks forever.
                    close()
                }
                override fun onFailure(e: TerminalException) {
                    close(e)
                }
            },
        )
        awaitClose { cancelable.cancel(NoopCallback) }
    }

    /**
     * Connect to [reader] over Bluetooth and resolve once the SDK reports success or failure.
     * `locationId` is required by Stripe — we expect the caller to pull it from the connection
     * token's `location_id` field (or the store's `stripe_terminal_location_id` setting).
     */
    suspend fun connect(
        reader: Reader,
        locationId: String,
        updateListener: ReaderUpdateListener? = null,
    ): Reader = suspendCancellableCoroutine { continuation ->
        terminalManager.ensureInitialized()
        // v4: the reader listener moved off connectReader() and into the connection config.
        // autoReconnectOnUnexpectedDisconnect=false preserves Phase 4 behaviour (reconnection is
        // driven from the settings UI, not the SDK's auto-reconnect, which now defaults on).
        val readerListener = object : MobileReaderListener {
            override fun onStartInstallingUpdate(
                update: ReaderSoftwareUpdate,
                cancelable: Cancelable?,
            ) {
                // ReaderSoftwareUpdate.estimatedUpdateTime was removed in v3.x — just fire
                // the start callback without an estimate; the progress hook drives UI updates.
                updateListener?.onStart()
            }

            override fun onReportReaderSoftwareUpdateProgress(progress: Float) {
                updateListener?.onProgress(progress)
            }

            override fun onFinishInstallingUpdate(
                update: ReaderSoftwareUpdate?,
                e: TerminalException?,
            ) {
                if (e != null) {
                    updateListener?.onFailure(e.message ?: "Update failed")
                } else {
                    updateListener?.onSuccess()
                }
            }
        }
        val config = BluetoothConnectionConfiguration(
            locationId = locationId,
            autoReconnectOnUnexpectedDisconnect = false,
            bluetoothReaderListener = readerListener,
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

    /** Suspend disconnect — used by "Forget reader" + sign-out paths. */
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

    private companion object {
        // Stripe's docs recommend 10–60 seconds; 15s balances responsiveness vs. discovery range.
        const val DISCOVERY_TIMEOUT_SECONDS: Int = 15
    }
}

/** Optional callback bag for surfacing reader firmware updates while [ReaderDiscovery.connect] runs. */
interface ReaderUpdateListener {
    fun onStart()
    fun onProgress(progress: Float)
    fun onSuccess()
    fun onFailure(message: String)
}

/**
 * Stripe SDK callbacks require non-null Callback impls even when the caller doesn't care.
 * `internal` so both [ReaderDiscovery] and [TapToPayService] share this single no-op rather than
 * each declaring a file-private copy (two top-level `NoopCallback`s in this package collide).
 */
internal object NoopCallback : Callback {
    override fun onSuccess() = Unit
    override fun onFailure(e: TerminalException) = Unit
}

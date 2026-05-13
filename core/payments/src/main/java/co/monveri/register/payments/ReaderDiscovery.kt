package co.monveri.register.payments

import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.BluetoothReaderListener
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.models.BluetoothConnectionConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryMethod
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
 *  - [connect] — suspend wrapper around `connectBluetoothReader` so the caller can `await`
 *    the result. The minimal [BluetoothReaderListener] forwards firmware-update progress to
 *    the optional [updateListener] (cashier sees "Updating reader 47%" while it's working).
 */
@Singleton
class ReaderDiscovery @Inject constructor(
    private val terminalManager: TerminalManager,
) {

    /** Live nearby-reader list. Each emission replaces the previous one (Stripe's contract). */
    fun discoverReaders(timeoutSeconds: Int = DISCOVERY_TIMEOUT_SECONDS): Flow<List<Reader>> = callbackFlow {
        terminalManager.ensureInitialized()
        val config = DiscoveryConfiguration(
            timeoutSeconds,
            DiscoveryMethod.BLUETOOTH_SCAN,
            // Simulated readers gated to the debug Test Harness — never enabled here.
            false,
        )
        val listener = DiscoveryListener { readers ->
            // `trySend` is non-blocking; if the collector is slow we drop intermediate frames
            // (acceptable — we only care about the latest list).
            trySend(readers)
        }
        val cancelable = Terminal.getInstance().discoverReaders(
            config,
            listener,
            object : Callback {
                override fun onSuccess() = Unit
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
        val config = BluetoothConnectionConfiguration(
            locationId,
            // Auto-reconnect on transient disconnect — the SDK retries with backoff for ~1 minute.
            true,
            object : BluetoothReaderListener {
                override fun onStartInstallingUpdate(
                    update: ReaderSoftwareUpdate,
                    cancelable: com.stripe.stripeterminal.external.callable.Cancelable?,
                ) {
                    updateListener?.onStart(update.estimatedUpdateTime?.toString().orEmpty())
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
            },
        )
        Terminal.getInstance().connectBluetoothReader(
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
    fun onStart(estimatedDuration: String)
    fun onProgress(progress: Float)
    fun onSuccess()
    fun onFailure(message: String)
}

/** Stripe SDK callbacks require non-null Callback impls even when the caller doesn't care. */
private object NoopCallback : Callback {
    override fun onSuccess() = Unit
    override fun onFailure(e: TerminalException) = Unit
}

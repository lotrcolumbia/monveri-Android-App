package co.monveri.register.feature.settings.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.network.MonveriApi
import co.monveri.register.payments.ReaderDiscovery
import co.monveri.register.payments.ReaderPreferences
import co.monveri.register.payments.TerminalManager
import co.monveri.register.payments.service.StripeReaderService
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.TerminalException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Reader Settings surface — discovery list, connect/disconnect, persistence, and the
 * silent auto-reconnect on cold start.
 *
 * Two side-effects deliberately kept in the ViewModel (not the screen):
 *  - `StripeReaderService.start()` on successful connect so backgrounding doesn't kill the BT link.
 *  - `ReaderPreferences.rememberReader()` so the next process can auto-reconnect without UI.
 */
@HiltViewModel
class ReaderDiscoveryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val terminalManager: TerminalManager,
    private val readerDiscovery: ReaderDiscovery,
    private val readerPreferences: ReaderPreferences,
    private val api: MonveriApi,
) : ViewModel() {

    private val isDiscovering = MutableStateFlow(false)
    private val isConnecting = MutableStateFlow(false)
    private val nearbyReaders = MutableStateFlow<List<Reader>>(emptyList())
    private val errorMessage = MutableStateFlow<String?>(null)
    private val pendingFirmware = MutableStateFlow<String?>(null)
    // Promoted from a one-shot _state.copy() write to its own StateFlow so the top-level combine
    // can include it as an input — otherwise the next combine emit would overwrite it back to null.
    private val rememberedSerial = MutableStateFlow<String?>(null)

    private var discoveryJob: Job? = null

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        terminalManager.ensureInitialized()

        // Pipe SDK status + local UI flags into a single render-ready state. The inner combine
        // bundles four sub-flows so we stay under the outer combine's 5-argument arity overload.
        combine(
            terminalManager.connectionStatus,
            terminalManager.connectedReader,
            isDiscovering,
            isConnecting,
            combine(
                nearbyReaders,
                errorMessage,
                pendingFirmware,
                rememberedSerial,
            ) { readers, error, firmware, serial ->
                LocalUi(readers, error, firmware, serial)
            },
        ) { status, reader, discovering, connecting, local ->
            ReaderUiState(
                connectionStatus = status,
                connectedReader = reader,
                discoveredReaders = local.readers,
                isDiscovering = discovering,
                isConnecting = connecting,
                errorMessage = local.error,
                firmwareMessage = local.firmware,
                rememberedSerial = local.serial,
            )
        }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)

        // Cold-start auto-reconnect — fire-and-forget. Failures fall through to the discovery UI.
        viewModelScope.launch { tryAutoReconnect() }
    }

    fun startDiscovery() {
        if (isDiscovering.value) return
        errorMessage.value = null
        isDiscovering.value = true
        nearbyReaders.value = emptyList()
        discoveryJob = readerDiscovery.discoverReaders()
            .onEach { nearbyReaders.value = it }
            .catch { e ->
                // SDK discovery failed (e.g. BT off mid-scan). Surface the message and let
                // onCompletion below reset the spinner.
                errorMessage.value = e.message ?: "Discovery failed"
            }
            .onCompletion {
                // Fires on normal completion (SDK timeout reached) AND after .catch — covers both
                // happy-path completion and the error branch so the spinner never sticks.
                isDiscovering.value = false
            }
            .launchIn(viewModelScope)
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        isDiscovering.value = false
    }

    fun connect(reader: Reader) {
        if (isConnecting.value) return
        isConnecting.value = true
        errorMessage.value = null
        pendingFirmware.value = null
        viewModelScope.launch {
            try {
                // Distinguish failure (network/auth) from "endpoint returned null" — without this
                // every transport error would surface as the misleading "no location configured"
                // copy. fetchLocationId throws; we let that hit the catch below.
                val locationId = fetchLocationId()
                if (locationId == null) {
                    errorMessage.value = "No Stripe location configured for this store"
                    isConnecting.value = false
                    return@launch
                }
                val connected = readerDiscovery.connect(reader, locationId)
                connected.serialNumber?.let { readerPreferences.rememberReader(it) }
                StripeReaderService.start(context)
                stopDiscovery()
            } catch (e: TerminalException) {
                errorMessage.value = e.errorMessage
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Connect failed"
            } finally {
                isConnecting.value = false
            }
        }
    }

    fun forgetReader() {
        viewModelScope.launch {
            runCatching { readerDiscovery.disconnect() }
            readerPreferences.forgetReader()
            StripeReaderService.stop(context)
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    /**
     * Best-effort silent reconnect. We can't reconnect to a [Reader] object directly without
     * re-discovering — Stripe doesn't persist usable handles across processes — so we kick off
     * discovery if a serial is on file. Auto-connect logic in the screen layer can opt to call
     * [connect] when the matching serial reappears.
     */
    private suspend fun tryAutoReconnect() {
        val serial = readerPreferences.currentSerial() ?: return
        // Surface the saved serial via the dedicated StateFlow so the top-level combine picks it
        // up and preserves it across subsequent recompositions.
        rememberedSerial.value = serial
    }

    /**
     * Pulls a location id by minting (and immediately discarding) a connection token — that
     * endpoint already echoes the configured location. Avoids a second backend call just for the
     * location lookup. Lets exceptions propagate so the caller can distinguish transport failure
     * (caught as TerminalException / generic Exception in [connect]) from "endpoint returned null".
     */
    private suspend fun fetchLocationId(): String? {
        val envelope = api.stripeConnectionToken()
        return envelope.data?.locationId
    }
}

/** Internal bag for the inner combine — keeps the lambda's destructuring under arity limits. */
private data class LocalUi(
    val readers: List<Reader>,
    val error: String?,
    val firmware: String?,
    val serial: String?,
)

/** Snapshot rendered by the reader settings screen. Pure-data — no SDK callbacks leak through. */
data class ReaderUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED,
    val connectedReader: Reader? = null,
    val discoveredReaders: List<Reader> = emptyList(),
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val firmwareMessage: String? = null,
    val rememberedSerial: String? = null,
)

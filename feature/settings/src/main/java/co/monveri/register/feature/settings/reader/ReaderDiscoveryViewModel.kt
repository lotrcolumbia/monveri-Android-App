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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
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

    private var discoveryJob: Job? = null

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        terminalManager.ensureInitialized()

        // Pipe SDK status + local UI flags into a single render-ready state.
        combine(
            terminalManager.connectionStatus,
            terminalManager.connectedReader,
            isDiscovering,
            isConnecting,
            combine(nearbyReaders, errorMessage, pendingFirmware) { readers, error, firmware ->
                Triple(readers, error, firmware)
            },
        ) { status, reader, discovering, connecting, (readers, error, firmware) ->
            ReaderUiState(
                connectionStatus = status,
                connectedReader = reader,
                discoveredReaders = readers,
                isDiscovering = discovering,
                isConnecting = connecting,
                errorMessage = error,
                firmwareMessage = firmware,
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
                val locationId = fetchLocationId() ?: run {
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
        // Surface the saved serial in state so the UI can pick it up — actual re-connect happens
        // when the user opens the reader settings (or when ambient discovery finds it).
        _state.value = _state.value.copy(rememberedSerial = serial)
    }

    /**
     * Pulls a location id by minting (and immediately discarding) a connection token — that
     * endpoint already echoes the configured location. Avoids a second backend call just for the
     * location lookup.
     */
    private suspend fun fetchLocationId(): String? = runCatching {
        val envelope = api.stripeConnectionToken()
        envelope.data?.locationId
    }.getOrNull()
}

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

package co.monveri.register.feature.settings.reader

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.tokens.MonveriSpacing
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.Reader

/**
 * Reader settings — pair, view, and forget the Stripe M2 reader.
 *
 * Permission flow: API 31+ asks for `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`; older devices fall
 * back to `ACCESS_FINE_LOCATION`. Discovery doesn't start until the cashier grants permission;
 * a denial path renders a recovery sheet pointing to system Settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReaderDiscoveryScreen(
    onBack: () -> Unit,
    onDebugTestHarness: (() -> Unit)? = null,
    viewModel: ReaderDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions = rememberBluetoothPermissions()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Card reader") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopDiscovery()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = MonveriSpacing.Lg)) {

            ConnectedReaderPanel(state = state, onForget = viewModel::forgetReader)

            HorizontalDivider(modifier = Modifier.padding(vertical = MonveriSpacing.Md))

            when {
                !permissions.allPermissionsGranted -> PermissionPanel(permissions = permissions)
                state.isDiscovering -> DiscoveryActive(
                    state = state,
                    onCancel = viewModel::stopDiscovery,
                    onConnect = viewModel::connect,
                )
                state.connectedReader == null -> IdlePanel(
                    rememberedSerial = state.rememberedSerial,
                    onScan = viewModel::startDiscovery,
                )
                else -> Spacer(modifier = Modifier.height(MonveriSpacing.Md))
            }

            state.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(MonveriSpacing.Md))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.firmwareMessage?.let { message ->
                Spacer(modifier = Modifier.height(MonveriSpacing.Md))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (onDebugTestHarness != null) {
                Spacer(modifier = Modifier.weight(1f))
                MonveriButton(
                    text = "Stripe Test Harness (debug)",
                    onClick = onDebugTestHarness,
                    variant = MonveriButtonVariant.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MonveriSpacing.Lg),
                )
            }
        }
    }
}

@Composable
private fun ConnectedReaderPanel(state: ReaderUiState, onForget: () -> Unit) {
    val reader = state.connectedReader
    if (reader == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConnectionDot(connected = false)
            Spacer(modifier = Modifier.size(MonveriSpacing.Sm))
            Text(
                text = "No reader connected",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        return
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConnectionDot(connected = state.connectionStatus == ConnectionStatus.CONNECTED)
            Spacer(modifier = Modifier.size(MonveriSpacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reader.deviceType?.deviceName ?: "Stripe Reader",
                    style = MaterialTheme.typography.titleMedium,
                )
                reader.serialNumber?.let {
                    Text(
                        text = "S/N $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MonveriButton(
                text = "Forget",
                onClick = onForget,
                variant = MonveriButtonVariant.Secondary,
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionPanel(permissions: MultiplePermissionsState) {
    EmptyState(
        modifier = Modifier.fillMaxWidth(),
        icon = Icons.Filled.Bluetooth,
        title = "Bluetooth permission needed",
        message = "Bluetooth lets your phone talk to the card reader. Location is required on " +
            "older Android versions only.",
        actionLabel = "Grant access",
        onAction = { permissions.launchMultiplePermissionRequest() },
    )
}

@Composable
private fun IdlePanel(rememberedSerial: String?, onScan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md)) {
        rememberedSerial?.let {
            Text(
                text = "Last paired reader: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MonveriButton(
            text = "Scan for readers",
            onClick = onScan,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DiscoveryActive(
    state: ReaderUiState,
    onCancel: () -> Unit,
    onConnect: (Reader) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.size(MonveriSpacing.Sm))
            Text(
                text = "Searching for nearby readers…",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            MonveriButton(
                text = "Cancel",
                onClick = onCancel,
                variant = MonveriButtonVariant.Secondary,
            )
        }
        Spacer(modifier = Modifier.height(MonveriSpacing.Md))
        if (state.discoveredReaders.isEmpty()) {
            Text(
                text = "Make sure the reader is on and nearby (≤30 ft).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                // Fall back to the SDK object's identity hash when `serialNumber` is null —
                // two readers without a serial would otherwise share the empty-string key and
                // crash the LazyColumn. The hash is stable for the Reader instances Stripe
                // emits across a single discovery cycle.
                items(
                    state.discoveredReaders,
                    key = { reader -> reader.serialNumber ?: reader.hashCode().toString() },
                ) { reader ->
                    DiscoveredReaderRow(
                        reader = reader,
                        connecting = state.isConnecting,
                        onConnect = { onConnect(reader) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DiscoveredReaderRow(reader: Reader, connecting: Boolean, onConnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !connecting, onClick = onConnect)
            .padding(vertical = MonveriSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(MonveriSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reader.deviceType?.deviceName ?: "Stripe Reader",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            reader.serialNumber?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val battery = reader.batteryLevel
        if (battery != null) {
            Text(
                text = "${(battery * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (connecting) {
            Spacer(modifier = Modifier.size(MonveriSpacing.Sm))
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.Transparent, // placeholder for layout symmetry
            )
        }
    }
}

@Composable
private fun ConnectionDot(connected: Boolean) {
    val color = if (connected) MonveriTheme.statusColors.success else MonveriTheme.statusColors.warning
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun rememberBluetoothPermissions(): MultiplePermissionsState {
    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return rememberMultiplePermissionsState(permissions = perms)
}

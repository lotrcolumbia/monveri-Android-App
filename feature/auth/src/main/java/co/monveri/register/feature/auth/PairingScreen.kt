package co.monveri.register.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.design.components.ConfirmActionsRow
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.tokens.MonveriSpacing
import co.monveri.register.model.PairingPayload

/**
 * Two faces, one screen — mirrors the existing "one screen, N faces" pattern used elsewhere
 * (e.g. Phase 5's TapToPayScreen):
 *
 *  - **Form face**: primary "Scan QR Code" button (the expected path — see [onScanRequested]);
 *    a collapsible "Enter manually" section holds the store URL / API key fields as a fallback
 *    for no-camera or no-printer situations. Matches iOS's `PairingView`.
 *  - **Confirm face**: once [AuthViewModel.onQrScanned] parses a valid QR into
 *    [PairingUiState.pendingPayload], swaps to a summary card (store name, URL, key label,
 *    register, masked key) so the cashier can verify before tapping "Pair this device" — mirrors
 *    iOS's `PairingConfirmView`.
 *
 * [pendingScannedQr] is the raw string handed back from [PairingScannerScreen] via the nav
 * graph's SavedStateHandle handoff (same pattern the catalog barcode scanner uses); consuming it
 * feeds [AuthViewModel.onQrScanned] and clears it via [onScannedQrConsumed] so a configuration
 * change doesn't replay the scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    onScanRequested: () -> Unit,
    pendingScannedQr: String? = null,
    onScannedQrConsumed: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.pairing.collectAsStateWithLifecycle()
    var apiKeyVisible by remember { mutableStateOf(false) }
    var manualEntryExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.pairedStoreName) {
        if (state.pairedStoreName != null) onPaired()
    }

    LaunchedEffect(pendingScannedQr) {
        if (pendingScannedQr != null) {
            viewModel.onQrScanned(pendingScannedQr)
            onScannedQrConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pair this device") })
        },
    ) { innerPadding ->
        val payload = state.pendingPayload
        if (payload != null) {
            PairingConfirmContent(
                payload = payload,
                isLoading = state.isLoading,
                errorMessage = state.errorMessage,
                onConfirm = viewModel::pair,
                onCancel = viewModel::dismissPendingPayload,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            PairingFormContent(
                state = state,
                apiKeyVisible = apiKeyVisible,
                onApiKeyVisibilityToggled = { apiKeyVisible = !apiKeyVisible },
                manualEntryExpanded = manualEntryExpanded,
                onManualEntryToggled = { manualEntryExpanded = !manualEntryExpanded },
                onScanRequested = onScanRequested,
                onBaseUrlChanged = viewModel::onPairingBaseUrlChanged,
                onApiKeyChanged = viewModel::onPairingApiKeyChanged,
                onPair = viewModel::pair,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun PairingFormContent(
    state: PairingUiState,
    apiKeyVisible: Boolean,
    onApiKeyVisibilityToggled: () -> Unit,
    manualEntryExpanded: Boolean,
    onManualEntryToggled: () -> Unit,
    onScanRequested: () -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onPair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MonveriSpacing.Xl, vertical = MonveriSpacing.Xl)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        Text(
            text = "Connect Monveri Register to your store.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MonveriButton(
            text = "Scan QR Code",
            onClick = onScanRequested,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Open the suite admin panel → Register API Keys → Show QR for the key you want to use.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!manualEntryExpanded && state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        TextButton(onClick = onManualEntryToggled) {
            Icon(
                imageVector = if (manualEntryExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = null,
            )
            Text("Enter pairing details manually")
        }

        AnimatedVisibility(visible = manualEntryExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg)) {
                MonveriTextField(
                    value = state.baseUrl,
                    onValueChange = onBaseUrlChanged,
                    label = "Store URL",
                    placeholder = "https://store.example",
                    keyboardType = KeyboardType.Uri,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                MonveriTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChanged,
                    label = "API key",
                    placeholder = "paste from register_api_keys table",
                    keyboardType = KeyboardType.Password,
                    enabled = !state.isLoading,
                    visualTransformation = if (apiKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onApiKeyVisibilityToggled) {
                            Icon(
                                imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key",
                            )
                        }
                    },
                    errorMessage = state.errorMessage,
                    modifier = Modifier.fillMaxWidth(),
                )

                MonveriButton(
                    text = if (state.isLoading) "Pairing…" else "Pair this device",
                    onClick = onPair,
                    loading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PairingConfirmContent(
    payload: PairingPayload,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MonveriSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text("Confirm pairing", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Verify this is the store you want to pair to.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MonveriSpacing.Md),
            colors = CardDefaults.cardColors(),
        ) {
            Column(
                modifier = Modifier.padding(MonveriSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
            ) {
                ConfirmField(label = "Store", value = payload.displayName)
                HorizontalDivider()
                ConfirmField(label = "URL", value = payload.storeUrl)
                val keyLabel = payload.keyLabel
                if (!keyLabel.isNullOrEmpty()) {
                    HorizontalDivider()
                    ConfirmField(label = "Key name", value = keyLabel)
                }
                if (payload.registerLabel.isNotEmpty()) {
                    HorizontalDivider()
                    ConfirmField(label = "Register", value = payload.registerLabel)
                }
                HorizontalDivider()
                ConfirmField(label = "Key", value = maskedKey(payload.apiKey))
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        ConfirmActionsRow(
            confirmLabel = if (isLoading) "Pairing…" else "Pair this device",
            onConfirm = onConfirm,
            onCancel = onCancel,
            loading = isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConfirmField(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** First 4 + last 4 chars, middle masked — a sanity check, not a secret reveal. */
private fun maskedKey(key: String): String {
    if (key.length <= 10) return "•".repeat(key.length)
    return "${key.take(4)}••••••••••••${key.takeLast(4)}"
}

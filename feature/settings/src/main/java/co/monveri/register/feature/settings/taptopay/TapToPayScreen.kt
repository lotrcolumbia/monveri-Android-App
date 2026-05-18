package co.monveri.register.feature.settings.taptopay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.tokens.MonveriSpacing
import co.monveri.register.payments.TapToPayReadiness
import com.stripe.stripeterminal.external.models.ConnectionStatus

/**
 * Tap to Pay on Android — phone-as-reader. Three faces of one screen:
 *
 *  - **Unsupported device** → diagnostics panel listing which readiness signal failed, with an
 *    actionable hint (turn on NFC) where one exists. Tap to Pay is *hidden* entirely once Phase 6
 *    owns the reader picker; here it stays visible so the cashier can see *why* it's unavailable.
 *  - **Ready, not connected** → one-tap "Set up Tap to Pay" (no pairing, the phone *is* the reader).
 *  - **Connected** → full-screen tap surface with a pulsing contactless animation that runs while
 *    a charge is collecting, plus a $1.00 test charge to validate the path on real hardware.
 *
 * The tap surface deliberately reuses the shared `PaymentSession` state machine, so Phase 6 can
 * lift this surface into the checkout flow unchanged — only the amount source differs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapToPayScreen(
    onBack: () -> Unit,
    viewModel: TapToPayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tap to Pay") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = MonveriSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
        ) {
            when {
                !state.readiness.isReady -> DiagnosticsPanel(
                    readiness = state.readiness,
                    onRecheck = viewModel::refreshReadiness,
                )

                state.connectionStatus != ConnectionStatus.CONNECTED -> SetupPanel(
                    isConnecting = state.isConnecting,
                    canConnect = state.canConnect,
                    onConnect = viewModel::connect,
                )

                else -> TapSurface(
                    statusLine = state.statusLine,
                    isCharging = state.isCharging,
                    canCharge = state.canCharge,
                    lastResult = state.lastResult,
                    onCharge = viewModel::charge,
                    onCancel = viewModel::cancel,
                    onDisconnect = viewModel::disconnect,
                )
            }

            state.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(MonveriSpacing.Sm))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsPanel(readiness: TapToPayReadiness, onRecheck: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md)) {
        EmptyState(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Contactless,
            title = "Tap to Pay isn't available",
            message = "This device doesn't meet every requirement for accepting taps. " +
                "Use the Stripe Reader M2 instead, or fix the items below.",
        )
        HorizontalDivider()
        SignalRow("Android 11 or newer", readiness.osVersionOk)
        SignalRow("NFC hardware present", readiness.hasNfcHardware)
        SignalRow(
            label = if (readiness.nfcEnabled) "NFC is on" else "NFC is off — turn it on in Settings",
            ok = readiness.nfcEnabled,
        )
        SignalRow(
            label = when (readiness.stripeSupported) {
                true -> "Stripe supports this device"
                false -> "Stripe doesn't support this device"
                null -> "Checking Stripe support…"
            },
            ok = readiness.stripeSupported == true,
            pending = readiness.stripeSupported == null,
        )
        Spacer(modifier = Modifier.height(MonveriSpacing.Sm))
        MonveriButton(
            text = "Re-check",
            onClick = onRecheck,
            variant = MonveriButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SignalRow(label: String, ok: Boolean, pending: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            // 24.dp matches the default Material icon size below so the label column
            // doesn't shift horizontally between pending and resolved rows.
            pending -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
            ok -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MonveriTheme.statusColors.success,
            )
            else -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.size(MonveriSpacing.Sm))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SetupPanel(isConnecting: Boolean, canConnect: Boolean, onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Contactless,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(MonveriSpacing.Lg))
        Text(
            text = "Accept contactless cards right on this phone — no separate reader needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = MonveriSpacing.Lg),
        )
        Spacer(modifier = Modifier.height(MonveriSpacing.Xl))
        MonveriButton(
            text = "Set up Tap to Pay",
            onClick = onConnect,
            enabled = canConnect,
            loading = isConnecting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TapSurface(
    statusLine: String,
    isCharging: Boolean,
    canCharge: Boolean,
    lastResult: TestChargeResult?,
    onCharge: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PulsingContactless(active = isCharging)
        Spacer(modifier = Modifier.height(MonveriSpacing.Xl))
        Text(
            text = statusLine,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        lastResult?.let { result ->
            Spacer(modifier = Modifier.height(MonveriSpacing.Md))
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(MonveriSpacing.Xxl))
        MonveriButton(
            text = "Charge $1.00 test",
            onClick = onCharge,
            enabled = canCharge,
            loading = isCharging,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MonveriSpacing.Sm))
        MonveriButton(
            text = "Cancel",
            onClick = onCancel,
            variant = MonveriButtonVariant.Secondary,
            enabled = isCharging,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MonveriSpacing.Sm))
        MonveriButton(
            text = "Disconnect",
            onClick = onDisconnect,
            variant = MonveriButtonVariant.Secondary,
            enabled = !isCharging,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The card-tap affordance: a contactless glyph that gently pulses while a charge is collecting,
 * sitting still otherwise. Scale-only animation (no recomposition churn) keeps it cheap.
 */
@Composable
private fun PulsingContactless(active: Boolean) {
    // Only spin up the infinite-transition clock while collecting. When inactive the glyph is a
    // static 1f — no per-frame animation work for a surface that's just sitting idle.
    val scale = if (active) {
        val transition = rememberInfiniteTransition(label = "tap-pulse")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "tap-pulse-scale",
        )
        animated
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Contactless,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
    }
}

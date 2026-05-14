package co.monveri.register.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.tokens.MonveriSpacing
import com.stripe.stripeterminal.external.models.ConnectionStatus
import kotlinx.coroutines.launch

/**
 * Debug-variant only. Runs a $0.50 test PaymentIntent against the connected Stripe reader so we
 * can validate the end-to-end SDK plumbing on real hardware without touching the cart.
 *
 * Lives in `app/src/debug/` so the release APK doesn't ship test-charge entry points.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StripeTestHarnessScreen(
    onBack: () -> Unit,
    viewModel: StripeTestHarnessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stripe Test Harness") },
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
                .padding(MonveriSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Charge a $0.50 test transaction against the connected Stripe reader.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = readerStatusLine(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.statusLine,
                style = MaterialTheme.typography.titleMedium,
            )
            state.lastResult?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.lastResultIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            MonveriButton(
                text = "Charge $0.50",
                onClick = { scope.launch { viewModel.charge() } },
                enabled = state.canCharge,
                loading = state.isCharging,
                modifier = Modifier.fillMaxWidth(),
            )
            MonveriButton(
                text = "Cancel",
                onClick = viewModel::cancel,
                variant = MonveriButtonVariant.Secondary,
                enabled = state.isCharging,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun readerStatusLine(state: StripeTestHarnessUiState): String = when (state.connectionStatus) {
    ConnectionStatus.NOT_CONNECTED -> "Reader: not connected — pair one in Settings → Card reader"
    ConnectionStatus.CONNECTING -> "Reader: connecting…"
    ConnectionStatus.CONNECTED -> "Reader: connected (${state.readerSerial ?: "—"})"
    else -> "Reader: ${state.connectionStatus.name.lowercase()}"
}

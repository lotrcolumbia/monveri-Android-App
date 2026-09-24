package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.CashCount
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing
import kotlin.math.abs

/**
 * Till reconciliation — mirrors iOS's `CloseRegisterView`: count-entry face (expected cash shown
 * up front so counting isn't blind) → submit → variance-report face.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseRegisterScreen(
    onBack: () -> Unit,
    onOpenNewRegister: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: CloseRegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Close register") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            when {
                state.isLoadingPreview -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.report != null -> ResultReport(
                    state = state,
                    onOpenNewRegister = onOpenNewRegister,
                    onLogout = {
                        viewModel.logout()
                        onLoggedOut()
                    },
                )
                else -> CountEntry(
                    state = state,
                    onCashCountChange = viewModel::updateCashCount,
                    onNotesChanged = viewModel::onNotesChanged,
                    onSubmit = viewModel::submitClose,
                )
            }
        }
    }
}

@Composable
private fun CountEntry(
    state: CloseRegisterUiState,
    onCashCountChange: ((CashCount) -> CashCount) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        state.expectedCashCents?.let { expected ->
            Column {
                Text(
                    text = "EXPECTED CASH",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                MoneyText(cents = expected, style = MaterialTheme.typography.headlineSmall)
            }
        }

        Text(
            text = "Count the cash in the drawer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BillsSection(state.cashCount, onCashCountChange)
        LooseCoinsSection(state.cashCount, onCashCountChange)
        CoinRollsSection(state.cashCount, onCashCountChange)
        BillStrapsSection(state.cashCount, onCashCountChange)

        CashCounterSummary(cashCount = state.cashCount, expectedCashCents = state.expectedCashCents)

        MonveriTextField(
            value = state.notes,
            onValueChange = onNotesChanged,
            label = "Notes (optional)",
            placeholder = "e.g. Short $2 — customer walked without change",
            modifier = Modifier.fillMaxWidth(),
        )

        state.errorMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        MonveriButton(
            text = if (state.isSubmitting) "Closing…" else "Close register",
            onClick = onSubmit,
            loading = state.isSubmitting,
            enabled = state.sessionId != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultReport(
    state: CloseRegisterUiState,
    onOpenNewRegister: () -> Unit,
    onLogout: () -> Unit,
) {
    val report = state.report ?: return
    val variance = state.varianceCents ?: 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = varianceLabel(variance),
                style = MaterialTheme.typography.headlineSmall,
                color = varianceColor(variance),
            )
            if (variance != 0L) {
                CompositionLocalProvider(LocalContentColor provides varianceColor(variance)) {
                    MoneyText(
                        cents = abs(variance),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }

        HorizontalDivider()

        ReportRow("Opening cash", report.openingCashCents)
        ReportRow("Cash sales", report.cashTotalCents)
        ReportRow("Card sales", report.creditTotalCents)
        ReportRow("Refunds", report.refundTotalCents)
        ReportRow("Expected cash", report.expectedCashCents)
        ReportRow("Counted cash", state.countedCents)
        HorizontalDivider()
        ReportRow("Total sales", report.totalSalesCents)
        ReportCountRow("Transactions", report.totalTransactions)

        MonveriButton(
            text = "Open new register",
            onClick = onOpenNewRegister,
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriButton(
            text = "Log out",
            onClick = onLogout,
            variant = MonveriButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportRow(label: String, cents: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        MoneyText(cents = cents, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReportCountRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(count.toString(), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun varianceLabel(varianceCents: Long): String = when {
    varianceCents == 0L -> "Drawer matches exactly"
    varianceCents > 0L -> "Over by"
    else -> "Short by"
}

@Composable
private fun varianceColor(varianceCents: Long) = when {
    varianceCents == 0L -> MaterialTheme.colorScheme.primary
    varianceCents > 0L -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}

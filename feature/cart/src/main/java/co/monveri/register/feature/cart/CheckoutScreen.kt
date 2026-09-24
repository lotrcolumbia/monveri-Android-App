package co.monveri.register.feature.cart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Owns the whole checkout flow as one screen with an internal step switch — matches iOS's
 * `CheckoutView`, which is a single sheet, not a push per tender. See [CheckoutViewModel]'s doc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onBackToCart: () -> Unit,
    onSaleComplete: () -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Matches iOS's `interactiveDismissDisabled` — a cashier can't swipe/back away mid-charge or
    // after a sale has already recorded; everywhere else, back returns to the tender picker.
    val canDismiss = state.step != CheckoutStep.SUBMITTING && state.step != CheckoutStep.SUCCEEDED
    BackHandler(enabled = canDismiss && state.step != CheckoutStep.TENDER_PICKER) {
        viewModel.backToTenderPicker()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.step.title()) },
                navigationIcon = {
                    if (state.step == CheckoutStep.TENDER_PICKER) {
                        IconButton(onClick = onBackToCart) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to cart")
                        }
                    } else if (canDismiss) {
                        IconButton(onClick = viewModel::backToTenderPicker) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.step) {
                CheckoutStep.TENDER_PICKER -> TenderPickerContent(state, viewModel)
                CheckoutStep.CASH -> CashTenderContent(state, viewModel)
                CheckoutStep.CARD -> CardTenderContent(state, viewModel)
                CheckoutStep.OTHER -> OtherTenderContent(viewModel)
                CheckoutStep.SPLIT -> SplitTenderContent(state, viewModel)
                CheckoutStep.SUBMITTING -> SubmittingContent()
                CheckoutStep.SUCCEEDED -> ReceiptSuccessContent(state, viewModel, onSaleComplete)
                CheckoutStep.FAILED -> FailedContent(state, viewModel)
            }
        }
    }
}

private fun CheckoutStep.title(): String = when (this) {
    CheckoutStep.TENDER_PICKER -> "Checkout"
    CheckoutStep.CASH -> "Cash"
    CheckoutStep.CARD -> "Card"
    CheckoutStep.OTHER -> "Other"
    CheckoutStep.SPLIT -> "Split Tender"
    CheckoutStep.SUBMITTING -> "Recording Sale…"
    CheckoutStep.SUCCEEDED -> "Sale Recorded"
    CheckoutStep.FAILED -> "Checkout"
}

@Composable
private fun TenderPickerContent(state: CheckoutUiState, viewModel: CheckoutViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Total due", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(cents = state.totalCents, style = MaterialTheme.typography.displaySmall)
        }

        MonveriButton(
            text = "Cash",
            onClick = { viewModel.selectTender(CheckoutStep.CASH) },
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriButton(
            text = if (state.readerConnected) "Card" else "Card (no reader connected)",
            onClick = { viewModel.selectTender(CheckoutStep.CARD) },
            enabled = state.readerConnected,
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriButton(
            text = "Other",
            onClick = { viewModel.selectTender(CheckoutStep.OTHER) },
            variant = MonveriButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriButton(
            text = "Split Tender",
            onClick = { viewModel.selectTender(CheckoutStep.SPLIT) },
            variant = MonveriButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SubmittingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md)) {
            CircularProgressIndicator()
            Text("Recording sale…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FailedContent(state: CheckoutUiState, viewModel: CheckoutViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MonveriTheme.statusColors.danger)
        Text("Couldn't record this sale", style = MaterialTheme.typography.titleLarge)
        state.errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.cardAuthorizedIntentId?.let { intentId ->
            Text(
                "Card was already authorized — PaymentIntent: $intentId. The charge went through even " +
                    "though the ticket didn't record; retry submit rather than charging the card again.",
                style = MaterialTheme.typography.bodySmall,
                color = MonveriTheme.statusColors.warning,
            )
        }
        MonveriButton(text = "Retry", onClick = viewModel::retrySubmit, modifier = Modifier.fillMaxWidth())
        MonveriButton(
            text = "Back to Tender Picker",
            onClick = viewModel::backToTenderPicker,
            variant = MonveriButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

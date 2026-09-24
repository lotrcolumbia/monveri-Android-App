package co.monveri.register.feature.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing
import co.monveri.register.payments.PaymentSessionState

/** Card tender — a thin view over [PaymentSession]'s state machine. Mirrors iOS's `CardPaymentBridgeView`. */
@Composable
internal fun CardTenderContent(state: CheckoutUiState, viewModel: CheckoutViewModel) {
    LaunchedEffect(Unit) {
        val fresh = state.cardSessionState is PaymentSessionState.Idle ||
            state.cardSessionState is PaymentSessionState.Canceled
        if (fresh) viewModel.beginCardPayment()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoneyText(cents = state.totalCents, style = MaterialTheme.typography.displaySmall)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val session = state.cardSessionState) {
                PaymentSessionState.Idle, PaymentSessionState.CreatingIntent ->
                    StatusColumn(spinner = true, message = "Starting…")
                PaymentSessionState.AwaitingCard ->
                    StatusColumn(icon = Icons.Filled.Nfc, message = "Tap, insert, or swipe card")
                PaymentSessionState.Processing ->
                    StatusColumn(spinner = true, message = "Processing…")
                is PaymentSessionState.Succeeded ->
                    StatusColumn(icon = Icons.Filled.CheckCircle, message = "Approved", tint = MonveriTheme.statusColors.success)
                is PaymentSessionState.Declined -> DeclinedColumn(session.message, viewModel)
                is PaymentSessionState.Failed -> FailedCardColumn(session.message, viewModel)
                PaymentSessionState.Canceled ->
                    StatusColumn(icon = Icons.Filled.Error, message = "Canceled")
            }
        }

        val cancelable = state.cardSessionState is PaymentSessionState.AwaitingCard ||
            state.cardSessionState is PaymentSessionState.Processing
        if (cancelable) {
            MonveriButton(
                text = "Cancel",
                onClick = viewModel::cancelCardPayment,
                variant = MonveriButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatusColumn(
    message: String,
    spinner: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md)) {
        if (spinner) {
            CircularProgressIndicator(modifier = Modifier.size(ICON_SIZE))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(ICON_SIZE))
        }
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DeclinedColumn(message: String, viewModel: CheckoutViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md)) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = MonveriTheme.statusColors.danger, modifier = Modifier.size(ICON_SIZE))
        Text("Declined", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MonveriButton(text = "Try a Different Card", onClick = viewModel::retryCardPayment)
    }
}

@Composable
private fun FailedCardColumn(message: String, viewModel: CheckoutViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md)) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = MonveriTheme.statusColors.danger, modifier = Modifier.size(ICON_SIZE))
        Text("Payment Failed", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MonveriButton(text = "Retry", onClick = viewModel::retryCardPayment)
    }
}

private val ICON_SIZE = 64.dp

package co.monveri.register.feature.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing

/** Post-sale confirmation — total, change-due (cash only), email receipt, done. Matches iOS's
 * `ReceiptSuccessView` minus print (no AirPrint equivalent wired up in this slice). */
@Composable
internal fun ReceiptSuccessContent(state: CheckoutUiState, viewModel: CheckoutViewModel, onDone: () -> Unit) {
    var showEmailDialog by remember { mutableStateOf(false) }
    val cashOutcome = state.lastOutcome as? PaymentOutcome.Cash

    Column(
        modifier = Modifier.fillMaxSize().padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MonveriTheme.statusColors.success,
            modifier = Modifier.size(72.dp),
        )
        Text("Sale Recorded", style = MaterialTheme.typography.headlineSmall)
        Text(
            state.ticketToken?.takeIf { it.isNotBlank() } ?: "Ticket #${state.ticketId}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyText(cents = state.totalCents, style = MaterialTheme.typography.displaySmall)

        if (cashOutcome != null && cashOutcome.changeCents > 0) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MonveriSpacing.Md),
            ) {
                Text("CHANGE DUE", style = MaterialTheme.typography.labelLarge, color = MonveriTheme.statusColors.success)
                CompositionLocalProvider(LocalContentColor provides MonveriTheme.statusColors.success) {
                    MoneyText(
                        cents = cashOutcome.changeCents,
                        style = MaterialTheme.typography.displayLarge,
                    )
                }
            }
        }

        state.emailReceiptResult?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        MonveriButton(
            text = "Email Receipt",
            onClick = { showEmailDialog = true },
            variant = MonveriButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriButton(
            text = "Done",
            onClick = { viewModel.finish(); onDone() },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showEmailDialog) {
        EmailReceiptDialog(
            isSending = state.isEmailingReceipt,
            onSend = { email ->
                viewModel.emailReceipt(email)
                showEmailDialog = false
            },
            onDismiss = { showEmailDialog = false },
        )
    }
}

@Composable
private fun EmailReceiptDialog(isSending: Boolean, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    val isValid = email.contains("@") && email.contains(".") && email.length >= MIN_EMAIL_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Email Receipt") },
        text = {
            MonveriTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email address",
                keyboardType = KeyboardType.Email,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSend(email) }, enabled = isValid && !isSending) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val MIN_EMAIL_LENGTH = 5

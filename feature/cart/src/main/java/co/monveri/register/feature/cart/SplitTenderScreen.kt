package co.monveri.register.feature.cart

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing
import java.math.BigDecimal
import java.math.RoundingMode

private val SPLIT_METHODS = listOf("Cash", "Card", "Other")

/**
 * Open-ended split tender — an accumulating list of applied payments plus a running remaining
 * balance, matching Phase 6's plan (no hard cap on the number of legs). Each payment is committed
 * the moment it's collected; "Complete Split" only becomes available once nothing is left owing.
 */
@Composable
internal fun SplitTenderContent(state: CheckoutUiState, viewModel: CheckoutViewModel) {
    val remaining = state.splitRemainingCents()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Total", style = MaterialTheme.typography.titleMedium)
            MoneyText(cents = state.totalCents, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()

        state.splitPayments.forEach { payment -> AppliedPaymentRow(payment) }

        if (remaining > 0L) {
            CompositionLocalProvider(LocalContentColor provides MonveriTheme.statusColors.warning) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Remaining", style = MaterialTheme.typography.titleMedium)
                    MoneyText(cents = remaining, style = MaterialTheme.typography.titleMedium)
                }
            }
            HorizontalDivider()
            AddPaymentSection(state = state, remaining = remaining, viewModel = viewModel)
        } else {
            MonveriButton(text = "Complete Split", onClick = viewModel::confirmSplit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AppliedPaymentRow(payment: AppliedSplitPayment) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(payment.outcome.paymentMethod, style = MaterialTheme.typography.bodyLarge)
        MoneyText(cents = payment.amountCents, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AddPaymentSection(state: CheckoutUiState, remaining: Long, viewModel: CheckoutViewModel) {
    var method by remember { mutableStateOf("Cash") }
    var methodExpanded by remember { mutableStateOf(false) }
    var otherService by remember { mutableStateOf(OtherTenderService.CASH_APP) }
    var otherReference by remember { mutableStateOf("") }
    val amountCents = state.splitNextAmountCents()

    Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
        Text("Add Payment", style = MaterialTheme.typography.titleSmall)

        Box {
            OutlinedButton(onClick = { methodExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(method, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                SPLIT_METHODS.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { method = option; methodExpanded = false })
                }
            }
        }

        MonveriTextField(
            value = state.splitAmountText,
            onValueChange = { text -> viewModel.updateState { it.copy(splitAmountText = text) } },
            label = "Amount for this payment",
            placeholder = dollarsText(remaining),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )

        if (method == "Other") {
            OtherServicePicker(selected = otherService, onSelect = { otherService = it })
            MonveriTextField(
                value = otherReference,
                onValueChange = { otherReference = it },
                label = "Reference (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (method == "Card" && state.isChargingSplitCard) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
                CircularProgressIndicator(modifier = Modifier.padding(MonveriSpacing.Xs))
                Text("Charging…")
            }
        } else {
            MonveriButton(
                text = if (method == "Card") "Charge This Payment" else "Confirm Payment",
                enabled = amountCents in 1..remaining,
                onClick = {
                    when (method) {
                        "Cash" -> viewModel.confirmSplitCash(amountCents)
                        "Card" -> viewModel.beginSplitCard(amountCents)
                        else -> viewModel.confirmSplitOther(otherService.label, otherReference, amountCents)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OtherServicePicker(selected: OtherTenderService, onSelect: (OtherTenderService) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OtherTenderService.entries.forEach { service ->
                DropdownMenuItem(text = { Text(service.label) }, onClick = { onSelect(service); expanded = false })
            }
        }
    }
}

private fun dollarsText(cents: Long): String =
    BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Two-leg split tender — leg A's amount is entered by hand (must be less than the total), leg B
 * is always the remainder. Matches iOS's `SplitPaymentView` (Venmo is intentionally excluded from
 * split legs on iOS too).
 */
@Composable
internal fun SplitTenderContent(state: CheckoutUiState, viewModel: CheckoutViewModel) {
    var legAMethod by remember { mutableStateOf("Cash") }
    var legBMethod by remember { mutableStateOf("Card") }
    var legAOtherRef by remember { mutableStateOf("") }
    var legBOtherRef by remember { mutableStateOf("") }

    val legAAmountCents = state.splitLegAAmountCents(state.totalCents)
    val legBAmountCents = state.totalCents - legAAmountCents
    val legALocked = state.splitLegAState !is LegState.Pending
    val legBLocked = state.splitLegBState !is LegState.Pending

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Total", style = MaterialTheme.typography.titleMedium)
            MoneyText(cents = state.totalCents, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()

        SplitLegCard(
            title = "Leg 1",
            method = legAMethod,
            onMethodSelected = { legAMethod = it },
            amountCents = legAAmountCents,
            amountEditable = !legALocked,
            onAmountChanged = { text ->
                viewModel.updateState { s -> s.copy(splitLegAAmountText = dollarsTextToCentsText(text)) }
            },
            reference = legAOtherRef,
            onReferenceChanged = { legAOtherRef = it },
            legState = state.splitLegAState,
            enabled = legAAmountCents > 0 && legAAmountCents < state.totalCents,
            onConfirm = {
                when (legAMethod) {
                    "Card" -> viewModel.beginSplitLegCard(0, legAAmountCents)
                    "Cash" -> viewModel.confirmSplitLeg(0, PaymentOutcome.Cash(legAAmountCents, 0L, legAAmountCents))
                    else -> viewModel.confirmSplitLeg(0, PaymentOutcome.Other("Other", legAOtherRef.takeIf { it.isNotBlank() }))
                }
            },
        )

        if (legALocked) {
            SplitLegCard(
                title = "Leg 2 (remainder)",
                method = legBMethod,
                onMethodSelected = { legBMethod = it },
                amountCents = legBAmountCents,
                amountEditable = false,
                onAmountChanged = {},
                reference = legBOtherRef,
                onReferenceChanged = { legBOtherRef = it },
                legState = state.splitLegBState,
                enabled = legBAmountCents > 0,
                onConfirm = {
                    when (legBMethod) {
                        "Card" -> viewModel.beginSplitLegCard(1, legBAmountCents)
                        "Cash" -> viewModel.confirmSplitLeg(1, PaymentOutcome.Cash(legBAmountCents, 0L, legBAmountCents))
                        else -> viewModel.confirmSplitLeg(1, PaymentOutcome.Other("Other", legBOtherRef.takeIf { it.isNotBlank() }))
                    }
                },
            )
        }

        if (legALocked && legBLocked) {
            MonveriButton(text = "Complete Split", onClick = viewModel::confirmSplit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun SplitLegCard(
    title: String,
    method: String,
    onMethodSelected: (String) -> Unit,
    amountCents: Long,
    amountEditable: Boolean,
    onAmountChanged: (String) -> Unit,
    reference: String,
    onReferenceChanged: (String) -> Unit,
    legState: LegState,
    enabled: Boolean,
    onConfirm: () -> Unit,
) {
    var methodExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)

        Box {
            OutlinedButton(
                onClick = { if (amountEditable) methodExpanded = true },
                enabled = amountEditable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(method, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                SPLIT_METHODS.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onMethodSelected(option); methodExpanded = false })
                }
            }
        }

        if (amountEditable) {
            MonveriTextField(
                value = if (amountCents == 0L) "" else dollarsText(amountCents),
                onValueChange = onAmountChanged,
                label = "Amount for this leg",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Amount")
                MoneyText(cents = amountCents)
            }
        }

        if (method == "Other") {
            MonveriTextField(
                value = reference,
                onValueChange = onReferenceChanged,
                label = "Reference (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (legState) {
            LegState.Pending -> MonveriButton(
                text = if (method == "Card") "Charge This Leg" else "Confirm Leg",
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            LegState.Charging -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
                CircularProgressIndicator(modifier = Modifier.padding(MonveriSpacing.Xs))
                Text("Charging…")
            }
            is LegState.Done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MonveriTheme.statusColors.success)
                Text("Confirmed")
            }
        }
        HorizontalDivider()
    }
}

private fun dollarsText(cents: Long): String =
    BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

private fun dollarsTextToCentsText(text: String): String {
    val value = text.toDoubleOrNull() ?: return "0"
    return BigDecimal(value.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString()
}

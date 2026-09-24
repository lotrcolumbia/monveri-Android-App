package co.monveri.register.feature.cart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Cash tender — cents-shift keypad entry, rounded-to-nickel total (matches [CashRounding]),
 * quick-pick chips, live change-due. Mirrors iOS's `CashPaymentView`, including its Cash
 * Due/Tendered/Change Due card styling.
 */
@Composable
internal fun CashTenderContent(state: CheckoutUiState, viewModel: CheckoutViewModel) {
    val roundedTotal = remember(state.totalCents) { CashRounding.roundedTotalCents(state.totalCents) }
    var tenderedCents by remember { mutableLongStateOf(roundedTotal) }
    val changeDue = (tenderedCents - roundedTotal).coerceAtLeast(0L)
    val quickPicks = remember(roundedTotal) { quickPickValues(roundedTotal) }
    val roundingAdjustment = state.totalCents - roundedTotal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
    ) {
        CashDueCard(subtotalCents = state.totalCents, roundedTotal = roundedTotal, roundingAdjustment = roundingAdjustment)
        TenderedCard(tenderedCents = tenderedCents)
        if (changeDue > 0) {
            ChangeDueCard(changeCents = changeDue)
        }

        QuickPickRow(quickPicks = quickPicks, onPick = { tenderedCents = it })

        CashKeypad(
            onDigit = { digit -> tenderedCents = (tenderedCents * DIGIT_SHIFT + digit.digitToInt()).coerceAtMost(MAX_CENTS) },
            onBackspace = { tenderedCents /= DIGIT_SHIFT },
            onClear = { tenderedCents = 0L },
        )

        MonveriButton(
            text = if (tenderedCents < roundedTotal) "Need ${formatShortfall(roundedTotal - tenderedCents)} more" else "Confirm Cash",
            onClick = { viewModel.confirmCash(tenderedCents) },
            enabled = tenderedCents >= roundedTotal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CashDueCard(subtotalCents: Long, roundedTotal: Long, roundingAdjustment: Long) {
    Card(
        shape = RoundedCornerShape(cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = CARD_BORDER_ALPHA)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(MonveriSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Xs),
        ) {
            if (roundingAdjustment != 0L) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Subtotal Due", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(
                        cents = subtotalCents,
                        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough),
                    )
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (roundingAdjustment > 0) "Cash Rounding" else "Cash Rounding (up)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CompositionLocalProvider(LocalContentColor provides MonveriTheme.statusColors.success) {
                        MoneyText(cents = -roundingAdjustment, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
            }
            Text(
                text = if (roundingAdjustment != 0L) "Cash Due" else "Amount Due",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
            MoneyText(
                cents = roundedTotal,
                style = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.End),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TenderedCard(tenderedCents: Long) {
    Card(
        shape = RoundedCornerShape(tenderedCardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MonveriSpacing.Lg),
        ) {
            Text(
                "TENDERED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                MoneyText(cents = tenderedCents, style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}

@Composable
private fun ChangeDueCard(changeCents: Long) {
    Card(
        shape = RoundedCornerShape(cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MonveriTheme.statusColors.success.copy(alpha = CHANGE_CARD_TINT_ALPHA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MonveriSpacing.Md),
        ) {
            Text(
                "CHANGE DUE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonveriTheme.statusColors.success,
            )
            CompositionLocalProvider(LocalContentColor provides MonveriTheme.statusColors.success) {
                MoneyText(cents = changeCents, style = MaterialTheme.typography.displaySmall)
            }
        }
    }
}

@Composable
private fun QuickPickRow(quickPicks: List<Long>, onPick: (Long) -> Unit) {
    val colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.secondary,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm), modifier = Modifier.fillMaxWidth()) {
        quickPicks.forEach { amount ->
            Button(
                onClick = { onPick(amount) },
                colors = colors,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                contentPadding = PaddingValues(horizontal = MonveriSpacing.Xs, vertical = MonveriSpacing.Sm),
                modifier = Modifier.weight(1f),
            ) {
                MoneyText(cents = amount, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CashKeypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    val colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.secondary,
    )
    val elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm), modifier = Modifier.fillMaxWidth()) {
                row.forEach { digit ->
                    Button(
                        onClick = { onDigit(digit) },
                        colors = colors,
                        elevation = elevation,
                        modifier = Modifier.weight(1f).aspectRatio(KEYPAD_ASPECT),
                    ) {
                        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onClear, colors = colors, elevation = elevation, modifier = Modifier.weight(1f).aspectRatio(KEYPAD_ASPECT)) {
                Text("Clear")
            }
            Button(
                onClick = { onDigit('0') },
                colors = colors,
                elevation = elevation,
                modifier = Modifier.weight(1f).aspectRatio(KEYPAD_ASPECT),
            ) {
                Text("0", style = MaterialTheme.typography.headlineSmall)
            }
            Button(onClick = onBackspace, colors = colors, elevation = elevation, modifier = Modifier.weight(1f).aspectRatio(KEYPAD_ASPECT)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
            }
        }
    }
}

/** [roundedTotal] itself, then the next $5/$10/$20/$50 multiple above it — capped at 4 chips. */
private fun quickPickValues(roundedTotal: Long): List<Long> {
    val denominations = listOf(500L, 1_000L, 2_000L, 5_000L, 10_000L)
    val nextMultiples = denominations
        .map { denom -> ((roundedTotal + denom - 1) / denom) * denom }
        .filter { it > roundedTotal }
        .distinct()
    return (listOf(roundedTotal) + nextMultiples).distinct().take(MAX_QUICK_PICKS)
}

private fun formatShortfall(cents: Long): String {
    val dollars = cents / CENTS_PER_DOLLAR
    val remainder = cents % CENTS_PER_DOLLAR
    return "$%d.%02d".format(dollars, remainder)
}

private const val DIGIT_SHIFT = 10L
private const val MAX_CENTS = 99_999_999L
private const val MAX_QUICK_PICKS = 4
private const val CENTS_PER_DOLLAR = 100L
private const val KEYPAD_ASPECT = 1.6f
private const val CARD_BORDER_ALPHA = 0.15f
private const val CHANGE_CARD_TINT_ALPHA = 0.08f
private val cardCornerRadius = 12.dp
private val tenderedCardCornerRadius = 14.dp

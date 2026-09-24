package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import co.monveri.register.data.repository.CashCount
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * The four-section physical cash count — Bills, Loose Coins, Coin Rolls, Bill Straps (Bundles) —
 * matching the web POS's `close.php` denominations and per-unit values exactly, so a cashier
 * counting the same drawer gets the same total on either surface.
 */
@Composable
internal fun BillsSection(cashCount: CashCount, onChange: ((CashCount) -> CashCount) -> Unit) {
    SectionHeader("Bills")
    DenominationGrid(BILLS, cashCount, onChange)
}

@Composable
internal fun LooseCoinsSection(cashCount: CashCount, onChange: ((CashCount) -> CashCount) -> Unit) {
    SectionHeader("Loose Coins")
    DenominationGrid(COINS, cashCount, onChange)
}

@Composable
internal fun CoinRollsSection(cashCount: CashCount, onChange: ((CashCount) -> CashCount) -> Unit) {
    SectionHeader("Coin Rolls")
    DenominationGrid(ROLLS, cashCount, onChange)
}

@Composable
internal fun BillStrapsSection(cashCount: CashCount, onChange: ((CashCount) -> CashCount) -> Unit) {
    SectionHeader("Bill Straps (Bundles)")
    DenominationGrid(STRAPS, cashCount, onChange)
}

@Composable
internal fun CashCounterSummary(cashCount: CashCount, expectedCashCents: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Xs), modifier = Modifier.fillMaxWidth()) {
        SummaryRow("Bills total", cashCount.billsCents)
        SummaryRow("Coins total", cashCount.coinsCents)
        SummaryRow("Rolls total", cashCount.rollsCents)
        SummaryRow("Straps total", cashCount.strapsCents)
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Actual Cash", style = MaterialTheme.typography.titleMedium)
            MoneyText(cents = cashCount.totalCents, style = MaterialTheme.typography.titleMedium)
        }
        if (expectedCashCents != null) {
            val variance = cashCount.totalCents - expectedCashCents
            CompositionLocalProvider(LocalContentColor provides varianceLiveColor(variance)) {
                Text(
                    text = varianceLiveLabel(variance),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, cents: Long) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(cents = cents, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun varianceLiveLabel(varianceCents: Long): String = when {
    varianceCents == 0L -> "Variance: $0.00 — Exact Match!"
    varianceCents > 0L -> "Variance: +${formatDollars(varianceCents)} (Over)"
    else -> "Variance: -${formatDollars(-varianceCents)} (Short)"
}

@Composable
private fun varianceLiveColor(varianceCents: Long) = when {
    varianceCents == 0L -> MaterialTheme.colorScheme.secondary
    varianceCents > 0L -> MonveriTheme.statusColors.success
    else -> MonveriTheme.statusColors.danger
}

private fun formatDollars(cents: Long): String {
    val dollars = cents / CENTS_PER_DOLLAR
    val remainder = cents % CENTS_PER_DOLLAR
    return "$%d.%02d".format(dollars, remainder)
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MonveriTheme.statusColors.warning,
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider()
}

@Composable
private fun DenominationGrid(items: List<DenominationSpec>, cashCount: CashCount, onChange: ((CashCount) -> CashCount) -> Unit) {
    items.chunked(GRID_COLUMNS).forEach { rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm), modifier = Modifier.fillMaxWidth()) {
            rowItems.forEach { spec -> DenominationField(spec, cashCount, onChange, modifier = Modifier.weight(1f)) }
            repeat(GRID_COLUMNS - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DenominationField(
    spec: DenominationSpec,
    cashCount: CashCount,
    onChange: ((CashCount) -> CashCount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = spec.get(cashCount)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(spec.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        spec.desc?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MonveriTextField(
            value = if (count == 0) "" else count.toString(),
            onValueChange = { text ->
                val parsed = text.filter(Char::isDigit).take(MAX_COUNT_DIGITS).toIntOrNull() ?: 0
                onChange { c -> spec.set(c, parsed) }
            },
            label = "Qty",
            placeholder = "0",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        val subtotal = count * spec.valueCents
        if (subtotal > 0) {
            CompositionLocalProvider(LocalContentColor provides MonveriTheme.statusColors.success) {
                MoneyText(cents = subtotal, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private data class DenominationSpec(
    val label: String,
    val valueCents: Long,
    val desc: String? = null,
    val get: (CashCount) -> Int,
    val set: (CashCount, Int) -> CashCount,
)

private val BILLS = listOf(
    DenominationSpec("$100 Bills", 10_000L, get = { it.bills100 }, set = { c, v -> c.copy(bills100 = v) }),
    DenominationSpec("$50 Bills", 5_000L, get = { it.bills50 }, set = { c, v -> c.copy(bills50 = v) }),
    DenominationSpec("$20 Bills", 2_000L, get = { it.bills20 }, set = { c, v -> c.copy(bills20 = v) }),
    DenominationSpec("$10 Bills", 1_000L, get = { it.bills10 }, set = { c, v -> c.copy(bills10 = v) }),
    DenominationSpec("$5 Bills", 500L, get = { it.bills5 }, set = { c, v -> c.copy(bills5 = v) }),
    DenominationSpec("$1 Bills", 100L, get = { it.bills1 }, set = { c, v -> c.copy(bills1 = v) }),
)

private val COINS = listOf(
    DenominationSpec("Dollar Coins", 100L, get = { it.dollarCoins }, set = { c, v -> c.copy(dollarCoins = v) }),
    DenominationSpec("Half Dollars", 50L, get = { it.halfDollars }, set = { c, v -> c.copy(halfDollars = v) }),
    DenominationSpec("Quarters", 25L, get = { it.quarters }, set = { c, v -> c.copy(quarters = v) }),
    DenominationSpec("Dimes", 10L, get = { it.dimes }, set = { c, v -> c.copy(dimes = v) }),
    DenominationSpec("Nickels", 5L, get = { it.nickels }, set = { c, v -> c.copy(nickels = v) }),
    DenominationSpec("Pennies", 1L, get = { it.pennies }, set = { c, v -> c.copy(pennies = v) }),
)

private val ROLLS = listOf(
    DenominationSpec("Dollar Rolls", 2_500L, "25 x $1", { it.dollarRolls }, { c, v -> c.copy(dollarRolls = v) }),
    DenominationSpec("Half Dollar Rolls", 1_000L, "20 x 50c", { it.halfDollarRolls }, { c, v -> c.copy(halfDollarRolls = v) }),
    DenominationSpec("Quarter Rolls", 1_000L, "40 x 25c", { it.quarterRolls }, { c, v -> c.copy(quarterRolls = v) }),
    DenominationSpec("Dime Rolls", 500L, "50 x 10c", { it.dimeRolls }, { c, v -> c.copy(dimeRolls = v) }),
    DenominationSpec("Nickel Rolls", 200L, "40 x 5c", { it.nickelRolls }, { c, v -> c.copy(nickelRolls = v) }),
    DenominationSpec("Penny Rolls", 50L, "50 x 1c", { it.pennyRolls }, { c, v -> c.copy(pennyRolls = v) }),
)

private val STRAPS = listOf(
    DenominationSpec("$20 Straps", 50_000L, "25 x $20", { it.straps20 }, { c, v -> c.copy(straps20 = v) }),
    DenominationSpec("$10 Straps", 25_000L, "25 x $10", { it.straps10 }, { c, v -> c.copy(straps10 = v) }),
    DenominationSpec("$5 Straps", 10_000L, "20 x $5", { it.straps5 }, { c, v -> c.copy(straps5 = v) }),
    DenominationSpec("$1 Straps", 2_500L, "25 x $1", { it.straps1 }, { c, v -> c.copy(straps1 = v) }),
)

private const val GRID_COLUMNS = 2
private const val MAX_COUNT_DIGITS = 4
private const val CENTS_PER_DOLLAR = 100L

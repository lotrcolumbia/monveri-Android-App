package co.monveri.register.feature.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.tokens.MonveriSpacing
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Bottom sheet that lets the cashier apply a whole-ticket discount. Two modes:
 *  - Percent: 5/10/15/20 preset chips + free entry
 *  - Dollars: free entry (decimal, two places)
 *
 * The final cents value is clamped to [0, subtotal] inside [PricingEngine.calculate], so the
 * sheet doesn't need to defend against over-discounting beyond a basic non-empty check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscountPickerSheet(
    currentDiscountCents: Long,
    subtotalCents: Long,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // When the sheet opens with an existing discount, start in Dollars mode — the value
    // was applied as a flat amount (we don't round-trip a percentage). Defaulting to Percent
    // mode with an empty percent field would compute 0 on Apply and silently wipe the discount.
    var mode by remember {
        mutableStateOf(if (currentDiscountCents > 0) DiscountMode.Dollars else DiscountMode.Percent)
    }
    var dollarText by remember {
        mutableStateOf(
            if (currentDiscountCents > 0) formatCentsAsDollars(currentDiscountCents) else "",
        )
    }
    var percentText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(MonveriSpacing.Lg)) {

            Text(text = "Discount", style = MaterialTheme.typography.titleLarge)

            Box(modifier = Modifier.padding(top = MonveriSpacing.Md)) {
                SingleChoiceSegmentedButtonRow {
                    DiscountMode.entries.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = mode == value,
                            onClick = { mode = value },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = DiscountMode.entries.size,
                            ),
                        ) { Text(value.label) }
                    }
                }
            }

            when (mode) {
                DiscountMode.Percent -> PercentSection(
                    subtotalCents = subtotalCents,
                    percentText = percentText,
                    onPercentTextChange = { percentText = it },
                )
                DiscountMode.Dollars -> DollarsSection(
                    dollarText = dollarText,
                    onDollarTextChange = { dollarText = it },
                )
            }

            Box(modifier = Modifier.padding(top = MonveriSpacing.Xl)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
                ) {
                    MonveriButton(
                        text = "Remove",
                        onClick = { onApply(0L) },
                        variant = MonveriButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    MonveriButton(
                        text = "Apply",
                        onClick = {
                            val cents = when (mode) {
                                DiscountMode.Percent -> centsFromPercent(percentText, subtotalCents)
                                DiscountMode.Dollars -> centsFromDollars(dollarText)
                            }
                            onApply(cents)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PercentSection(
    subtotalCents: Long,
    percentText: String,
    onPercentTextChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = MonveriSpacing.Md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
            PRESET_PERCENTS.forEach { preset ->
                FilterChip(
                    selected = percentText == preset.toString(),
                    onClick = { onPercentTextChange(preset.toString()) },
                    label = { Text("$preset%") },
                )
            }
        }
        OutlinedTextField(
            value = percentText,
            onValueChange = { input ->
                if (input.matches(NUMERIC_REGEX)) onPercentTextChange(input)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MonveriSpacing.Md),
            label = { Text("Custom percent") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { Text("%") },
            singleLine = true,
        )
        val previewCents = centsFromPercent(percentText, subtotalCents)
        Text(
            text = if (previewCents > 0L) "Discount: ${formatCentsAsCurrency(previewCents)}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MonveriSpacing.Sm),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DollarsSection(dollarText: String, onDollarTextChange: (String) -> Unit) {
    OutlinedTextField(
        value = dollarText,
        onValueChange = { input ->
            if (input.matches(NUMERIC_REGEX)) onDollarTextChange(input)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MonveriSpacing.Md),
        label = { Text("Discount amount") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        prefix = { Text("$") },
        singleLine = true,
    )
}

private enum class DiscountMode(val label: String) {
    Percent("Percent"),
    Dollars("Dollars"),
}

private val PRESET_PERCENTS = listOf(5, 10, 15, 20)
private val NUMERIC_REGEX = Regex("^\\d*(\\.\\d{0,2})?$")

private fun centsFromPercent(text: String, subtotalCents: Long): Long {
    val pct = text.toBigDecimalOrNull() ?: return 0L
    if (subtotalCents <= 0L) return 0L
    val cents = BigDecimal(subtotalCents)
        .multiply(pct)
        .divide(BigDecimal(PERCENT_DIVISOR), 0, RoundingMode.HALF_UP)
        .toLong()
    return cents.coerceAtLeast(0L)
}

private fun centsFromDollars(text: String): Long {
    val dollars = text.toBigDecimalOrNull() ?: return 0L
    return dollars
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()
        .coerceAtLeast(0L)
}

private fun formatCentsAsDollars(cents: Long): String =
    BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

private fun formatCentsAsCurrency(cents: Long): String = "$${formatCentsAsDollars(cents)}"

private const val PERCENT_DIVISOR: Int = 100

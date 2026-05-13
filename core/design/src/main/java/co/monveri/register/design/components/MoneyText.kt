package co.monveri.register.design.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import co.monveri.register.design.MonveriTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Locale-aware currency renderer. Pass the integer **cents** value; the formatter handles
 * the major/minor units. Negative amounts get colored red (or the configured danger token)
 * when [signColored] is true — matches refund / negative-balance conventions on the receipt UI.
 */
@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.US,
    currencyCode: String = "USD",
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    signColored: Boolean = false,
    boldZero: Boolean = false,
) {
    val formatter = remember(locale, currencyCode) {
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = java.util.Currency.getInstance(currencyCode)
        }
    }
    val amount = cents.toDouble() / CENTS_PER_DOLLAR
    val color: Color = when {
        signColored && cents < 0 -> MonveriTheme.statusColors.danger
        signColored && cents > 0 -> MonveriTheme.statusColors.success
        else -> LocalContentColor.current
    }
    val effectiveStyle = if (boldZero && cents == 0L) {
        style.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    } else {
        style
    }
    Text(
        text = formatter.format(amount),
        color = color,
        style = effectiveStyle,
        modifier = modifier,
    )
}

private const val CENTS_PER_DOLLAR = 100.0

package co.monveri.register.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.monveri.register.design.MonveriTheme

enum class MonveriButtonVariant { Primary, Secondary, Destructive }

/**
 * Brand-styled button. Three variants + an optional `loading` state that swaps the label for a
 * progress indicator and disables interaction. Sized for touch (min 48dp via Material defaults).
 */
@Composable
fun MonveriButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MonveriButtonVariant = MonveriButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val effectiveEnabled = enabled && !loading
    val isDestructive = variant == MonveriButtonVariant.Destructive
    val isSecondary = variant == MonveriButtonVariant.Secondary

    if (isSecondary) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = effectiveEnabled,
        ) {
            ButtonContent(text = text, loading = loading)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = effectiveEnabled,
            colors = if (isDestructive) destructiveColors() else ButtonDefaults.buttonColors(),
        ) {
            ButtonContent(text = text, loading = loading)
        }
    }
}

@Composable
private fun ButtonContent(text: String, loading: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(text = text)
    }
}

@Composable
private fun destructiveColors(): ButtonColors {
    val danger = MonveriTheme.statusColors.danger
    val onDanger = MonveriTheme.statusColors.onDanger
    return ButtonDefaults.buttonColors(
        containerColor = danger,
        contentColor = onDanger,
    )
}

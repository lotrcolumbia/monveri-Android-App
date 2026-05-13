package co.monveri.register.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Confirm/cancel modal with branded buttons. Use for destructive actions (delete sale, unpair).
 * The confirm button gets the [confirmVariant] variant — typically [MonveriButtonVariant.Destructive]
 * for delete-style flows or [MonveriButtonVariant.Primary] for opt-in.
 */
@Composable
fun ConfirmModalScaffold(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "Cancel",
    confirmVariant: MonveriButtonVariant = MonveriButtonVariant.Primary,
    loading: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(title) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            MonveriButton(
                text = confirmLabel,
                onClick = onConfirm,
                variant = confirmVariant,
                loading = loading,
            )
        },
        dismissButton = {
            MonveriButton(
                text = cancelLabel,
                onClick = onDismiss,
                variant = MonveriButtonVariant.Secondary,
                enabled = !loading,
            )
        },
    )
}

/** Two-button row for inline confirm actions (e.g. footer of a bottom sheet). */
@Composable
fun ConfirmActionsRow(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    cancelLabel: String = "Cancel",
    confirmVariant: MonveriButtonVariant = MonveriButtonVariant.Primary,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(MonveriSpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
    ) {
        MonveriButton(
            text = cancelLabel,
            onClick = onCancel,
            variant = MonveriButtonVariant.Secondary,
            enabled = !loading,
            modifier = Modifier.weight(1f),
        )
        MonveriButton(
            text = confirmLabel,
            onClick = onConfirm,
            variant = confirmVariant,
            loading = loading,
            modifier = Modifier.weight(1f),
        )
    }
}

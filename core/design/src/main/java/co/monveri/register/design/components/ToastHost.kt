package co.monveri.register.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.tokens.MonveriCornerRadius
import co.monveri.register.design.tokens.MonveriSpacing

enum class ToastKind { Success, Warning, Danger, Info }

/**
 * Encoded into the Snackbar visuals' message string using a `kind::msg` prefix. Callers should
 * use [SnackbarHostState.showToast] which handles the encoding for them.
 */
private const val KIND_DELIMITER = "::"

suspend fun SnackbarHostState.showToast(message: String, kind: ToastKind = ToastKind.Info) {
    showSnackbar(message = "${kind.name}$KIND_DELIMITER$message")
}

/**
 * Branded Snackbar host. Plug into a Scaffold's `snackbarHost` slot; callers fire
 * `snackbarHostState.showToast("Saved", ToastKind.Success)`.
 */
@Composable
fun MonveriToastHost(snackbarHostState: SnackbarHostState) {
    SnackbarHost(hostState = snackbarHostState) { data ->
        val (kind, message) = remember(data) { decode(data.visuals.message) }
        val backgroundColor = backgroundFor(kind)
        val contentColor = contentColorFor(kind)
        Snackbar(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            modifier = Modifier
                .padding(MonveriSpacing.Md)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(MonveriCornerRadius.Md))
                    .background(backgroundColor)
                    .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Md)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
            ) {
                Icon(
                    imageVector = iconFor(kind),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(text = message, color = contentColor, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun decode(raw: String): Pair<ToastKind, String> {
    val idx = raw.indexOf(KIND_DELIMITER)
    if (idx <= 0) return ToastKind.Info to raw
    val prefix = raw.substring(0, idx)
    val message = raw.substring(idx + KIND_DELIMITER.length)
    val kind = ToastKind.entries.firstOrNull { it.name == prefix } ?: ToastKind.Info
    return kind to message
}

@Composable
private fun backgroundFor(kind: ToastKind): Color = when (kind) {
    ToastKind.Success -> MonveriTheme.statusColors.success
    ToastKind.Warning -> MonveriTheme.statusColors.warning
    ToastKind.Danger -> MonveriTheme.statusColors.danger
    ToastKind.Info -> MonveriTheme.statusColors.info
}

@Composable
private fun contentColorFor(kind: ToastKind): Color = when (kind) {
    ToastKind.Success -> MonveriTheme.statusColors.onSuccess
    ToastKind.Warning -> MonveriTheme.statusColors.onWarning
    ToastKind.Danger -> MonveriTheme.statusColors.onDanger
    ToastKind.Info -> MonveriTheme.statusColors.onInfo
}

private fun iconFor(kind: ToastKind): ImageVector = when (kind) {
    ToastKind.Success -> Icons.Filled.CheckCircle
    ToastKind.Warning -> Icons.Filled.Warning
    ToastKind.Danger -> Icons.Filled.Error
    ToastKind.Info -> Icons.Filled.Info
}

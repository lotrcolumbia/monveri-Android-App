package co.monveri.register.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import co.monveri.register.data.repository.ExpenseStatus
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.tokens.MonveriCornerRadius
import co.monveri.register.design.tokens.MonveriSpacing

/** Small capsule badge — orange Pending / green Approved / red "Needs attention". Matches iOS. */
@Composable
fun ExpenseStatusPill(status: ExpenseStatus, modifier: Modifier = Modifier) {
    val (background, content) = when (status) {
        ExpenseStatus.APPROVED -> MonveriTheme.statusColors.success to MonveriTheme.statusColors.onSuccess
        ExpenseStatus.REJECTED -> MonveriTheme.statusColors.danger to MonveriTheme.statusColors.onDanger
        ExpenseStatus.PENDING -> MonveriTheme.statusColors.warning to MonveriTheme.statusColors.onWarning
    }
    Text(
        text = status.label,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier
            .clip(RoundedCornerShape(MonveriCornerRadius.Pill))
            .background(background)
            .padding(horizontal = MonveriSpacing.Sm, vertical = 2.dp),
    )
}

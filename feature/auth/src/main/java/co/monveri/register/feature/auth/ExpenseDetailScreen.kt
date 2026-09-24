package co.monveri.register.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.Expense
import co.monveri.register.data.repository.ExpenseAuditEntry
import co.monveri.register.data.repository.ExpenseStatus
import co.monveri.register.design.MonveriTheme
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriCornerRadius
import co.monveri.register.design.tokens.MonveriSpacing
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Read-only expense detail — see [ExpenseDetailViewModel]'s doc for why there's no edit/approve/
 * reject/delete here. This is "confirm the submission landed", same framing as iOS's own comment
 * on `ExpenseDetailView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExpenseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.expense?.vendorName?.ifBlank { "Expense" } ?: "Expense") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.expense == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.errorMessage ?: "Could not load this expense")
                }
                else -> ExpenseDetailContent(
                    expense = state.expense!!,
                    audit = state.audit,
                    imageUrl = state.expense!!.imageUrl?.let { viewModel.resolveImageUrl(it) },
                    imageHeaders = viewModel.imageHeaders,
                )
            }
        }
    }
}

@Composable
private fun ExpenseDetailContent(
    expense: Expense,
    audit: List<ExpenseAuditEntry>,
    imageUrl: String?,
    imageHeaders: Map<String, String>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        imageUrl?.let { url ->
            val context = LocalContext.current
            val request = remember(url, imageHeaders) {
                val builder = ImageRequest.Builder(context).data(url)
                imageHeaders.forEach { (name, value) -> builder.addHeader(name, value) }
                builder.build()
            }
            AsyncImage(
                model = request,
                contentDescription = "Receipt image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(MonveriCornerRadius.Md))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        val rejectionReason = expense.rejectionReason
        if (expense.status == ExpenseStatus.REJECTED && !rejectionReason.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MonveriCornerRadius.Md))
                    .background(MonveriTheme.statusColors.danger.copy(alpha = DANGER_CALLOUT_ALPHA))
                    .padding(MonveriSpacing.Md),
            ) {
                Text(
                    "Needs attention",
                    style = MaterialTheme.typography.titleMedium,
                    color = MonveriTheme.statusColors.danger,
                )
                Text(rejectionReason, style = MaterialTheme.typography.bodyMedium)
            }
        }

        DetailSection(title = "Status") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
                ExpenseStatusPill(status = expense.status)
                expense.reviewedAt?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        DetailSection(title = "Receipt") {
            DetailRow("Vendor", expense.vendorName.ifBlank { "—" })
            DetailRow("Date", expense.expenseDate)
            expense.categoryName?.let { DetailRow("Category", it) }
            DetailRow("Payment method", expense.paymentMethod.label)
        }

        DetailSection(title = "Amounts") {
            expense.subtotalAmountCents?.let {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(cents = it)
                }
            }
            expense.taxAmountCents?.let {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Tax", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(cents = it)
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                MoneyText(cents = expense.amountCents, style = MaterialTheme.typography.titleMedium)
            }
        }

        if (expense.notes.isNotBlank()) {
            DetailSection(title = "Notes") {
                Text(expense.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (audit.isNotEmpty()) {
            DetailSection(title = "Activity") {
                audit.forEach { entry ->
                    Column(modifier = Modifier.padding(vertical = MonveriSpacing.Xs)) {
                        Text("${entry.action.replaceFirstChar { it.uppercase() }} · ${entry.actorName}")
                        entry.detail?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(entry.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private const val DANGER_CALLOUT_ALPHA = 0.12f

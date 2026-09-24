package co.monveri.register.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.Expense
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Back Office → Expenses. Mirrors iOS's `ExpensesTabView`: paginated list, a receipt thumbnail
 * (or a generic doc icon when there's no image), status pill, and a "+" to add one. No "Scan
 * receipt" camera path yet — this slice covers manual entry end to end; see
 * [AddExpenseScreen]'s doc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onExpenseSelected: (Long) -> Unit,
    onAddExpenseRequested: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExpenseListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadFirstPage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAddExpenseRequested) {
                        Icon(Icons.Filled.Add, contentDescription = "Add expense")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Box
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.items.isEmpty() -> EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.Receipt,
                        title = "No receipts yet",
                        message = "Tap + to add a receipt by hand. Submissions land in the back " +
                            "office for approval.",
                        actionLabel = state.errorMessage?.let { "Try again" },
                        onAction = state.errorMessage?.let { { viewModel.loadFirstPage() } },
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.id }) { expense ->
                            ExpenseRow(
                                expense = expense,
                                imageUrl = expense.imageUrl?.let { viewModel.resolveImageUrl(it) },
                                imageHeaders = viewModel.imageHeaders,
                                onClick = { onExpenseSelected(expense.id) },
                            )
                            HorizontalDivider()
                        }
                        if (state.hasMore) {
                            item {
                                LoadMoreRow(isLoading = state.isLoadingMore, onClick = viewModel::loadNextPage)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    imageUrl: String?,
    imageHeaders: Map<String, String>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
    ) {
        ReceiptThumbnail(imageUrl = imageUrl, headers = imageHeaders)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = expense.vendorName.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(formatExpenseDate(expense.expenseDate), expense.categoryName)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            MoneyText(cents = expense.amountCents, style = MaterialTheme.typography.bodyLarge)
            ExpenseStatusPill(status = expense.status, modifier = Modifier.padding(top = MonveriSpacing.Xs))
        }
    }
}

@Composable
private fun ReceiptThumbnail(imageUrl: String?, headers: Map<String, String>) {
    val thumbnailModifier = Modifier
        .size(48.dp)
        .clip(RoundedCornerShape(MonveriSpacing.Xs))

    if (imageUrl == null) {
        Box(
            modifier = thumbnailModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val context = LocalContext.current
        val request = remember(imageUrl, headers) {
            val builder = ImageRequest.Builder(context).data(imageUrl)
            headers.forEach { (name, value) -> builder.addHeader(name, value) }
            builder.build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = thumbnailModifier,
        )
    }
}

@Composable
private fun LoadMoreRow(isLoading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MonveriSpacing.Lg),
        horizontalArrangement = Arrangement.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            TextButton(onClick = onClick) { Text("Load more") }
        }
    }
}

private val EXPENSE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun formatExpenseDate(iso: String): String? {
    if (iso.isBlank()) return null
    return runCatching { LocalDate.parse(iso).format(EXPENSE_DATE_FORMATTER) }.getOrNull() ?: iso
}

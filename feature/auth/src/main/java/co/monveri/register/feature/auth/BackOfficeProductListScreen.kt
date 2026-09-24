package co.monveri.register.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.Product
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Back Office → Products. Tap a row to edit it; see [BackOfficeProductListViewModel] for what
 * this list can and can't show. No "add new product" yet — edit-only for this first slice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackOfficeProductListScreen(
    onProductSelected: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: BackOfficeProductListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Products") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Sm),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search name, SKU, or UPC") },
                singleLine = true,
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isEmpty -> EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.Add,
                        title = "Catalog is empty",
                        message = "Sync the catalog from the Products tab first.",
                    )
                    state.products.isEmpty() -> EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.Search,
                        title = "No products match",
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.products, key = { it.id }) { product ->
                            BackOfficeProductRow(product = product, onClick = { onProductSelected(product.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackOfficeProductRow(product: Product, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            product.sku?.let { sku ->
                Text(
                    text = sku,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MoneyText(cents = product.priceCents, style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

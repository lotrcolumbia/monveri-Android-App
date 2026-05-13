package co.monveri.register.feature.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.Category
import co.monveri.register.data.repository.Product
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriCornerRadius
import co.monveri.register.design.tokens.MonveriSpacing
import kotlinx.coroutines.launch

/**
 * Catalog browse screen — the cashier's main "find a product" surface.
 *
 * Three input affordances stacked at the top:
 *   1. Search field (server-side, debounced 300 ms by the ViewModel)
 *   2. Horizontal category chips (client-side filter on whatever list is current)
 *   3. Scan button → opens the barcode scanner sheet which hands the code back via callback
 *
 * Tap a card → product detail. Long-press → quick-add the default variant straight to the cart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogListScreen(
    onProductSelected: (Long) -> Unit,
    onScanRequested: () -> Unit,
    onCartRequested: () -> Unit,
    pendingScannedBarcode: String? = null,
    onScannedBarcodeConsumed: () -> Unit = {},
    viewModel: CatalogListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartItemCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pendingScannedBarcode) {
        val code = pendingScannedBarcode ?: return@LaunchedEffect
        viewModel.onBarcodeScanned(code)
        onScannedBarcodeConsumed()
    }

    LaunchedEffect(state.barcodeFlashMessage) {
        val message = state.barcodeFlashMessage ?: return@LaunchedEffect
        scope.launch {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissBarcodeFlash()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catalog") },
                actions = {
                    IconButton(onClick = onScanRequested) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(snackbarData = it) } },
        floatingActionButton = {
            if (cartCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onCartRequested,
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                    text = { Text("View cart ($cartCount)") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            SearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                onClear = { viewModel.onQueryChanged("") },
                isSearching = state.isSearching,
            )

            CategoryChipRow(
                categories = state.categories,
                selectedId = state.selectedCategoryId,
                onSelect = viewModel::onCategorySelected,
            )

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Sm),
                )
            }

            Box(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MonveriSpacing.Sm),
            ) {
                when {
                    state.isInitialLoad -> CenteredSpinner()
                    state.products.isEmpty() -> EmptyCatalogState(
                        hasFilters = state.query.isNotBlank() || state.selectedCategoryId != null,
                        onClearFilters = {
                            viewModel.onQueryChanged("")
                            viewModel.onCategorySelected(null)
                        },
                    )
                    else -> ProductGrid(
                        products = state.products,
                        onTap = { onProductSelected(it.id) },
                        onLongPress = viewModel::quickAdd,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Sm),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text("Search name, SKU, or UPC") },
        singleLine = true,
        trailingIcon = {
            when {
                isSearching -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                value.isNotEmpty() -> IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
                else -> Unit
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChipRow(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    if (categories.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = MonveriSpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
    ) {
        item {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(if (selectedId == category.id) null else category.id) },
                label = { Text(category.name) },
            )
        }
    }
}

@Composable
private fun ProductGrid(
    products: List<Product>,
    onTap: (Product) -> Unit,
    onLongPress: (Product) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = PRODUCT_CARD_MIN_WIDTH),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(MonveriSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
    ) {
        items(products, key = { it.id }) { product ->
            ProductCard(
                product = product,
                onTap = { onTap(product) },
                onLongPress = { onLongPress(product) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductCard(
    product: Product,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(MonveriCornerRadius.Md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Column(modifier = Modifier
            .padding(MonveriSpacing.Md)
            .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PRODUCT_THUMB_HEIGHT)
                    .clip(RoundedCornerShape(MonveriCornerRadius.Sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MonveriSpacing.Sm),
            )
            product.sku?.let { sku ->
                Text(
                    text = sku,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MonveriSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MoneyText(
                    cents = product.priceCents,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (product.tracksStock && product.stockQuantity <= 0) {
                    // Static badge — `enabled = false` removes the ripple + makes screen readers
                    // skip the interactive role. An empty onClick would look tappable.
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Out") },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyCatalogState(hasFilters: Boolean, onClearFilters: () -> Unit) {
    EmptyState(
        modifier = Modifier.fillMaxSize(),
        icon = Icons.Filled.Add,
        title = if (hasFilters) "No products match" else "Catalog is empty",
        message = if (hasFilters) {
            "Try a different search term or clear your filters."
        } else {
            "Pull down to sync the catalog from the server."
        },
        actionLabel = if (hasFilters) "Clear filters" else null,
        onAction = if (hasFilters) onClearFilters else null,
    )
}

private val PRODUCT_CARD_MIN_WIDTH = 160.dp
private val PRODUCT_THUMB_HEIGHT = 120.dp

package co.monveri.register.feature.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.Product
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing
import kotlinx.coroutines.launch

/**
 * Catalog browse screen — the cashier's main "find a product" surface.
 *
 * Four pages behind a bottom tab bar, mirroring iOS's Quick / Products / Categories / Custom
 * swipe pages:
 *   - **Quick**: admin-configured tiles (mix of individual products and category folders)
 *   - **Products**: server-side search (debounced 300 ms) + the full grid
 *   - **Categories**: flat, indented category picker; tapping drills into a product list
 *   - **Custom**: ad-hoc name/price entry added straight to the cart, no backing product
 *
 * Typing in the search field always snaps back to Products, since search only renders there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogListScreen(
    onProductSelected: (Long) -> Unit,
    onScanRequested: () -> Unit,
    onCartRequested: () -> Unit,
    onCustomerRequested: () -> Unit = {},
    onCategorySelected: (String) -> Unit = {},
    onReaderRequested: () -> Unit = {},
    onCloseRegisterRequested: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    pendingScannedBarcode: String? = null,
    onScannedBarcodeConsumed: () -> Unit = {},
    viewModel: CatalogListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartItemCount.collectAsStateWithLifecycle()
    val cartSubtotalCents by viewModel.cartSubtotalCents.collectAsStateWithLifecycle()
    val customerName by viewModel.attachedCustomerName.collectAsStateWithLifecycle()
    val hasOpenRegisterSession by viewModel.hasOpenRegisterSession.collectAsStateWithLifecycle()
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

    LaunchedEffect(state.query) {
        if (state.query.isNotBlank()) viewModel.onTabSelected(CatalogTab.PRODUCTS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catalog") },
                navigationIcon = {
                    CartBadgeButton(count = cartCount, subtotalCents = cartSubtotalCents, onClick = onCartRequested)
                },
                actions = {
                    if (customerName == null) {
                        IconButton(onClick = onCustomerRequested) {
                            Icon(Icons.Filled.PersonAddAlt, contentDescription = "Attach customer")
                        }
                    }
                    IconButton(onClick = onScanRequested) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                    ProfileMenuButton(
                        employeeName = viewModel.employeeName,
                        hasOpenRegisterSession = hasOpenRegisterSession,
                        onReaderRequested = onReaderRequested,
                        onCloseRegisterRequested = onCloseRegisterRequested,
                        onLogout = {
                            viewModel.logout()
                            onLoggedOut()
                        },
                    )
                },
            )
        },
        bottomBar = {
            CatalogTabBar(selected = tabState.selectedTab, onSelect = viewModel::onTabSelected)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(snackbarData = it) } },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            customerName?.let { name ->
                CustomerBanner(name = name, onClick = onCustomerRequested)
            }

            if (tabState.selectedTab == CatalogTab.PRODUCTS) {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    onClear = { viewModel.onQueryChanged("") },
                    isSearching = state.isSearching,
                )
            }

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
                when (tabState.selectedTab) {
                    CatalogTab.QUICK -> QuickButtonsTab(
                        buttons = tabState.quickButtons,
                        isLoading = tabState.quickButtonsLoading,
                        errorMessage = tabState.quickButtonsError,
                        onOpenProduct = onProductSelected,
                        onOpenCategory = onCategorySelected,
                    )
                    CatalogTab.PRODUCTS -> when {
                        state.isInitialLoad -> CenteredSpinner()
                        state.products.isEmpty() -> EmptyCatalogState(
                            hasFilters = state.query.isNotBlank(),
                            onClearFilters = { viewModel.onQueryChanged("") },
                        )
                        else -> ProductGrid(
                            products = state.products,
                            onTap = { onProductSelected(it.id) },
                            onLongPress = viewModel::quickAdd,
                        )
                    }
                    CatalogTab.CATEGORIES -> CategoriesTab(
                        categories = state.categories,
                        onCategorySelected = onCategorySelected,
                    )
                    CatalogTab.CUSTOM -> CustomItemTab(
                        state = tabState.customItem,
                        onNameChanged = viewModel::onCustomItemNameChanged,
                        onDigit = viewModel::onCustomItemDigit,
                        onBackspace = viewModel::onCustomItemBackspace,
                        onClear = viewModel::onCustomItemClear,
                        onTaxableChanged = viewModel::onCustomItemTaxableChanged,
                        onAdd = viewModel::addCustomItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogTabBar(selected: CatalogTab, onSelect: (CatalogTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == CatalogTab.QUICK,
            onClick = { onSelect(CatalogTab.QUICK) },
            icon = { Icon(Icons.Filled.GridView, contentDescription = null) },
            label = { Text("Quick") },
        )
        NavigationBarItem(
            selected = selected == CatalogTab.PRODUCTS,
            onClick = { onSelect(CatalogTab.PRODUCTS) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text("Products") },
        )
        NavigationBarItem(
            selected = selected == CatalogTab.CATEGORIES,
            onClick = { onSelect(CatalogTab.CATEGORIES) },
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            label = { Text("Categories") },
        )
        NavigationBarItem(
            selected = selected == CatalogTab.CUSTOM,
            onClick = { onSelect(CatalogTab.CUSTOM) },
            icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            label = { Text("Custom") },
        )
    }
}

@Composable
private fun CartBadgeButton(count: Int, subtotalCents: Long, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = if (count > 0) Icons.Filled.ShoppingCart else Icons.Outlined.ShoppingCart,
            contentDescription = "View cart",
            tint = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count > 0) {
            Text(
                text = "$count · ${formatWholeDollars(subtotalCents)}",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = MonveriSpacing.Xs),
            )
        }
    }
}

/** Rounded whole-dollar display for the toolbar badge only — line items still show exact cents. */
private fun formatWholeDollars(cents: Long): String = "$${(cents + HALF_CENT_ROUNDING) / CENTS_PER_DOLLAR}"

private const val HALF_CENT_ROUNDING = 50L
private const val CENTS_PER_DOLLAR = 100L

/**
 * Mirrors iOS's employee dropdown (`Signed in as {name}` / Close Register / Card Reader /
 * Back Office / Log out). "Back Office" is left out on purpose — on iOS that's an entire native
 * admin sub-app (product management, expenses, ~2,000+ lines), a separate multi-day project, not
 * a menu item to fake here.
 */
@Composable
private fun ProfileMenuButton(
    employeeName: String?,
    hasOpenRegisterSession: Boolean,
    onReaderRequested: () -> Unit,
    onCloseRegisterRequested: () -> Unit,
    onLogout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.AccountCircle, contentDescription = "Account menu")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (employeeName != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Signed in as $employeeName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {},
                enabled = false,
            )
            HorizontalDivider()
        }
        if (hasOpenRegisterSession) {
            DropdownMenuItem(
                text = { Text("Close Register") },
                leadingIcon = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
                onClick = {
                    expanded = false
                    onCloseRegisterRequested()
                },
            )
        }
        DropdownMenuItem(
            text = { Text("Card Reader") },
            leadingIcon = { Icon(Icons.Filled.Contactless, contentDescription = null) },
            onClick = {
                expanded = false
                onReaderRequested()
            },
        )
        DropdownMenuItem(
            text = { Text("Log out") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            onClick = {
                expanded = false
                onLogout()
            },
        )
    }
}

@Composable
private fun CustomerBanner(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
    ) {
        Icon(Icons.Filled.PersonAddAlt, contentDescription = null)
        Text("Customer: $name", style = MaterialTheme.typography.bodyMedium)
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

/**
 * A flat, dividers-between-rows list — matches iOS's `ProductRowView`/`.insetGrouped List`
 * exactly (name + SKU + category on the left, price + stock + chevron on the right). Used for
 * both the Products tab and the category drill-in; neither app renders real product photos yet
 * (see [CatalogTabs.kt] — the backend's sync/search/barcode endpoints don't send an image field).
 */
@Composable
internal fun ProductGrid(
    products: List<Product>,
    onTap: (Product) -> Unit,
    onLongPress: (Product) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(products, key = { it.id }) { product ->
            ProductListRow(
                product = product,
                onTap = { onTap(product) },
                onLongPress = { onLongPress(product) },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductListRow(
    product: Product,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
            product.categoryName?.let { categoryName ->
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Spacer(modifier = Modifier.width(MonveriSpacing.Md))
        Column(horizontalAlignment = Alignment.End) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                MoneyText(
                    cents = product.priceCents,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            if (product.tracksStock) {
                if (product.stockQuantity <= 0) {
                    Text(
                        text = "Out of stock",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "${product.stockQuantity} in stock",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CenteredSpinner() {
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

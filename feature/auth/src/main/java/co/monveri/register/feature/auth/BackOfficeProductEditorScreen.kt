package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.tokens.MonveriSpacing
import kotlinx.coroutines.launch

/**
 * Back Office → Products → editor. Mirrors iOS's tabbed layout (Basics/Details/Pricing/Images/
 * Variants) — see [BackOfficeProductEditorViewModel]'s doc for how staged (Basics/Details/
 * Pricing/Variants) vs. immediate-effect (Images) fields are split.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackOfficeProductEditorScreen(
    onBack: () -> Unit,
    viewModel: BackOfficeProductEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.saved) {
        if (state.saved) {
            scope.launch {
                snackbarHostState.showSnackbar("Saved")
                viewModel.dismissSavedFlag()
            }
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifBlank { "Edit product" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.isSaving && !state.isLoading) {
                        Text(if (state.isSaving) "Saving…" else "Save")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(snackbarData = it) } },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Box
            }

            Column(modifier = Modifier.fillMaxSize()) {
                ScrollableTabRow(selectedTabIndex = state.activeTab.ordinal, edgePadding = MonveriSpacing.Lg) {
                    EditorTab.entries.forEach { tab ->
                        Tab(
                            selected = state.activeTab == tab,
                            onClick = { viewModel.updateState { it.copy(activeTab = tab) } },
                            text = { Text(tab.label) },
                        )
                    }
                }
                HorizontalDivider()

                when (state.activeTab) {
                    EditorTab.BASICS -> BasicsTab(state, viewModel)
                    EditorTab.DETAILS -> DetailsTab(state, viewModel)
                    EditorTab.PRICING -> PricingTab(state, viewModel)
                    EditorTab.IMAGES -> BackOfficeProductImagesTab(state, viewModel)
                    EditorTab.VARIANTS -> BackOfficeProductVariantsTab(state, viewModel)
                }
            }
        }
    }
}

private val EditorTab.label: String
    get() = when (this) {
        EditorTab.BASICS -> "Basics"
        EditorTab.DETAILS -> "Details"
        EditorTab.PRICING -> "Pricing"
        EditorTab.IMAGES -> "Images"
        EditorTab.VARIANTS -> "Variants"
    }

@Composable
private fun BasicsTab(state: BackOfficeProductEditorUiState, viewModel: BackOfficeProductEditorViewModel) {
    TabColumn {
        MonveriTextField(
            value = state.name,
            onValueChange = { v -> viewModel.updateState { it.copy(name = v) } },
            label = "Name",
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.sku,
            onValueChange = { v -> viewModel.updateState { it.copy(sku = v) } },
            label = "SKU",
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.upc,
            onValueChange = { v -> viewModel.updateState { it.copy(upc = v) } },
            label = "UPC",
            modifier = Modifier.fillMaxWidth(),
        )
        state.categoryName?.let { category ->
            Text(
                text = "Category: $category",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        MonveriTextField(
            value = state.quantityText,
            onValueChange = viewModel::onQuantityChanged,
            label = "Quantity on hand",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        SwitchRow(
            label = "Active",
            checked = state.isActive,
            onCheckedChange = { v -> viewModel.updateState { it.copy(isActive = v) } },
        )
        SwitchRow(
            label = "Visible on register (mobile POS)",
            checked = state.publishToMobilePos,
            onCheckedChange = { v -> viewModel.updateState { it.copy(publishToMobilePos = v) } },
        )

        if (state.isActive) {
            MonveriButton(
                text = "Deactivate product",
                onClick = { viewModel.updateState { it.copy(isActive = false) } },
                variant = MonveriButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Hides the product from the cashier catalog while keeping its history. " +
                    "Re-activate any time with the Active switch above, then Save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailsTab(state: BackOfficeProductEditorUiState, viewModel: BackOfficeProductEditorViewModel) {
    TabColumn {
        MonveriTextField(
            value = state.shortDesc,
            onValueChange = { v -> viewModel.updateState { it.copy(shortDesc = v) } },
            label = "Short description",
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.longDesc,
            onValueChange = { v -> viewModel.updateState { it.copy(longDesc = v) } },
            label = "Long description",
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        MonveriTextField(
            value = state.vendor,
            onValueChange = { v -> viewModel.updateState { it.copy(vendor = v) } },
            label = "Vendor",
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.vendorSku,
            onValueChange = { v -> viewModel.updateState { it.copy(vendorSku = v) } },
            label = "Vendor SKU",
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.url,
            onValueChange = { v -> viewModel.updateState { it.copy(url = v) } },
            label = "URL",
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.binLocation,
            onValueChange = { v -> viewModel.updateState { it.copy(binLocation = v) } },
            label = "Bin / Location",
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        MonveriTextField(
            value = state.unitOfSale,
            onValueChange = { v -> viewModel.updateState { it.copy(unitOfSale = v) } },
            label = "Unit of sale",
            helperText = "e.g. \"piece\" — an unrecognized unit code falls back to \"piece\" on save",
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.pricePerUnitText,
            onValueChange = { v -> viewModel.updateState { it.copy(pricePerUnitText = v) } },
            label = "Price per unit (optional)",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        SwitchRow(
            label = "Subtract from inventory on sale",
            checked = state.tracksStock,
            onCheckedChange = { v -> viewModel.updateState { it.copy(tracksStock = v) } },
        )
        MonveriTextField(
            value = state.reorderPointText,
            onValueChange = viewModel::onReorderPointChanged,
            label = "Reorder point",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.orderQuantityText,
            onValueChange = viewModel::onOrderQuantityChanged,
            label = "Order quantity",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PricingTab(state: BackOfficeProductEditorUiState, viewModel: BackOfficeProductEditorViewModel) {
    TabColumn {
        MonveriTextField(
            value = state.priceText,
            onValueChange = { v -> viewModel.updateState { it.copy(priceText = v) } },
            label = "Price",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.costText,
            onValueChange = { v -> viewModel.updateState { it.copy(costText = v) } },
            label = "Cost (optional)",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        MonveriTextField(
            value = state.salePriceText,
            onValueChange = { v -> viewModel.updateState { it.copy(salePriceText = v) } },
            label = "Sale price",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "MSRP, sale dates, and tax class aren't editable yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shared scrollable padded column every tab's content sits in. */
@Composable
internal fun TabColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
        content = content,
    )
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Product variants (size/color/pack SKUs) — staged locally, committed with the product's own
 * Save button (see [BackOfficeProductEditorViewModel]'s doc on why this differs from Images).
 */
@Composable
internal fun BackOfficeProductVariantsTab(
    state: BackOfficeProductEditorUiState,
    viewModel: BackOfficeProductEditorViewModel,
) {
    var editingKey by remember { mutableStateOf<Long?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.variants.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Filled.Apps,
                title = "No variants yet",
                message = "Variants let one product carry multiple SKUs — sizes, colors, " +
                    "packs, etc. Tap + to add the first one.",
                actionLabel = "Add variant",
                onAction = { editingKey = viewModel.onAddVariant() },
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.variants, key = { it.localKey }) { variant ->
                    VariantRow(
                        variant = variant,
                        onClick = { editingKey = variant.localKey },
                        onDelete = { viewModel.onDeleteVariant(variant.localKey) },
                    )
                    HorizontalDivider()
                }
            }
        }

        FloatingActionButton(
            onClick = { editingKey = viewModel.onAddVariant() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(MonveriSpacing.Lg),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add variant")
        }
    }

    val editing = state.variants.firstOrNull { it.localKey == editingKey }
    if (editing != null) {
        VariantEditDialog(
            variant = editing,
            onChange = { transform -> viewModel.onVariantChanged(editing.localKey, transform) },
            onDismiss = { editingKey = null },
        )
    }
}

@Composable
private fun VariantRow(variant: VariantUiModel, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val label = listOf(variant.variantName, variant.variantValue).filter { it.isNotBlank() }.joinToString(" — ")
            Text(label.ifBlank { "New variant" }, style = MaterialTheme.typography.bodyLarge)
            val subtitle = listOfNotNull(
                variant.sku.takeIf { it.isNotBlank() },
                "Qty ${variant.quantityText}",
            ).joinToString(" · ")
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete variant", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun VariantEditDialog(
    variant: VariantUiModel,
    onChange: ((VariantUiModel) -> VariantUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Variant") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md)) {
                MonveriTextField(
                    value = variant.variantName,
                    onValueChange = { v -> onChange { it.copy(variantName = v) } },
                    label = "Name (e.g. Size)",
                    modifier = Modifier.fillMaxWidth(),
                )
                MonveriTextField(
                    value = variant.variantValue,
                    onValueChange = { v -> onChange { it.copy(variantValue = v) } },
                    label = "Value (e.g. Large)",
                    modifier = Modifier.fillMaxWidth(),
                )
                MonveriTextField(
                    value = variant.sku,
                    onValueChange = { v -> onChange { it.copy(sku = v) } },
                    label = "SKU (optional)",
                    modifier = Modifier.fillMaxWidth(),
                )
                MonveriTextField(
                    value = variant.upc,
                    onValueChange = { v -> onChange { it.copy(upc = v) } },
                    label = "UPC (optional)",
                    modifier = Modifier.fillMaxWidth(),
                )
                MonveriTextField(
                    value = variant.priceText,
                    onValueChange = { v -> onChange { it.copy(priceText = v) } },
                    label = "Price (optional)",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                MonveriTextField(
                    value = variant.costText,
                    onValueChange = { v -> onChange { it.copy(costText = v) } },
                    label = "Cost (optional)",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
                MonveriTextField(
                    value = variant.quantityText,
                    onValueChange = { v -> onChange { it.copy(quantityText = v.filter(Char::isDigit)) } },
                    label = "Quantity",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

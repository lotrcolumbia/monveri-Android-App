package co.monveri.register.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.monveri.register.data.repository.Category
import co.monveri.register.data.repository.QuickButton
import co.monveri.register.data.repository.QuickButtonType
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriColors
import co.monveri.register.design.tokens.MonveriCornerRadius
import co.monveri.register.design.tokens.MonveriSpacing

// ---------------------------------------------------------------------------------------------
// Quick tab — admin-configured tiles mixing category "folders" and individual quick-add products.
// ---------------------------------------------------------------------------------------------

@Composable
fun QuickButtonsTab(
    buttons: List<ResolvedQuickButton>,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenProduct: (Long) -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Sm),
            )
        }
        when {
            isLoading && buttons.isEmpty() -> CenteredSpinner()
            buttons.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Filled.Add,
                title = "No quick buttons configured",
                message = "Set these up from Settings → Quick Buttons in the suite admin.",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = QUICK_TILE_SIZE),
                contentPadding = PaddingValues(MonveriSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
            ) {
                items(buttons, key = { it.button.id }) { resolved ->
                    QuickButtonTile(
                        button = resolved.button,
                        available = resolved.target != QuickButtonTarget.Unavailable,
                        onClick = {
                            when (val target = resolved.target) {
                                is QuickButtonTarget.OpenProduct -> onOpenProduct(target.productId)
                                is QuickButtonTarget.OpenCategory -> onOpenCategory(target.categoryId)
                                QuickButtonTarget.Unavailable -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickButtonTile(button: QuickButton, available: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(QUICK_TILE_SIZE)
            .alpha(if (available) 1f else 0.45f)
            .clip(RoundedCornerShape(MonveriCornerRadius.Md))
            .background(quickButtonColor(button.color))
            .then(if (available) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(MonveriSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when (button.type) {
                    QuickButtonType.PRODUCT -> Icons.Filled.Inventory2
                    QuickButtonType.CATEGORY -> Icons.Filled.Folder
                },
                contentDescription = null,
                tint = Color.White,
            )
            Text(
                text = button.label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MonveriSpacing.Xs),
            )
            if (!available) {
                Text(
                    text = "Unavailable",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Maps the admin's literal Bootstrap class string to a color — never inferred from
 * [QuickButtonType]. An unrecognized/blank value falls back to brand orange, matching iOS.
 */
private fun quickButtonColor(raw: String?): Color = when (raw?.trim()?.lowercase()) {
    "btn-success", "success", "gbg" -> MonveriColors.Success
    "btn-primary", "primary" -> MonveriColors.Secondary
    "btn-warning", "warning" -> MonveriColors.Warning
    "btn-danger", "danger" -> MonveriColors.Danger
    "btn-info", "info" -> MonveriColors.Info
    "btn-secondary", "secondary" -> MonveriColors.Neutral500
    "btn-dark", "dark" -> MonveriColors.Neutral800
    else -> MonveriColors.Primary
}

private val QUICK_TILE_SIZE = 110.dp

// ---------------------------------------------------------------------------------------------
// Categories tab — a flat, path-sorted, indent-rendered list (not a real nested folder browser;
// see CategoryProductsViewModel — tapping any row drills into it + every descendant's products).
// ---------------------------------------------------------------------------------------------

@Composable
fun CategoriesTab(categories: List<Category>, onCategorySelected: (String) -> Unit) {
    if (categories.isEmpty()) {
        EmptyState(
            modifier = Modifier.fillMaxSize(),
            icon = Icons.Filled.Folder,
            title = "No categories yet",
            message = "Pull down on Products to sync the catalog from the server.",
        )
        return
    }
    val entries = remember(categories) { sortedByPath(categories) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.category.id }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategorySelected(entry.category.id) }
                    .padding(
                        start = MonveriSpacing.Lg + (CATEGORY_INDENT_STEP * entry.depth),
                        end = MonveriSpacing.Lg,
                        top = MonveriSpacing.Md,
                        bottom = MonveriSpacing.Md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = MonveriColors.Primary)
                Text(entry.category.name, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider()
        }
    }
}

private data class CategoryEntry(val category: Category, val depth: Int, val path: String)

/** Depth-first visual order: parents sort above every one of their descendants. */
private fun sortedByPath(categories: List<Category>): List<CategoryEntry> {
    val byId = categories.associateBy { it.id }
    fun ancestorChain(start: Category): List<Category> {
        val chain = mutableListOf(start)
        var current = start
        var hops = 0
        while (current.parentId != null && hops < MAX_CATEGORY_DEPTH) {
            val parent = byId[current.parentId] ?: break
            chain.add(0, parent)
            current = parent
            hops++
        }
        return chain
    }
    return categories
        .map { category ->
            val chain = ancestorChain(category)
            CategoryEntry(
                category = category,
                depth = chain.size - 1,
                path = chain.joinToString(" / ") { it.name.lowercase() },
            )
        }
        .sortedBy { it.path }
}

private const val MAX_CATEGORY_DEPTH = 16
private val CATEGORY_INDENT_STEP = 18.dp

// ---------------------------------------------------------------------------------------------
// Custom tab — ad-hoc name + price entry, added straight to the cart with no backing product.
// ---------------------------------------------------------------------------------------------

@Composable
fun CustomItemTab(
    state: CustomItemUiState,
    onNameChanged: (String) -> Unit,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onTaxableChanged: (Boolean) -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        MonveriTextField(
            value = state.name,
            onValueChange = onNameChanged,
            label = "Item name",
            placeholder = "e.g. Gift wrap",
            modifier = Modifier.fillMaxWidth(),
        )

        MoneyText(
            cents = state.cents,
            style = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Taxable", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = state.taxable, onCheckedChange = onTaxableChanged)
        }

        CustomKeypad(onDigit = onDigit, onBackspace = onBackspace, onClear = onClear)

        MonveriButton(
            text = "Add to Cart",
            onClick = onAdd,
            enabled = state.cents > 0 && state.name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CustomKeypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { digit ->
                    OutlinedButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(KEYPAD_KEY_ASPECT_RATIO),
                    ) { Text(digit.toString(), style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(KEYPAD_KEY_ASPECT_RATIO),
            ) { Text("Clear") }
            OutlinedButton(
                onClick = { onDigit('0') },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(KEYPAD_KEY_ASPECT_RATIO),
            ) { Text("0", style = MaterialTheme.typography.headlineSmall) }
            OutlinedButton(
                onClick = onBackspace,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(KEYPAD_KEY_ASPECT_RATIO),
            ) { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace") }
        }
    }
}

private const val KEYPAD_KEY_ASPECT_RATIO = 1.6f

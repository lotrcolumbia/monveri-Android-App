package co.monveri.register.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.ProductVariant
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriCornerRadius
import co.monveri.register.design.tokens.MonveriSpacing
import kotlinx.coroutines.launch

/**
 * Product detail screen — image placeholder, name, price, variant chips, quantity stepper, and a
 * sticky add-to-cart button. Phase 3 doesn't render bundle/kit pickers (out-of-scope per plan).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    onAddedToCart: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.product?.name ?: "Product") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(snackbarData = it) } },
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            when {
                state.notFound -> NotFoundPanel(onBack)
                state.product == null -> Unit
                else -> ProductDetailContent(
                    state = state,
                    onVariantSelected = viewModel::onVariantSelected,
                    onIncrement = { viewModel.onQuantityChange(1) },
                    onDecrement = { viewModel.onQuantityChange(-1) },
                    onAddToCart = {
                        if (viewModel.addToCart()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Added to cart")
                            }
                            onAddedToCart()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProductDetailContent(
    state: ProductDetailUiState,
    onVariantSelected: (ProductVariant?) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onAddToCart: () -> Unit,
) {
    val product = state.product ?: return
    val scroll = rememberScrollState()

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scroll)
        .padding(MonveriSpacing.Lg)) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IMAGE_PLACEHOLDER_HEIGHT)
                .clip(RoundedCornerShape(MonveriCornerRadius.Md))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(MonveriSpacing.Lg))

        Text(text = product.name, style = MaterialTheme.typography.headlineSmall)

        product.categoryName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(MonveriSpacing.Md))

        MoneyText(
            cents = state.effectivePriceCents,
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(MonveriSpacing.Md))

        if (state.variants.isNotEmpty()) {
            VariantChips(
                variants = state.variants,
                selected = state.selectedVariant,
                onSelect = onVariantSelected,
            )
            Spacer(modifier = Modifier.height(MonveriSpacing.Md))
        }

        StockBadge(
            tracksStock = product.tracksStock,
            stock = state.effectiveStock,
        )

        Spacer(modifier = Modifier.height(MonveriSpacing.Xl))

        QuantityStepper(
            quantity = state.quantity,
            onDecrement = onDecrement,
            onIncrement = onIncrement,
        )

        Spacer(modifier = Modifier.height(MonveriSpacing.Xl))

        MonveriButton(
            text = "Add to cart",
            onClick = onAddToCart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantChips(
    variants: List<ProductVariant>,
    selected: ProductVariant?,
    onSelect: (ProductVariant?) -> Unit,
) {
    Text(
        text = "Options",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = MonveriSpacing.Sm),
    )
    LazyRow(
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
    ) {
        items(variants, key = { it.id }) { variant ->
            FilterChip(
                selected = selected?.id == variant.id,
                onClick = {
                    onSelect(if (selected?.id == variant.id) null else variant)
                },
                label = { Text(variant.displayLabel) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockBadge(tracksStock: Boolean, stock: Int) {
    if (!tracksStock) {
        AssistChip(
            onClick = {},
            label = { Text("Always in stock") },
            colors = AssistChipDefaults.assistChipColors(),
        )
        return
    }
    val label = when {
        stock <= 0 -> "Out of stock"
        stock < LOW_STOCK_THRESHOLD -> "Low stock — $stock left"
        else -> "In stock — $stock"
    }
    AssistChip(onClick = {}, label = { Text(label) })
}

@Composable
private fun QuantityStepper(quantity: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Quantity", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(MonveriSpacing.Lg))
        StepperButton(onClick = onDecrement, content = {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        })
        Spacer(modifier = Modifier.width(MonveriSpacing.Md))
        Text(text = quantity.toString(), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.width(MonveriSpacing.Md))
        StepperButton(onClick = onIncrement, content = {
            Text(text = "+", style = MaterialTheme.typography.headlineSmall)
        })
    }
}

@Composable
private fun StepperButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(STEPPER_BUTTON_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

@Composable
private fun NotFoundPanel(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonveriSpacing.Xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Product not found",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(MonveriSpacing.Md))
        Text(
            text = "Pull down on the catalog to sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(MonveriSpacing.Xl))
        MonveriButton(text = "Back to catalog", onClick = onBack)
    }
}

private val IMAGE_PLACEHOLDER_HEIGHT = 220.dp
private val STEPPER_BUTTON_SIZE = 44.dp
private const val LOW_STOCK_THRESHOLD: Int = 5

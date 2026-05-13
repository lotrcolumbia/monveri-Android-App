package co.monveri.register.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.Cart
import co.monveri.register.data.repository.CartLine
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriCornerRadius
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Cart screen — line items, customer attach/detach chip, discount editor, sticky totals bar
 * with checkout CTA.
 *
 * Phase 3 stubs the checkout flow (button shows a one-line "Checkout — Phase 6" message). Phase 6
 * replaces it with payment routing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onLookUpCustomer: () -> Unit,
    onCheckout: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val cart by viewModel.cartState.collectAsStateWithLifecycle()
    var showDiscountSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cart (${cart.itemCount})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (cart.lines.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearCart) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear cart")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (cart.lines.isNotEmpty()) {
                StickyTotalsBar(
                    cart = cart,
                    onEditDiscount = { showDiscountSheet = true },
                    onCheckout = onCheckout,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            CustomerChipRow(
                cart = cart,
                onLookUp = onLookUpCustomer,
                onDetach = viewModel::detachCustomer,
            )

            if (cart.lines.isEmpty()) {
                EmptyCartState(onLookUpCustomer)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(cart.lines, key = { it.lineId }) { line ->
                        CartLineRow(
                            line = line,
                            onIncrement = { viewModel.updateQuantity(line.lineId, line.quantity + 1) },
                            onDecrement = { viewModel.updateQuantity(line.lineId, line.quantity - 1) },
                            onRemove = { viewModel.removeLine(line.lineId) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showDiscountSheet) {
        DiscountPickerSheet(
            currentDiscountCents = cart.discountCents,
            subtotalCents = cart.totals.subtotalCents,
            onDismiss = { showDiscountSheet = false },
            onApply = { cents ->
                viewModel.setDiscount(cents)
                showDiscountSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerChipRow(cart: Cart, onLookUp: () -> Unit, onDetach: () -> Unit) {
    val attached = cart.customer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (attached == null) {
            AssistChip(
                onClick = onLookUp,
                leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                label = { Text("Attach customer") },
                colors = AssistChipDefaults.assistChipColors(),
            )
        } else {
            AssistChip(
                onClick = onLookUp,
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                label = {
                    val pts = if (attached.currentPoints > 0) " · ${attached.currentPoints} pts" else ""
                    Text("${attached.displayName}$pts", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
            Spacer(modifier = Modifier.width(MonveriSpacing.Sm))
            IconButton(onClick = onDetach) {
                Icon(Icons.Filled.Close, contentDescription = "Detach customer")
            }
        }
    }
}

@Composable
private fun CartLineRow(
    line: CartLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            line.variantLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            line.sku?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(
                cents = line.unitPriceCents * line.quantity,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(modifier = Modifier.width(MonveriSpacing.Sm))
        StepperBox(onDecrement = onDecrement, onIncrement = onIncrement, quantity = line.quantity)
        Spacer(modifier = Modifier.width(MonveriSpacing.Sm))
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove line",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun StepperBox(onDecrement: () -> Unit, onIncrement: () -> Unit, quantity: Int) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(MonveriCornerRadius.Md),
            )
            .padding(horizontal = MonveriSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDecrement) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrement")
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = MonveriSpacing.Xs),
        )
        IconButton(onClick = onIncrement) {
            Icon(Icons.Filled.Add, contentDescription = "Increment")
        }
    }
}

@Composable
private fun StickyTotalsBar(cart: Cart, onEditDiscount: () -> Unit, onCheckout: () -> Unit) {
    Surface(
        tonalElevation = TOTALS_TONAL_ELEVATION,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(MonveriSpacing.Lg)) {
            TotalsRow(label = "Subtotal", valueCents = cart.totals.subtotalCents)
            DiscountRow(
                discountCents = cart.totals.discountCents,
                onEditDiscount = onEditDiscount,
            )
            TotalsRow(label = "Tax", valueCents = cart.totals.taxCents)
            HorizontalDivider(modifier = Modifier.padding(vertical = MonveriSpacing.Sm))
            TotalsRow(
                label = "Total",
                valueCents = cart.totals.totalCents,
                emphasize = true,
            )
            Spacer(modifier = Modifier.height(MonveriSpacing.Md))
            MonveriButton(
                text = "Checkout",
                onClick = onCheckout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TotalsRow(label: String, valueCents: Long, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        )
        MoneyText(
            cents = valueCents,
            style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DiscountRow(discountCents: Long, onEditDiscount: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditDiscount),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (discountCents > 0L) "Discount (tap to edit)" else "Add discount",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        MoneyText(
            cents = -discountCents,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun EmptyCartState(onLookUpCustomer: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonveriSpacing.Xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(MonveriSpacing.Md))
        Text(text = "Cart is empty", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(MonveriSpacing.Sm))
        Text(
            text = "Add items from the catalog or scan a barcode.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(MonveriSpacing.Xl))
        MonveriButton(text = "Look up customer", onClick = onLookUpCustomer)
    }
}

private val TOTALS_TONAL_ELEVATION = 3.dp

package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Back Office landing menu — mirrors iOS's `BackOfficeHomeView` tile grid. Only two modules exist
 * so far (Products, Expenses); both are gated the same way iOS's own tiles are, by
 * [AuthViewModel.isCurrentEmployeeAdmin] one level up at [RegisterHomeScreen] — no per-tile
 * permission check yet, since the app doesn't sync the server's fine-grained permission map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackOfficeHomeScreen(
    onProductsRequested: () -> Unit,
    onExpensesRequested: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Back Office") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MonveriSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
        ) {
            HomeMenuRow(
                icon = Icons.Filled.ShoppingCart,
                title = "Products",
                subtitle = "Edit price, cost, quantity, and visibility",
                onClick = onProductsRequested,
            )
            HomeMenuRow(
                icon = Icons.Filled.Receipt,
                title = "Expenses",
                subtitle = "Track receipts and submit them for approval",
                onClick = onExpensesRequested,
            )
        }
    }
}

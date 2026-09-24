package co.monveri.register.feature.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.design.components.EmptyState

/**
 * Drill-in destination pushed from the Categories tab or a `category`-type Quick button. Shows
 * every product under the tapped category, including descendants — see
 * [CategoryProductsViewModel] for the aggregation rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryProductsScreen(
    onProductSelected: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CategoryProductsViewModel = hiltViewModel(),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.categoryName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
            if (products.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.Inventory2,
                    title = "No items in ${viewModel.categoryName}",
                )
            } else {
                ProductGrid(
                    products = products,
                    onTap = { onProductSelected(it.id) },
                    onLongPress = {},
                )
            }
        }
    }
}

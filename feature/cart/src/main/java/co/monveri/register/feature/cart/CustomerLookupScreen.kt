package co.monveri.register.feature.cart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.Customer
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Customer lookup screen. Live search across name / phone / email / loyalty card. Tap a row to
 * attach the customer to the active cart and pop back. Detach is available from the cart itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLookupScreen(
    onDismiss: () -> Unit,
    viewModel: CustomerLookupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.selectedCustomer) {
        if (state.selectedCustomer != null) {
            viewModel.consumeSelection()
            onDismiss()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Look up customer") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
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
                    .padding(MonveriSpacing.Lg),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Phone, email, name, or loyalty card") },
                singleLine = true,
                trailingIcon = {
                    when {
                        state.isSearching -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        state.query.isNotEmpty() -> IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                        else -> Unit
                    }
                },
            )

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = MonveriSpacing.Lg),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.query.isBlank() -> EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.Person,
                        title = "Search to begin",
                        message = "Start typing to find a customer.",
                    )
                    state.results.isEmpty() && !state.isSearching -> EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.Person,
                        title = "No customers match",
                        message = "Try a phone, email, or loyalty card.",
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.results, key = { it.id }) { customer ->
                            CustomerRow(
                                customer = customer,
                                isAttached = state.attached?.id == customer.id,
                                onSelect = { viewModel.selectCustomer(customer) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(customer: Customer, isAttached: Boolean, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Xs),
    ) {
        Text(
            text = customer.displayName + if (isAttached) " · attached" else "",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val secondary = listOfNotNull(customer.phone, customer.email)
            .joinToString(" · ")
            .ifBlank { null }
        secondary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val loyaltyLine = buildString {
            customer.loyaltyCardNumber?.takeIf { it.isNotBlank() }?.let { append("Card $it") }
            if (customer.currentPoints > 0) {
                if (isNotEmpty()) append(" · ")
                append("${customer.currentPoints} pts")
            }
            customer.tierName?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
        }
        if (loyaltyLine.isNotBlank()) {
            Text(
                text = loyaltyLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

package co.monveri.register.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.monveri.register.design.components.ConfirmActionsRow
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.components.ListItemRow
import co.monveri.register.design.components.LoadingShimmerRows
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.components.MonveriToastHost
import co.monveri.register.design.components.SectionHeader
import co.monveri.register.design.components.ToastKind
import co.monveri.register.design.components.showToast
import co.monveri.register.design.tokens.MonveriSpacing
import kotlinx.coroutines.launch

/**
 * Debug-only screen that renders every reusable composable. Lets us catch theme regressions
 * (light/dark contrast, padding) without launching individual flows. Never compiled into release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentGalleryScreen(onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var textFieldValue by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Component Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { MonveriToastHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = MonveriSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Md),
        ) {
            SectionHeader(title = "Buttons")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MonveriSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
            ) {
                MonveriButton(text = "Primary", onClick = {}, modifier = Modifier.fillMaxWidth())
                MonveriButton(
                    text = "Secondary",
                    onClick = {},
                    variant = MonveriButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                MonveriButton(
                    text = "Destructive",
                    onClick = {},
                    variant = MonveriButtonVariant.Destructive,
                    modifier = Modifier.fillMaxWidth(),
                )
                MonveriButton(
                    text = "Loading",
                    onClick = {},
                    loading = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader(title = "Text field")
            Column(modifier = Modifier.padding(horizontal = MonveriSpacing.Lg)) {
                MonveriTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = "Search",
                    placeholder = "Type something",
                    helperText = "Helper text appears below",
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                )
            }

            SectionHeader(title = "Money")
            Column(
                modifier = Modifier.padding(horizontal = MonveriSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Xs),
            ) {
                MoneyText(cents = 12_345)
                MoneyText(cents = -500, signColored = true)
                MoneyText(cents = 0, boldZero = true)
            }

            SectionHeader(title = "List rows")
            ListItemRow(
                title = "Carolina Thread Place",
                subtitle = "spaw66yk0wxk · paired",
                onClick = {},
            )
            ListItemRow(
                title = "Espresso",
                subtitle = "12 oz · cold brew",
                trailing = { MoneyText(cents = 425) },
            )

            SectionHeader(title = "Empty state")
            EmptyState(
                icon = Icons.Filled.Search,
                title = "No matches",
                message = "Try a different search term.",
                actionLabel = "Clear filters",
                onAction = {},
            )

            SectionHeader(title = "Shimmer")
            LoadingShimmerRows(rowCount = 3)

            SectionHeader(title = "Toasts")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MonveriSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
            ) {
                ToastKind.entries.forEach { kind ->
                    MonveriButton(
                        text = "Show ${kind.name} toast",
                        variant = MonveriButtonVariant.Secondary,
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showToast("$kind sample", kind)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionHeader(title = "Confirm row")
            ConfirmActionsRow(
                confirmLabel = "Delete",
                onConfirm = {},
                onCancel = {},
                confirmVariant = MonveriButtonVariant.Destructive,
            )

            Text(
                text = "All composables shown. Switch system theme to verify dark mode contrast.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MonveriSpacing.Lg, vertical = MonveriSpacing.Md),
            )
        }
    }
}

package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.data.repository.ExpenseCategory
import co.monveri.register.data.repository.ExpensePaymentMethod
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.tokens.MonveriSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Manual expense entry — the "Enter manually" half of iOS's two-option add flow. The "Scan
 * receipt" camera + on-device OCR path (VisionKit on iOS; CameraX + ML Kit on Android, per
 * `PLAN_Android_Phase8_ReceiptScanner.php`) is a separate, larger follow-up — deliberately not
 * half-built here. This form stands on its own: it's exactly iOS's second entry path, complete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual Expense") },
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
                .verticalScroll(rememberScrollState())
                .padding(MonveriSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
        ) {
            MonveriTextField(
                value = state.vendorName,
                onValueChange = viewModel::onVendorChanged,
                label = "Vendor",
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(dateText = state.dateText, onDateChanged = viewModel::onDateChanged)

            MonveriTextField(
                value = state.totalText,
                onValueChange = viewModel::onTotalChanged,
                label = "Total",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            MonveriTextField(
                value = state.taxText,
                onValueChange = viewModel::onTaxChanged,
                label = "Tax (optional)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            MonveriTextField(
                value = state.subtotalText,
                onValueChange = viewModel::onSubtotalChanged,
                label = "Subtotal (optional)",
                helperText = "Leave blank to derive from Total − Tax",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryField(
                categories = state.categories,
                selectedId = state.categoryId,
                onSelected = viewModel::onCategorySelected,
            )
            PaymentMethodField(
                selected = state.paymentMethod,
                onSelected = viewModel::onPaymentMethodSelected,
            )

            MonveriTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                label = "Notes (optional)",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            MonveriButton(
                text = if (state.isSaving) "Saving…" else "Save Expense",
                onClick = viewModel::save,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(dateText: String, onDateChanged: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Date: $dateText")
    }

    if (showPicker) {
        val initialMillis = runCatching {
            LocalDate.parse(dateText).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateChanged(date.toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun CategoryField(
    categories: List<ExpenseCategory>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: "Select a category"

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedName, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelected(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodField(
    selected: ExpensePaymentMethod,
    onSelected: (ExpensePaymentMethod) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExpensePaymentMethod.entries.forEach { method ->
                DropdownMenuItem(
                    text = { Text(method.label) },
                    onClick = {
                        onSelected(method)
                        expanded = false
                    },
                )
            }
        }
    }
}

package co.monveri.register.feature.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.tokens.MonveriSpacing

/** Third-party tender (Cash App, Zelle, PayPal, etc.) with a free-text reference. Matches iOS's `OtherPaymentView`. */
@Composable
internal fun OtherTenderContent(viewModel: CheckoutViewModel) {
    var service by remember { mutableStateOf(OtherTenderService.CASH_APP) }
    var reference by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
    ) {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(service.label, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                OtherTenderService.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { service = option; expanded = false },
                    )
                }
            }
        }

        MonveriTextField(
            value = reference,
            onValueChange = { reference = it },
            label = "Reference (optional)",
            modifier = Modifier.fillMaxWidth(),
        )

        MonveriButton(
            text = "Confirm ${service.label}",
            onClick = { viewModel.confirmOther(service.label, reference) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

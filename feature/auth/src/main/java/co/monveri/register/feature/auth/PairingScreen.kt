package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Manual pairing form: user pastes store URL + API key from the back office, taps Pair.
 *
 * The plan calls for a 6-digit code; the real backend expects the full API key. Future Phase 3
 * adds a camera-based QR scanner that delivers the same fields in one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.pairing.collectAsState()

    LaunchedEffect(state.pairedStoreName) {
        if (state.pairedStoreName != null) onPaired()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pair this device") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Enter the store URL and API key from your back office to bind this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onPairingBaseUrlChanged,
                label = { Text("Store URL") },
                placeholder = { Text("https://store.example") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onPairingApiKeyChanged,
                label = { Text("API key") },
                placeholder = { Text("paste from register_api_keys table") },
                singleLine = true,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = viewModel::pair,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 12.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (state.isLoading) "Pairing…" else "Pair this device")
            }

            Text(
                text = "QR-code scan is coming in Phase 3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

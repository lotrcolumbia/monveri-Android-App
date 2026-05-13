package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriTextField
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Manual pairing form: user pastes store URL + API key from the back office, taps Pair.
 *
 * The plan calls for a 6-digit code; the real backend expects the full API key. A camera-based
 * QR scanner that delivers the same fields in one tap is on the roadmap for a later phase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.pairing.collectAsStateWithLifecycle()
    var apiKeyVisible by remember { mutableStateOf(false) }

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
                .padding(horizontal = MonveriSpacing.Xl, vertical = MonveriSpacing.Xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg),
        ) {
            Text(
                text = "Enter the store URL and API key from your back office to bind this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MonveriTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onPairingBaseUrlChanged,
                label = "Store URL",
                placeholder = "https://store.example",
                keyboardType = KeyboardType.Uri,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            MonveriTextField(
                value = state.apiKey,
                onValueChange = viewModel::onPairingApiKeyChanged,
                label = "API key",
                placeholder = "paste from register_api_keys table",
                keyboardType = KeyboardType.Password,
                enabled = !state.isLoading,
                visualTransformation = if (apiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key",
                        )
                    }
                },
                errorMessage = state.errorMessage,
                modifier = Modifier.fillMaxWidth(),
            )

            MonveriButton(
                text = if (state.isLoading) "Pairing…" else "Pair this device",
                onClick = viewModel::pair,
                loading = state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MonveriSpacing.Sm),
            )

            Text(
                text = "Tip: paste the store URL and API key from the back office.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

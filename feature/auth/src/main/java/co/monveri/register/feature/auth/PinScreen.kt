package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val PIN_LENGTH = 4

/**
 * 4-digit numeric pad. On the fourth digit, the ViewModel auto-submits. A successful login
 * fires `onAuthenticated` once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreen(
    onAuthenticated: () -> Unit,
    onUnpair: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.login.collectAsStateWithLifecycle()

    LaunchedEffect(state.employee) {
        if (state.employee != null) onAuthenticated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter PIN") },
                actions = {
                    TextButton(
                        onClick = {
                            // Clear persisted pairing + session state BEFORE navigating so a
                            // subsequent cold start can't route back to PIN against the old key.
                            viewModel.unpair()
                            onUnpair()
                        },
                    ) { Text("Unpair") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            PinDots(filled = state.pin.length)

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Keypad(
                onDigit = viewModel::onPinDigit,
                onBackspace = viewModel::onPinBackspace,
                onClear = viewModel::clearPin,
                enabled = !state.isLoading,
            )
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PIN_LENGTH) { index ->
            val isFilled = index < filled
            Surface(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape),
                color = if (isFilled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape,
                content = {},
            )
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { digit ->
                    KeypadButton(
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        onClick = { onDigit(digit) },
                    ) { Text(digit.toString(), style = MaterialTheme.typography.headlineMedium) }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            KeypadButton(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = onClear,
            ) { Text("Clear", style = MaterialTheme.typography.titleMedium) }
            KeypadButton(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onDigit('0') },
            ) { Text("0", style = MaterialTheme.typography.headlineMedium) }
            KeypadButton(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = onBackspace,
            ) {
                Icon(
                    imageVector = Icons.Filled.Backspace,
                    contentDescription = "Backspace",
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.aspectRatio(2f),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

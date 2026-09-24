package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MoneyText
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * "Open a till session" — a starting cash count, then one tap. Mirrors iOS's `OpenRegisterView`,
 * which sits between PIN login and the catalog whenever this (employee, device) pair has no
 * session open yet (see [AuthViewModel.needsRegisterOpen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterOpenScreen(
    onOpened: () -> Unit,
    onUnpair: () -> Unit,
    viewModel: RegisterOpenViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.opened) {
        if (state.opened) onOpened()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open register") },
                actions = {
                    TextButton(onClick = onUnpair) { Text("Unpair") }
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
            Text(
                text = "Count the starting cash in the drawer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MoneyText(
                cents = state.openingCents,
                style = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            CentsKeypad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onClear = viewModel::onClear,
            )

            MonveriButton(
                text = if (state.isLoading) "Opening…" else "Open register",
                onClick = viewModel::openRegister,
                loading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A 0-9 + Clear/Backspace grid for cents-shift entry (no decimal key) — same pattern used by the
 * catalog's Custom-item tab and the PIN pad, kept as its own small copy here since feature
 * modules don't depend on each other. */
@Composable
internal fun CentsKeypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    Column(verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { digit ->
                    OutlinedButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(KEYPAD_KEY_ASPECT_RATIO),
                    ) { Text(digit.toString(), style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(KEYPAD_KEY_ASPECT_RATIO),
            ) { Text("Clear") }
            OutlinedButton(
                onClick = { onDigit('0') },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(KEYPAD_KEY_ASPECT_RATIO),
            ) { Text("0", style = MaterialTheme.typography.headlineSmall) }
            OutlinedButton(
                onClick = onBackspace,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(KEYPAD_KEY_ASPECT_RATIO),
            ) { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace") }
        }
    }
}

private const val KEYPAD_KEY_ASPECT_RATIO = 1.6f

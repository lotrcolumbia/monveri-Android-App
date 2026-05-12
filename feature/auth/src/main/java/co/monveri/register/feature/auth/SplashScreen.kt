package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.monveri.register.model.AuthState

/**
 * Reads the persisted auth state once and routes accordingly. Pure UI — no work beyond the
 * `LaunchedEffect`. Splash content is intentionally minimal; the goal is to disappear quickly.
 */
@Composable
fun SplashScreen(
    onUnpaired: () -> Unit,
    onPairedNoSession: () -> Unit,
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (state) {
            AuthState.Unpaired -> onUnpaired()
            AuthState.PairedNoSession -> onPairedNoSession()
            AuthState.Authenticated -> onAuthenticated()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Monveri Register",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

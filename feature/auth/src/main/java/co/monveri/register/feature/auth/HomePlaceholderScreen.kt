package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Stub home screen. Phase 3 replaces this with the real catalog/register surface.
 * Today it just confirms the auth flow worked end-to-end and offers a logout button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePlaceholderScreen(
    onLoggedOut: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val session = remember { viewModel.currentSession() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(session?.storeName ?: "Monveri Register") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Signed in as ${session?.employee?.name ?: "Unknown"}",
                style = MaterialTheme.typography.headlineSmall,
            )
            session?.employee?.username?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Phase 1 complete — catalog & cart arrive in Phase 3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    viewModel.logout()
                    onLoggedOut()
                },
            ) { Text("Sign out") }
        }
    }
}

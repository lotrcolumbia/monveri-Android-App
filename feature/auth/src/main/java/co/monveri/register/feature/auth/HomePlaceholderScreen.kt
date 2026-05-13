package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Stub home screen. Phase 3 replaces this with the real catalog/register surface.
 * Today it just confirms the auth flow worked end-to-end and offers a logout button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePlaceholderScreen(
    onLoggedOut: () -> Unit,
    onShowGallery: (() -> Unit)? = null,
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
                .padding(horizontal = MonveriSpacing.Xl, vertical = MonveriSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Lg, Alignment.CenterVertically),
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
                text = "Phase 2 foundation in place — catalog & cart arrive in Phase 3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MonveriButton(
                text = "Sign out",
                onClick = {
                    viewModel.logout()
                    onLoggedOut()
                },
                variant = MonveriButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            if (onShowGallery != null) {
                MonveriButton(
                    text = "Component gallery (debug)",
                    onClick = onShowGallery,
                    variant = MonveriButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

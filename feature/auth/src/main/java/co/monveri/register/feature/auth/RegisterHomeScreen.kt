package co.monveri.register.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * Lands here after PIN login whenever this (employee, device) pair has no register session open
 * yet — mirrors iOS's post-login home: a small menu rather than an automatic redirect, since an
 * admin might want Back Office without ringing up a single sale. "Open Register" is available to
 * everyone; "Back Office" only to admins ([AuthViewModel.isCurrentEmployeeAdmin], `level == 1`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterHomeScreen(
    onOpenRegisterRequested: () -> Unit,
    onBackOfficeRequested: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                actions = {
                    TextButton(onClick = {
                        viewModel.logout()
                        onLoggedOut()
                    }) { Text("Log out") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MonveriSpacing.Lg),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Monveri Register",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                viewModel.currentEmployeeName?.let { name ->
                    Text(
                        text = "Signed in as $name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(MonveriSpacing.Xxl))

            HomeMenuRow(
                icon = Icons.Filled.ShoppingCart,
                title = "Open Register",
                subtitle = "Start a till for ringing up sales",
                onClick = onOpenRegisterRequested,
            )

            if (viewModel.isCurrentEmployeeAdmin) {
                Spacer(modifier = Modifier.height(MonveriSpacing.Md))
                HomeMenuRow(
                    icon = Icons.Filled.Apartment,
                    title = "Back Office",
                    subtitle = "Products, expenses, and more",
                    onClick = onBackOfficeRequested,
                )
            }
        }
    }
}

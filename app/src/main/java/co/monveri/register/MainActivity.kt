package co.monveri.register

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import co.monveri.register.design.MonveriTheme
import co.monveri.register.navigation.MonveriNavGraph
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity host for the entire app. Owns the Compose root and navigation controller;
 * everything else lives in feature modules.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MonveriTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MonveriNavGraph(navController = rememberNavController())
                }
            }
        }
    }
}

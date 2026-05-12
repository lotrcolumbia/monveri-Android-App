package co.monveri.register.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Phase 1 ships a minimal palette anchored to the Monveri brand colors. Phase 2 expands this
// into the full design-system token set with light/dark variants and typography.
private val MonveriOrange = Color(0xFFE57E00)
private val MonveriOrangeDark = Color(0xFFB36300)
private val MonveriBlue = Color(0xFF025B92)
private val MonveriBlueDark = Color(0xFF014673)

private val LightColors = lightColorScheme(
    primary = MonveriOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF402100),
    secondary = MonveriBlue,
    onSecondary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = MonveriOrange,
    onPrimary = Color.Black,
    primaryContainer = MonveriOrangeDark,
    onPrimaryContainer = Color(0xFFFFE0B2),
    secondary = MonveriBlue,
    onSecondary = Color.White,
    secondaryContainer = MonveriBlueDark,
)

@Composable
fun MonveriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

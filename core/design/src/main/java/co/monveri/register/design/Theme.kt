package co.monveri.register.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import co.monveri.register.design.tokens.MonveriColors
import co.monveri.register.design.tokens.MonveriTypography

/**
 * Status colors (success/warning/danger/info) live outside Material 3's ColorScheme — they're
 * surfaced via this CompositionLocal so screens can pull them via [MonveriTheme.statusColors].
 */
data class MonveriStatusColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val danger: Color,
    val onDanger: Color,
    val info: Color,
    val onInfo: Color,
)

private val LightStatusColors = MonveriStatusColors(
    success = MonveriColors.Success,
    onSuccess = Color.White,
    warning = MonveriColors.Warning,
    onWarning = MonveriColors.Neutral900,
    danger = MonveriColors.Danger,
    onDanger = Color.White,
    info = MonveriColors.Info,
    onInfo = Color.White,
)

private val DarkStatusColors = LightStatusColors

val LocalMonveriStatusColors = staticCompositionLocalOf { LightStatusColors }

private val LightColors = lightColorScheme(
    primary = MonveriColors.Primary,
    onPrimary = Color.White,
    primaryContainer = MonveriColors.PrimaryTint,
    onPrimaryContainer = MonveriColors.Neutral900,
    secondary = MonveriColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = MonveriColors.SecondaryTint,
    onSecondaryContainer = MonveriColors.Neutral900,
    tertiary = MonveriColors.SecondaryDark,
    onTertiary = Color.White,
    background = MonveriColors.Neutral0,
    onBackground = MonveriColors.Neutral900,
    surface = MonveriColors.Neutral0,
    onSurface = MonveriColors.Neutral900,
    surfaceVariant = MonveriColors.Neutral100,
    onSurfaceVariant = MonveriColors.Neutral700,
    outline = MonveriColors.Neutral300,
    outlineVariant = MonveriColors.Neutral200,
    error = MonveriColors.Danger,
    onError = Color.White,
    scrim = Color(0x80000000),
)

private val DarkColors = darkColorScheme(
    primary = MonveriColors.Primary,
    onPrimary = Color.Black,
    primaryContainer = MonveriColors.PrimaryDark,
    onPrimaryContainer = MonveriColors.PrimaryTint,
    secondary = MonveriColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = MonveriColors.SecondaryDark,
    onSecondaryContainer = MonveriColors.SecondaryTint,
    tertiary = MonveriColors.SecondaryDark,
    onTertiary = Color.White,
    background = MonveriColors.Neutral900,
    onBackground = MonveriColors.Neutral50,
    surface = MonveriColors.Neutral800,
    onSurface = MonveriColors.Neutral50,
    surfaceVariant = MonveriColors.Neutral700,
    onSurfaceVariant = MonveriColors.Neutral200,
    outline = MonveriColors.Neutral600,
    outlineVariant = MonveriColors.Neutral700,
    error = MonveriColors.Danger,
    onError = Color.White,
    scrim = Color(0x80000000),
)

/**
 * App-wide theme wrapper. Wraps Material 3 with the Monveri brand palette + status colors.
 */
@Composable
fun MonveriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors
    CompositionLocalProvider(LocalMonveriStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MonveriTypography,
            content = content,
        )
    }
}

/** Convenience accessor mirroring `MaterialTheme.colorScheme` for status colors. */
object MonveriTheme {
    val statusColors: MonveriStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMonveriStatusColors.current
}

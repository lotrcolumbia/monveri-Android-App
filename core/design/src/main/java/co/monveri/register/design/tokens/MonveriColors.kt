package co.monveri.register.design.tokens

import androidx.compose.ui.graphics.Color

/**
 * Raw brand color tokens. Mirrors the iOS `MonveriColors` enum and the suite's
 * `css/design-system.css` so all three surfaces speak the same hex.
 *
 * Composables should consume these via [MaterialTheme.colorScheme] (brand) or the
 * `LocalMonveriStatusColors` CompositionLocal (status), not these raw values directly —
 * the indirection lets dark mode override them.
 */
object MonveriColors {
    // Brand
    val Primary = Color(0xFFE57E00)
    val PrimaryDark = Color(0xFFD06E00)
    val PrimaryTint = Color(0xFFFFF8F0)

    val Secondary = Color(0xFF025B92)
    val SecondaryDark = Color(0xFF014773)
    val SecondaryTint = Color(0xFFE8F4F8)

    // Neutral surfaces — derived from the suite's `css/design-system.css` neutral ramp
    val Neutral0 = Color(0xFFFFFFFF)
    val Neutral50 = Color(0xFFFAFAFA)
    val Neutral100 = Color(0xFFF5F5F5)
    val Neutral200 = Color(0xFFEEEEEE)
    val Neutral300 = Color(0xFFE0E0E0)
    val Neutral400 = Color(0xFFBDBDBD)
    val Neutral500 = Color(0xFF9E9E9E)
    val Neutral600 = Color(0xFF757575)
    val Neutral700 = Color(0xFF616161)
    val Neutral800 = Color(0xFF424242)
    val Neutral900 = Color(0xFF1A1A1A)

    // Status — match iOS `MonveriColors`
    val Success = Color(0xFF28A745)
    val Warning = Color(0xFFFFC107)
    val Danger = Color(0xFFDC3545)
    val Info = Color(0xFF17A2B8)
}

package co.monveri.register.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.monveri.register.design.tokens.MonveriCornerRadius
import co.monveri.register.design.tokens.MonveriSpacing

/**
 * A single shimmering placeholder bar. Use [LoadingShimmerRows] for typical list skeletons.
 *
 * The animation runs a linear gradient across the X axis, repeating forever — Compose
 * automatically tears it down when the composable leaves the composition.
 */
@Composable
fun LoadingShimmer(
    modifier: Modifier = Modifier,
    cornerRadius: Float = MonveriCornerRadius.Md.value,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = SHIMMER_INITIAL_X,
        targetValue = SHIMMER_TARGET_X,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(translate, 0f),
        end = androidx.compose.ui.geometry.Offset(translate + SHIMMER_BAND_WIDTH, 0f),
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(Color.Transparent)
            .background(brush),
    )
}

/**
 * Stack of N shimmer rows, sized like a typical list item. Drop this in while a list loads.
 */
@Composable
fun LoadingShimmerRows(
    rowCount: Int = DEFAULT_ROW_COUNT,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = MonveriSpacing.Lg)) {
        repeat(rowCount) {
            LoadingShimmer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = MonveriSpacing.Xs),
            )
            Spacer(modifier = Modifier.height(MonveriSpacing.Xs))
        }
    }
}

private const val SHIMMER_INITIAL_X = 0f
private const val SHIMMER_TARGET_X = 1500f
private const val SHIMMER_DURATION_MS = 1200
private const val SHIMMER_BAND_WIDTH = 400f
private const val DEFAULT_ROW_COUNT = 6

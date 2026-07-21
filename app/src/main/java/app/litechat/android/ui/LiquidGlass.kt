package app.litechat.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A restrained glass panel: translucent depth, a crisp rim and a slowly drifting refractive
 * highlight. It deliberately avoids opaque cards so the ambient canvas remains visible.
 */
internal val LocalLiquidGlassPhase = staticCompositionLocalOf { 0f }

@Composable
internal fun rememberLiquidGlassPhase(): Float {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "glass-refraction")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduceMotion) 0f else 1f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing)),
        label = "glass-refraction-phase"
    ).value
}

@Composable
internal fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
    shadowElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val phase = LocalLiquidGlassPhase.current
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = shape,
        color = color.copy(alpha = 0.68f),
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation
    ) {
        Box(
            Modifier.drawBehind {
                // A thin, moving refraction band makes the surface feel like glass without
                // competing with reading content or relying on heavy blur effects.
                val shift = (phase * 1.4f - 0.7f) * size.width
                val band = Path().apply {
                    moveTo(shift - size.width * 0.35f, 0f)
                    cubicTo(
                        shift, size.height * 0.05f,
                        shift + size.width * 0.18f, size.height * 0.2f,
                        shift + size.width * 0.7f, size.height * 0.12f
                    )
                    lineTo(shift + size.width * 0.7f, size.height * 0.19f)
                    cubicTo(
                        shift + size.width * 0.16f, size.height * 0.29f,
                        shift, size.height * 0.12f,
                        shift - size.width * 0.35f, size.height * 0.07f
                    )
                    close()
                }
                drawPath(band, colors.onSurface.copy(alpha = 0.035f))
                drawLine(
                    color = Color.White.copy(alpha = 0.13f),
                    start = androidx.compose.ui.geometry.Offset(18.dp.toPx(), 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width - 18.dp.toPx(), 0f),
                    strokeWidth = 0.8.dp.toPx()
                )
            },
            content = content
        )
    }
}

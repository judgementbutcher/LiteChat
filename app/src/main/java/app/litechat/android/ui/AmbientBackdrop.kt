package app.litechat.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.sin

/** A low-contrast flowing light field behind the translucent app surfaces. */
@Composable
internal fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "ambient-backdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduceMotion) 0f else 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)),
        label = "ambient-phase"
    )
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(colors.background, colors.surfaceContainerLowest, colors.background)))

        val cycle = phase * (2f * PI.toFloat())
        val drift = size.width * (0.16f + sin(cycle) * 0.08f)
        val primaryRibbon = Path().apply {
            moveTo(-size.width * 0.3f + drift, -size.height * 0.08f)
            cubicTo(
                size.width * 0.24f + drift, size.height * 0.12f,
                size.width * 0.46f - drift * 0.2f, size.height * 0.32f,
                size.width * 1.15f, size.height * 0.18f
            )
            lineTo(size.width * 1.15f, size.height * 0.38f)
            cubicTo(
                size.width * 0.54f, size.height * 0.54f,
                size.width * 0.22f + drift * 0.35f, size.height * 0.3f,
                -size.width * 0.3f + drift, size.height * 0.14f
            )
            close()
        }
        val secondaryRibbon = Path().apply {
            moveTo(-size.width * 0.1f, size.height * 0.65f)
            cubicTo(
                size.width * 0.3f - drift * 0.15f, size.height * 0.48f,
                size.width * 0.72f + drift * 0.2f, size.height * 0.92f,
                size.width * 1.1f, size.height * 0.72f
            )
            lineTo(size.width * 1.1f, size.height * 0.88f)
            cubicTo(
                size.width * 0.66f, size.height * 1.04f,
                size.width * 0.25f, size.height * 0.65f,
                -size.width * 0.1f, size.height * 0.82f
            )
            close()
        }
        drawPath(primaryRibbon, colors.primary.copy(alpha = 0.065f))
        drawPath(secondaryRibbon, colors.tertiary.copy(alpha = 0.045f))

        val grid = 56f * density
        var x = 0f
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.018f), Offset(x, 0f), Offset(x, size.height), 1f)
            x += grid
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Color.White.copy(alpha = 0.014f), Offset(0f, y), Offset(size.width, y), 1f)
            y += grid
        }
    }
}

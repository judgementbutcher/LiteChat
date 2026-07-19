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
import androidx.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.sin

/** A low-contrast animated light field behind the translucent app surfaces. */
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
        drawRect(
            Brush.verticalGradient(
                listOf(colors.background, colors.surfaceContainerLowest, colors.background)
            )
        )

        val cycle = phase * (2f * PI.toFloat())
        val radius = size.minDimension * 0.72f
        val primaryCenter = Offset(
            size.width * (0.12f + 0.12f * sin(cycle)),
            size.height * (0.14f + 0.07f * sin(cycle * 0.7f))
        )
        val secondaryCenter = Offset(
            size.width * (0.88f + 0.08f * sin(cycle + 2.1f)),
            size.height * (0.72f + 0.09f * sin(cycle * 0.6f + 1.2f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.primary.copy(alpha = 0.13f), Color.Transparent),
                center = primaryCenter,
                radius = radius
            ),
            radius = radius,
            center = primaryCenter
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.tertiary.copy(alpha = 0.09f), Color.Transparent),
                center = secondaryCenter,
                radius = radius * 0.9f
            ),
            radius = radius * 0.9f,
            center = secondaryCenter
        )

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

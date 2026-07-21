package app.litechat.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A flat, opaque surface in the ChatGPT-style visual language: a solid fill, an optional hairline
 * border and a soft shadow. It replaced an earlier translucent "liquid glass" panel; the name and
 * signature are kept so every call site stays a one-liner, but there is no blur, translucency or
 * moving refraction any more — the app now reads as calm and flat.
 *
 * Pass [borderColor] = [Color.Transparent] for a borderless fill (e.g. chat bubbles).
 */
@Composable
internal fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    shadowElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (borderColor == Color.Transparent) null else BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation
    ) {
        Box(content = content)
    }
}

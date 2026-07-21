package app.litechat.android.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.litechat.android.data.settings.ThemeMode
import app.litechat.android.ui.rememberReduceMotion

// A neutral, near-monochrome palette modelled on the ChatGPT app: a calm off-white/near-black
// canvas, grey user bubbles and a single high-contrast accent used sparingly (send button, active
// controls). Links get their own blue in MarkdownContent so they stay recognisable against the
// otherwise colourless scheme.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0D0D0D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECECEC),
    onPrimaryContainer = Color(0xFF0D0D0D),
    secondary = Color(0xFF5D5D5D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEDED),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF5D5D5D),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0D0D0D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF676767),
    outline = Color(0xFFB4B4B4),
    outlineVariant = Color(0xFFE5E5E5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFE6E6E6),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFECECEC),
    onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF303030),
    onPrimaryContainer = Color(0xFFECECEC),
    secondary = Color(0xFFB4B4B4),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF2F2F2F),
    onSecondaryContainer = Color(0xFFECECEC),
    tertiary = Color(0xFFB4B4B4),
    background = Color(0xFF212121),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF212121),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF303030),
    onSurfaceVariant = Color(0xFFB4B4B4),
    outline = Color(0xFF676767),
    outlineVariant = Color(0xFF3D3D3D),
    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF212121),
    surfaceContainer = Color(0xFF2A2A2A),
    surfaceContainerHigh = Color(0xFF303030),
    surfaceContainerHighest = Color(0xFF363636),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5A2E2A)
)

private val LiteChatShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val LiteChatTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 31.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp)
)

@Composable
fun LiteChatTheme(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    // The default look is a neutral, ChatGPT-style palette in both light and dark. Dynamic color
    // (Material You) remains an opt-in override for light mode only, so enabling it never disturbs
    // the calm dark canvas.
    val target = if (dynamicColor && !dark) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    val colors = target.animated(if (rememberReduceMotion()) 0 else 300)
    MaterialTheme(colorScheme = colors, typography = LiteChatTypography, shapes = LiteChatShapes, content = content)
}

/**
 * Cross-fades every visible color role toward [this] scheme so switching between light, dark and
 * dynamic palettes glides instead of snapping. A duration of 0 (reduced motion) makes it a no-op
 * snap while keeping the composable call structure constant.
 */
@Composable
private fun ColorScheme.animated(durationMillis: Int): ColorScheme {
    val spec = tween<Color>(durationMillis)
    @Composable fun anim(target: Color) = animateColorAsState(target, spec, label = "themeColor").value
    return copy(
        primary = anim(primary),
        onPrimary = anim(onPrimary),
        primaryContainer = anim(primaryContainer),
        onPrimaryContainer = anim(onPrimaryContainer),
        secondary = anim(secondary),
        onSecondary = anim(onSecondary),
        secondaryContainer = anim(secondaryContainer),
        onSecondaryContainer = anim(onSecondaryContainer),
        tertiary = anim(tertiary),
        onTertiary = anim(onTertiary),
        tertiaryContainer = anim(tertiaryContainer),
        onTertiaryContainer = anim(onTertiaryContainer),
        background = anim(background),
        onBackground = anim(onBackground),
        surface = anim(surface),
        onSurface = anim(onSurface),
        surfaceVariant = anim(surfaceVariant),
        onSurfaceVariant = anim(onSurfaceVariant),
        surfaceTint = anim(surfaceTint),
        inverseSurface = anim(inverseSurface),
        inverseOnSurface = anim(inverseOnSurface),
        error = anim(error),
        onError = anim(onError),
        errorContainer = anim(errorContainer),
        onErrorContainer = anim(onErrorContainer),
        outline = anim(outline),
        outlineVariant = anim(outlineVariant),
        surfaceBright = anim(surfaceBright),
        surfaceDim = anim(surfaceDim),
        surfaceContainerLowest = anim(surfaceContainerLowest),
        surfaceContainerLow = anim(surfaceContainerLow),
        surfaceContainer = anim(surfaceContainer),
        surfaceContainerHigh = anim(surfaceContainerHigh),
        surfaceContainerHighest = anim(surfaceContainerHighest)
    )
}

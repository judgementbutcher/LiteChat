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

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1DC),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4C5F78),
    secondaryContainer = Color(0xFFD5E3FF),
    tertiary = Color(0xFF67558F),
    background = Color(0xFFF4F7F6),
    surface = Color(0xFFF8FAF9),
    surfaceVariant = Color(0xFFDDE5E2),
    outlineVariant = Color(0xFFC0CAC6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CF5D4),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF123F35),
    onPrimaryContainer = Color(0xFFB6FFE6),
    secondary = Color(0xFFB7CBFF),
    onSecondary = Color(0xFF1C2E54),
    secondaryContainer = Color(0xFF23375D),
    onSecondaryContainer = Color(0xFFDCE7FF),
    tertiary = Color(0xFFD5C2FF),
    onTertiary = Color(0xFF37255D),
    tertiaryContainer = Color(0xFF372850),
    onTertiaryContainer = Color(0xFFF0E7FF),
    background = Color(0xFF07090D),
    onBackground = Color(0xFFF0F3F7),
    surface = Color(0xFF10141B),
    onSurface = Color(0xFFF0F3F7),
    surfaceVariant = Color(0xFF1A202A),
    onSurfaceVariant = Color(0xFFC2C9D4),
    outline = Color(0xFF8992A0),
    outlineVariant = Color(0xFF303945),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF4B1717)
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
    // The dark experience deliberately keeps its deep-black canvas; dynamic color is used as an
    // optional light-theme palette instead of replacing the product's defining dark surfaces.
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

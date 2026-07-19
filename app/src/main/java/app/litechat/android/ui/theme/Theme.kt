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
    primary = Color(0xFF79F2CA),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF123D33),
    onPrimaryContainer = Color(0xFFA2F8DB),
    secondary = Color(0xFFAFC9F3),
    onSecondary = Color(0xFF19314E),
    secondaryContainer = Color(0xFF24364C),
    onSecondaryContainer = Color(0xFFD5E5FF),
    tertiary = Color(0xFFC9B6FF),
    onTertiary = Color(0xFF33245E),
    tertiaryContainer = Color(0xFF30264B),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF050607),
    onBackground = Color(0xFFE8ECEA),
    surface = Color(0xFF080A0C),
    onSurface = Color(0xFFE8ECEA),
    surfaceVariant = Color(0xFF171B20),
    onSurfaceVariant = Color(0xFFB9C2BE),
    outline = Color(0xFF7B8581),
    outlineVariant = Color(0xFF28302D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF4B1717)
)

private val LiteChatShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
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

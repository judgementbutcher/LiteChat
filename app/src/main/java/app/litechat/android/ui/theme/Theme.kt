package app.litechat.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import app.litechat.android.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF315C8C),
    onPrimary = Color.White,
    secondary = Color(0xFF52635A),
    tertiary = Color(0xFF76546C),
    surface = Color(0xFFFAF9FD),
    surfaceVariant = Color(0xFFE2E2E8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF0A305F),
    secondary = Color(0xFFB9CCBF),
    tertiary = Color(0xFFE5BBD4),
    surfaceVariant = Color(0xFF43474E)
)

private val LiteChatShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun LiteChatTheme(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = if (dynamicColor) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = Typography(), shapes = LiteChatShapes, content = content)
}

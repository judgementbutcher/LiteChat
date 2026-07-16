package app.litechat.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.litechat.android.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF425E91),
    onPrimary = Color.White,
    secondary = Color(0xFF565F71),
    surfaceVariant = Color(0xFFDFE2EB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF0A305F),
    secondary = Color(0xFFBEC6DC),
    surfaceVariant = Color(0xFF43474E)
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
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

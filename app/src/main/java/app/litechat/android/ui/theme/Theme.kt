package app.litechat.android.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF245F75),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3E8F5),
    onPrimaryContainer = Color(0xFF073744),
    secondary = Color(0xFF5B6052),
    secondaryContainer = Color(0xFFE1E6D3),
    tertiary = Color(0xFF79536B),
    surface = Color(0xFFFBF9F7),
    surfaceVariant = Color(0xFFE3E2DF),
    outlineVariant = Color(0xFFC9C7C3)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD0E4),
    onPrimary = Color(0xFF003642),
    primaryContainer = Color(0xFF154E5E),
    onPrimaryContainer = Color(0xFFC3E8F5),
    secondary = Color(0xFFC5CBB7),
    secondaryContainer = Color(0xFF434839),
    tertiary = Color(0xFFE9B8D2),
    surface = Color(0xFF121413),
    surfaceVariant = Color(0xFF444744),
    outlineVariant = Color(0xFF444744)
)

private val LiteChatShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
    val colors = if (dynamicColor) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = LiteChatTypography, shapes = LiteChatShapes, content = content)
}

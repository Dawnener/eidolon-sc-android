package sc.eidolon.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 和 frontend/src/app/globals.css 的中性主题对齐；角色自己的颜色由 HerColors 按角色注入。
val Paper = Color(0xFFFFFFFF)
val Ink = Color(0xFF0A0A0A)
val PaperDark = Color(0xFF0A0A0A)
val InkDark = Color(0xFFFAFAFA)

val ProducerInk = Color(0xFF1C2A4A)
val ProducerInkText = Color(0xFFFBFCFE)
val ProducerInkDark = Color(0xFFA9C6F5)
val ProducerInkTextDark = Color(0xFF14171D)
val HerBubbleDark = Color(0xFF2B2E33)
val Destructive = Color(0xFFC8283C)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF171717),
    onPrimary = Color(0xFFFAFAFA),
    secondary = Color(0xFFF5F5F5),
    onSecondary = Color(0xFF171717),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF696969),
    outline = Color(0xFFA1A1A1),
    outlineVariant = Color(0xFFE5E5E5),
    error = Destructive,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE5E5E5),
    onPrimary = Color(0xFF171717),
    secondary = Color(0xFF262626),
    onSecondary = Color(0xFFFAFAFA),
    background = PaperDark,
    onBackground = InkDark,
    surface = Color(0xFF171717),
    onSurface = InkDark,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA1A1A1),
    outline = Color(0xFF737373),
    outlineVariant = Color(0x1AFFFFFF),
    error = Destructive,
    onError = Color.White,
)

private val EidolonTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

private val EidolonShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
)

@Composable
fun EidolonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = EidolonTypography,
        shapes = EidolonShapes,
        content = content,
    )
}

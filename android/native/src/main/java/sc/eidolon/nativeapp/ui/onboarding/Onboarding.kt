package sc.eidolon.nativeapp.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 首次安装的引导视觉：永远深色、Serif 标题、白光散成 29 种印象色（落地页 ADR-0016 的
 * 视觉语言在原生里的简化版）。不复制落地页的滚动叙事和 3D。
 */

private val OnboardingColors = darkColorScheme(
    primary = Color(0xFFF4F5F7),
    onPrimary = Color(0xFF101319),
    background = Color(0xFF080A0F),
    onBackground = Color(0xFFF4F5F7),
    surface = Color(0xFF0E121A),
    onSurface = Color(0xFFF4F5F7),
    surfaceVariant = Color(0xFF171C26),
    onSurfaceVariant = Color(0xFFAAB2C0),
    outline = Color(0xFF3A4150),
    error = Color(0xFFFF8A9A),
    onError = Color(0xFF2A0A10),
)

private val OnboardingTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = FontFamily.Serif,
            fontSize = 48.sp,
            lineHeight = 54.sp,
            letterSpacing = 1.sp,
        ),
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
    )
}

/** 29 位角色的官方印象色（characters/characters.toml）；太暗的提亮一点，保证在夜里看得见。 */
private val RayColors: List<Color> = listOf(
    "#ffbad6", "#144384", "#ffe012", "#f84cad", "#a846fb", "#006047", "#3b91c4",
    "#d9f2ff", "#e5461c", "#f93b90", "#ffc602", "#89c3eb", "#90e667", "#f54275",
    "#e75bec", "#fbfafa", "#f30100", "#5ce626", "#ff00ff", "#50d0d0", "#be1e3e",
    "#7967c3", "#ffc639", "#a6ceb6", "#760f10", "#24130d", "#e0b5d3", "#ead7a4",
    "#c3d825",
).map { hex ->
    val color = Color(android.graphics.Color.parseColor(hex))
    if (color.luminance() < 0.18f) {
        Color(
            red = color.red + (1f - color.red) * 0.35f,
            green = color.green + (1f - color.green) * 0.35f,
            blue = color.blue + (1f - color.blue) * 0.35f,
            alpha = 1f,
        )
    } else {
        color
    }
}

@Composable
fun OnboardingSurface(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OnboardingColors, typography = OnboardingTypography) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DarkSystemBars()
            content()
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun DarkSystemBars() {
    val view = LocalView.current
    val window = view.context.findActivity()?.window ?: return
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

/** 第一屏：品牌、白光分光和「开始」。 */
@Composable
fun WelcomeScreen(onStart: () -> Unit, startLabel: String = "开始") {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    OnboardingSurface {
        Box(modifier = Modifier.fillMaxSize()) {
            BeamBackdrop(intensity = 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.62f to Color.Black.copy(alpha = 0.18f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(900)) +
                        slideInVertically(tween(900)) { it / 6 },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "283 PRODUCTION",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "Eidolon-SC",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "和 283 事务所的偶像说说话",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(48.dp))
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(1100)) +
                        slideInVertically(tween(1100)) { it / 8 },
                ) {
                    Button(
                        onClick = onStart,
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background,
                        ),
                        modifier = Modifier.widthIn(min = 180.dp).height(52.dp),
                    ) {
                        Text(startLabel, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "非官方粉丝作品，与版权方无关。角色由 AI 扮演。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 引导里的后续页面：深色底、细光束、Serif 标题，内容由调用方给。 */
@Composable
fun OnboardingPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnboardingSurface {
        Box(modifier = Modifier.fillMaxSize()) {
            BeamBackdrop(intensity = 0.35f)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .consumeWindowInsets(WindowInsets.systemBars)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(if (onBack == null) 36.dp else 8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(28.dp))
                content()
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun BeamBackdrop(intensity: Float) {
    val transition = rememberInfiniteTransition(label = "beam")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "beam-phase",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF07090D),
                0.55f to Color(0xFF0B0F16),
                1f to Color(0xFF050608),
            ),
        )
        val split = Offset(size.width / 2f, size.height * 0.38f)
        drawLine(
            color = Color.White.copy(alpha = 0.72f * intensity),
            start = Offset(size.width / 2f, 0f),
            end = split,
            strokeWidth = 1.2.dp.toPx(),
        )
        RayColors.forEachIndexed { index, color ->
            val t = if (RayColors.size <= 1) 0.5f else index / (RayColors.size - 1f)
            val radians = Math.toRadians((-78f + 156f * t).toDouble())
            val end = Offset(
                split.x + sin(radians).toFloat() * size.height * 1.08f,
                split.y + cos(radians).toFloat() * size.height * 1.08f,
            )
            val shimmer = 0.22f + 0.12f * sin(phase * 2f * PI + index * 0.55f).toFloat()
            drawLine(
                color = color.copy(alpha = shimmer * intensity),
                start = split,
                end = end,
                strokeWidth = 0.9.dp.toPx(),
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.5f * intensity),
                    Color.Transparent,
                ),
                center = split,
                radius = 64.dp.toPx(),
            ),
            radius = 64.dp.toPx(),
            center = split,
        )
    }
}

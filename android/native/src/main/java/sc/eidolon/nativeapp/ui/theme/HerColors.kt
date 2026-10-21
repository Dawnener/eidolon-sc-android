package sc.eidolon.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import sc.eidolon.nativeapp.core.ColorMath

data class HerColors(
    val mark: Color,
    val accent: Color,
    val wall: Color,
    val onAccent: Color,
)

/** 从角色的官方印象色推出强调色、墙色和可读的前景色；v1 用简化版对比度修正。 */
@Composable
fun herColors(raw: String): HerColors {
    val dark = isSystemInDarkTheme()
    val base = ColorMath.parseHex(raw) ?: 0xFF7FB4FF.toInt()
    val background = if (dark) 0xFF0A0A0A.toInt() else 0xFFFFFFFF.toInt()
    val muted = if (dark) 0xFF262626.toInt() else 0xFFF5F5F5.toInt()
    val paper = if (dark) 0xFF14171D.toInt() else 0xFFFBFCFE.toInt()
    val mark = if (ColorMath.contrastRatio(base, muted) >= 3.0) {
        base
    } else {
        ColorMath.blend(base, background, 0.35f)
    }
    val accent = if (ColorMath.contrastRatio(base, paper) >= 4.5) {
        base
    } else {
        ColorMath.blend(base, if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 0.35f)
    }
    val onAccent = ColorMath.readableOn(
        accent,
        listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
    )
    val wall = ColorMath.blend(background, base, if (dark) 0.10f else 0.06f)
    return HerColors(Color(mark), Color(accent), Color(wall), Color(onAccent))
}

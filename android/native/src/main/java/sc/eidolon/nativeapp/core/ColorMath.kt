package sc.eidolon.nativeapp.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 纯 ARGB 颜色运算；不依赖 Android，方便单测。 */
object ColorMath {
    /** "#rrggbb" 或 "#aarrggbb" → 0xAARRGGBB；非法返回 null。 */
    fun parseHex(value: String?): Int? {
        val text = value?.trim()?.removePrefix("#") ?: return null
        if (text.length != 6 && text.length != 8) return null
        val parsed = text.toLongOrNull(16) ?: return null
        return if (text.length == 6) (0xFF000000L or parsed).toInt() else parsed.toInt()
    }

    fun alpha(color: Int): Int = color ushr 24 and 0xFF

    fun red(color: Int): Int = color ushr 16 and 0xFF

    fun green(color: Int): Int = color ushr 8 and 0xFF

    fun blue(color: Int): Int = color and 0xFF

    fun blend(base: Int, overlay: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = base ushr shift and 0xFF
            val b = overlay ushr shift and 0xFF
            return (a + (b - a) * f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(red(color)) + 0.7152 * linear(green(color)) + 0.0722 * linear(blue(color))
    }

    fun contrastRatio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = max(la, lb)
        val darker = min(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** 从候选中挑一个在背景上对比度最高的颜色。 */
    fun readableOn(background: Int, candidates: List<Int>): Int =
        candidates.maxByOrNull { contrastRatio(background, it) } ?: candidates.firstOrNull() ?: 0xFFFFFFFF.toInt()
}

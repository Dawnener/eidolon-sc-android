package sc.eidolon.nativeapp.ui.stats

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.Stats
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.roundToInt

/** 把当前统计画成一张适合分享的竖图，交给系统分享面板。 */
object StatsShare {
    suspend fun share(
        context: Context,
        stats: Stats,
        characters: Map<String, Character>,
    ): Result<Unit> = runCatching {
        val bitmap = withContext(Dispatchers.IO) { draw(stats, characters) }
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "eidolon-stats.png")
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享统计")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun draw(stats: Stats, characters: Map<String, Character>): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0B0E14"))

        val accent = stats.range.characters
            .maxByOrNull { it.turns }
            ?.let { characters[it.characterId]?.color }
            ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
            ?: Color.parseColor("#7FB4FF")

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                880f,
                140f,
                640f,
                intArrayOf(withAlpha(accent, 0.32f), withAlpha(accent, 0f)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 760f, glow)

        val title = paint(76f, "#F4F5F7", Typeface.SERIF)
        val subtitle = paint(32f, "#AAB2C0")
        val label = paint(30f, "#AAB2C0")
        val number = paint(120f, "#F4F5F7")
        val small = paint(28f, "#AAB2C0")
        val name = paint(38f, "#F4F5F7")
        val footer = paint(24f, "#6F7888")

        // 顶部细光束与标题
        canvas.drawRect(0f, 0f, width.toFloat(), 8f, paint(8f, "#F4F5F7"))
        canvas.drawText("Eidolon-SC", 80f, 150f, title)
        canvas.drawText("和她们的往来", 80f, 205f, subtitle)

        canvas.drawText("认识", 80f, 330f, label)
        canvas.drawText("${stats.daysKnown}", 80f, 450f, number)
        canvas.drawText("天", 80f + number.measureText("${stats.daysKnown}") + 16f, 450f, label)

        canvas.drawText("本次范围", 80f, 545f, label)
        val turnsText = "${stats.range.turns}"
        canvas.drawText(turnsText, 80f, 650f, number)
        canvas.drawText(
            "个回合 · ${rangeLabel(stats.range.span)}",
            80f + number.measureText(turnsText) + 16f,
            650f,
            label,
        )

        val calls = stats.range.calls
        val giftGiven = stats.range.gifts?.given
        val extras = buildList {
            if (calls.count > 0) add("通话 ${calls.count} 次")
            if (calls.seconds > 0) add("通话 ${callMinutes(calls.seconds)} 分")
            if (giftGiven != null && giftGiven > 0) add("礼物 $giftGiven 件")
        }
        if (extras.isNotEmpty()) {
            canvas.drawText(extras.joinToString(" · "), 80f, 712f, small)
        }

        canvas.drawText("聊得最多", 80f, 780f, label)
        val top = stats.range.characters
            .filter { it.turns > 0 }
            .sortedByDescending { it.turns }
            .take(3)
        var y = 825f
        if (top.isEmpty()) {
            canvas.drawText("还没有聊过", 80f, y, small)
        } else {
            top.forEach { entry ->
                val character = characters[entry.characterId]
                val color = character?.color
                    ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
                    ?: accent
                canvas.drawCircle(96f, y - 12f, 12f, paint(24f, colorOf(color)))
                canvas.drawText(character?.name ?: entry.characterId, 130f, y, name)
                val count = "${entry.turns} 回合"
                canvas.drawText(
                    count,
                    width - 80f - name.measureText(count),
                    y,
                    small,
                )
                y += 76f
            }
        }

        canvas.drawText("连续来访 ${stats.visits.current} 天 · 最长 ${stats.visits.longest} 天", 80f, 1040f, small)

        val counts = stats.heatmap.counts
        canvas.drawText("过去一年的来访", 80f, 1110f, label)
        if (counts.isNotEmpty()) {
            val cell = 14f
            val gap = 3f
            val top = 1128f
            val most = counts.maxOrNull() ?: 0
            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            counts.forEachIndexed { index, count ->
                val level = if (most <= 0 || count <= 0) {
                    0
                } else {
                    (count.toFloat() / most * 4).toInt().coerceIn(1, 4)
                }
                cellPaint.color = if (level == 0) {
                    Color.parseColor("#242A35")
                } else {
                    withAlpha(accent, 0.16f + level * 0.2f)
                }
                val left = 80f + (index / 7) * (cell + gap)
                val cellTop = top + (index % 7) * (cell + gap)
                canvas.drawRoundRect(
                    RectF(left, cellTop, left + cell, cellTop + cell),
                    3f,
                    3f,
                    cellPaint,
                )
            }
        }

        canvas.drawText(LocalDate.now().toString(), 80f, 1278f, footer)
        canvas.drawText("非官方粉丝作品，角色由 AI 扮演", 80f, 1318f, footer)

        // 右侧一道角色色的竖线
        canvas.drawRect(width - 14f, 0f, width.toFloat(), height.toFloat(), paint(14f, colorOf(accent)))
        return bitmap
    }

    private fun paint(size: Float, color: String, typeface: Typeface = Typeface.SANS_SERIF): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.parseColor(color)
            textSize = size
            this.typeface = typeface
        }

    private fun colorOf(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun rangeLabel(span: String): String = when (span) {
        "30d" -> "近 30 天"
        "1y" -> "近一年"
        else -> "全部"
    }

    private fun callMinutes(seconds: Double): Int =
        if (seconds <= 0) 0 else maxOf(1, (seconds / 60).roundToInt())
}

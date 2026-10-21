package sc.eidolon.nativeapp.model

import java.text.Normalizer
import java.util.Locale

object CharacterSearch {
    fun matches(query: String, character: Character): Boolean {
        val folded = fold(query)
        if (folded.isBlank()) return true
        return candidates(character).any { fold(it).contains(folded) }
    }

    fun fold(text: String): String {
        var value = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(SEPARATORS, "")
        value = buildString(value.length) {
            for (ch in value) {
                append(if (ch in 'ァ'..'ヶ') (ch.code - 0x60).toChar() else ch)
            }
        }
        value = value.replace(Regex("(.)々"), "$1$1")
        value = buildString(value.length) {
            for (ch in value) append(GLYPHS[ch] ?: ch)
        }
        for ((pattern, replacement) in LONG_MARKS) {
            value = value.replace(pattern, replacement)
        }
        return value
    }

    private fun candidates(character: Character): List<String> = buildList {
        add(character.name)
        character.nameZh?.let(::add)
        addAll(bothOrders(character.nameKana))
        addAll(bothOrders(character.nameRomaji))
        character.unit?.let(::add)
        character.unitReading?.let(::add)
    }.filter { it.isNotBlank() }

    private fun bothOrders(fullName: String?): List<String> {
        if (fullName.isNullOrBlank()) return emptyList()
        val parts = fullName.trim().split(Regex("\\s+"))
        val direct = parts.joinToString(" ")
        val reversed = parts.reversed().joinToString(" ")
        return if (direct == reversed) listOf(direct) else listOf(direct, reversed)
    }

    private val SEPARATORS = Regex("[\\s・'’\\-·.]")

    private val GLYPHS = mapOf(
        '櫻' to '樱', '風' to '风', '織' to '织', '燈' to '灯', '宮' to '宫', '岡' to '冈',
        '戀' to '恋', '鐘' to '钟', '鍾' to '钟', '瀬' to '濑', '瀨' to '濑', '結' to '结',
        '華' to '华', '霧' to '雾', '倉' to '仓', '淺' to '浅', '円' to '圆', '圓' to '圆',
        '絲' to '糸', '雛' to '雏', '緋' to '绯', '沢' to '泽', '澤' to '泽', '優' to '优',
        '愛' to '爱', '穂' to '穗', '園' to '园', '樹' to '树', '凜' to '凛', '葉' to '叶',
        '鳩' to '鸠', '鈴' to '铃', '陽' to '阳', '課' to '课', '後' to '后',
    )

    private val LONG_MARKS = listOf(
        Regex("[ōô]") to "o",
        Regex("[ūû]") to "u",
        Regex("[āâ]") to "a",
        Regex("[īî]") to "i",
        Regex("[ēê]") to "e",
        Regex("o[ouh]") to "o",
        Regex("uu") to "u",
        Regex("aa") to "a",
        Regex("ii") to "i",
        Regex("ee") to "e",
    )
}

package sc.eidolon.nativeapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.CharacterSearch

class CharacterSearchTest {
    private val character = Character(
        id = "mano",
        name = "櫻木真乃",
        intro = "",
        color = "#ffbad6",
        avatarUrl = null,
        nameZh = "樱木真乃",
        nameKana = "さくらぎ まの",
        nameRomaji = "Sakuragi Mano",
        unit = "illumination STARS",
        unitReading = "イルミネーションスターズ",
    )

    @Test
    fun matchesSimplifiedAndTraditionalChinese() {
        assertTrue(CharacterSearch.matches("樱木", character))
        assertTrue(CharacterSearch.matches("櫻木", character))
    }

    @Test
    fun matchesKanaAndBothRomajiOrders() {
        assertTrue(CharacterSearch.matches("まの", character))
        assertTrue(CharacterSearch.matches("Mano", character))
        assertTrue(CharacterSearch.matches("Sakuragi Mano", character))
        assertTrue(CharacterSearch.matches("Mano Sakuragi", character))
    }

    @Test
    fun matchesUnitAndReading() {
        assertTrue(CharacterSearch.matches("illumination", character))
        assertTrue(CharacterSearch.matches("イルミ", character))
    }

    @Test
    fun blankMatchesEverythingAndUnknownDoesNot() {
        assertTrue(CharacterSearch.matches("", character))
        assertFalse(CharacterSearch.matches("xyz", character))
    }

    @Test
    fun foldNormalizesWidthCaseAndSeparators() {
        assertTrue(CharacterSearch.fold("ＳＡＫＵＲＡＧＩ　ＭＡＮＯ") == "sakuragimano")
    }
}

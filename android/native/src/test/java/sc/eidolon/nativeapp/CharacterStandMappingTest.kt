package sc.eidolon.nativeapp

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.data.api.CharacterDto
import sc.eidolon.nativeapp.data.api.toDomain

class CharacterStandMappingTest {
    @Test
    fun mapsTheDefaultOutfitOfHerStand() {
        val payload = """
            {
              "id": "mano",
              "name": "櫻木真乃",
              "color": "#ffbad6",
              "greeting": {
                "ja": "ほわっ……プロデューサーさん、ですか？",
                "subtitle": "呼哇……你就是制作人吗？"
              },
              "stand": {
                "default_outfit": {
                  "id": "stand/1030010010",
                  "url": "https://stand.example/spine/idols/stand/1030010010/"
                },
                "outfits_url": "https://stand.example/outfits/1.json"
              }
            }
        """.trimIndent()

        val character = EidolonJson.decodeFromString<CharacterDto>(payload).toDomain()

        assertEquals("stand/1030010010", character.stand?.defaultOutfit?.id)
        assertEquals(
            "https://stand.example/spine/idols/stand/1030010010/",
            character.stand?.defaultOutfit?.url,
        )
        assertEquals("https://stand.example/outfits/1.json", character.stand?.outfitsUrl)
        assertEquals("ほわっ……プロデューサーさん、ですか？", character.greetingJa)
        assertEquals("呼哇……你就是制作人吗？", character.greetingZh)
    }

    @Test
    fun leavesTheStandNullWhenTheDeploymentDoesNotServeIt() {
        val payload = """
            {"id":"mano","name":"櫻木真乃","color":"#ffbad6","stand":null}
        """.trimIndent()

        val character = EidolonJson.decodeFromString<CharacterDto>(payload).toDomain()

        assertNull(character.stand)
    }
}

package sc.eidolon.nativeapp

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.data.api.OutfitListDto
import sc.eidolon.nativeapp.data.api.toDomain

class OutfitListMappingTest {
    @Test
    fun resolvesOutfitAndThumbnailPathsAgainstTheListUrl() {
        val payload = """
            {
              "idol_id": 1,
              "cards": [{
                "id": "1030010010",
                "name": "ナチュラルモード",
                "type": "P_SR",
                "everyday": {
                  "id": "stand/1030010010",
                  "url": "../spine/idols/stand/1030010010/",
                  "thumbnail": "../thumbnails/stand/1030010010.png"
                },
                "stage": null
              }]
            }
        """.trimIndent()

        val cards = EidolonJson.decodeFromString<OutfitListDto>(payload)
            .toDomain("https://stand.example/outfits/1.json")

        assertEquals(1, cards.size)
        assertEquals("ナチュラルモード", cards[0].name)
        assertEquals(
            "https://stand.example/spine/idols/stand/1030010010/",
            cards[0].everyday?.url,
        )
        assertEquals(
            "https://stand.example/thumbnails/stand/1030010010.png",
            cards[0].everyday?.thumbnail,
        )
        assertNull(cards[0].stage)
        assertEquals(listOf("stand/1030010010"), cards[0].outfits.map { it.id })
    }
}

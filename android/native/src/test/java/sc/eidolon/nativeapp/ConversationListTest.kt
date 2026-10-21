package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.ConversationList
import sc.eidolon.nativeapp.model.EventKind
import sc.eidolon.nativeapp.model.LastPreview
import sc.eidolon.nativeapp.model.LatestConversation
import sc.eidolon.nativeapp.model.Preview
import java.time.Instant

class ConversationListTest {
    private val mano = Character("mano", "櫻木真乃", "", "#ffbad6", null, nameRomaji = "Sakuragi Mano")
    private val hiori = Character("hiori", "風野灯織", "", "#144384", null, nameRomaji = "Kazano Hiori")
    private val kogane = Character("kogane", "月岡恋鐘", "", "#f84cad", null)

    private fun latest(
        characterId: String,
        conversationId: String,
        createdAt: Instant,
        last: LastPreview?,
        unseen: Boolean = false,
    ) = LatestConversation(characterId, conversationId, createdAt, last, unseen)

    @Test
    fun sortsByPreviewTimeAndKeepsNeverTalkedLast() {
        val latest = mapOf(
            "mano" to latest(
                "mano",
                "c1",
                Instant.parse("2026-10-21T09:00:00Z"),
                LastPreview(
                    at = Instant.parse("2026-10-21T10:00:00Z"),
                    role = "character",
                    ja = "こんにちは",
                    zh = "你好",
                ),
            ),
            "hiori" to latest(
                "hiori",
                "c2",
                Instant.parse("2026-10-21T11:00:00Z"),
                null,
            ),
        )
        val rows = ConversationList.arrange(listOf(mano, hiori, kogane), latest)
        assertEquals(listOf("mano", "hiori", "kogane"), rows.map { it.character.id })
        assertEquals("你好", (rows[0].preview as Preview.Her).text)
        assertEquals(Preview.None, rows[1].preview)
        assertTrue(rows[1].hasConversation)
        assertFalse(rows[2].hasConversation)
    }

    @Test
    fun previewsUsersEventsAndPhotos() {
        val latest = mapOf(
            "mano" to latest(
                "mano",
                "c1",
                Instant.parse("2026-10-21T09:00:00Z"),
                LastPreview(at = Instant.parse("2026-10-21T10:00:00Z"), role = "user", text = "在吗"),
            ),
            "hiori" to latest(
                "hiori",
                "c2",
                Instant.parse("2026-10-21T09:00:00Z"),
                LastPreview(
                    at = Instant.parse("2026-10-21T10:00:00Z"),
                    eventKind = EventKind.Gift,
                    giftName = "花",
                ),
            ),
            "kogane" to latest(
                "kogane",
                "c3",
                Instant.parse("2026-10-21T09:00:00Z"),
                LastPreview(
                    at = Instant.parse("2026-10-21T10:00:00Z"),
                    role = "character",
                    ja = "はい",
                    zh = "",
                    photo = true,
                ),
            ),
        )
        val rows = ConversationList.arrange(listOf(mano, hiori, kogane), latest)
        assertEquals(Preview.You("在吗"), rows.first { it.character.id == "mano" }.preview)
        assertEquals(Preview.Event(EventKind.Gift, "花"), rows.first { it.character.id == "hiori" }.preview)
        assertEquals(Preview.Her("はい", true, false), rows.first { it.character.id == "kogane" }.preview)
    }

    @Test
    fun searchFiltersAndUnseenIsExposed() {
        val latest = mapOf(
            "mano" to latest(
                "mano",
                "c1",
                Instant.parse("2026-10-21T09:00:00Z"),
                LastPreview(at = Instant.parse("2026-10-21T10:00:00Z"), role = "character", zh = "你好"),
                unseen = true,
            ),
        )
        val rows = ConversationList.arrange(listOf(mano, hiori, kogane), latest, "sakuragi")
        assertEquals(listOf("mano"), rows.map { it.character.id })
        assertTrue(rows.single().unseen)
    }
}

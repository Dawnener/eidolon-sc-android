package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.data.api.TurnEventParser
import sc.eidolon.nativeapp.model.Lang
import sc.eidolon.nativeapp.model.TurnEvent

class TurnEventParserTest {
    @Test
    fun userAndDelta() {
        assertEquals(TurnEvent.User("你好"), TurnEventParser.parse("""{"type":"user","text":"你好"}"""))
        assertEquals(
            TurnEvent.Delta(0, Lang.Ja, "こんにちは"),
            TurnEventParser.parse("""{"type":"delta","segment":0,"lang":"ja","text":"こんにちは"}"""),
        )
        assertEquals(
            TurnEvent.Delta(1, Lang.Zh, "你好"),
            TurnEventParser.parse("""{"type":"delta","segment":1,"lang":"zh","text":"你好"}"""),
        )
    }

    @Test
    fun doneWithSegmentsAndEnded() {
        val payload = """
            {"type":"done",
             "message":{"id":12,"role":"character","text":null,"ja":"こんにちは","zh":"你好",
               "created_at":"2026-10-21T09:05:00+00:00",
               "segments":[{"ja":"こんにちは","zh":"你好"}],"affinity":null},
             "affinity":{"tier":"trust","cooldown_until":null},
             "ended":{"type":"event","id":13,"kind":"ended",
               "created_at":"2026-10-21T09:06:00+00:00","affinity":null}}
        """.trimIndent()
        val event = TurnEventParser.parse(payload)
        assertTrue(event is TurnEvent.Done)
        val done = event as TurnEvent.Done
        assertEquals(12, done.message.id)
        assertEquals("こんにちは", done.message.segments.single().ja)
        assertEquals(13, done.ended?.id)
        assertEquals("trust", done.affinity?.tier)
    }

    @Test
    fun errorAndUnknown() {
        assertEquals(
            TurnEvent.Error("角色暂时无法回复"),
            TurnEventParser.parse("""{"type":"error","message":"角色暂时无法回复"}"""),
        )
        assertNull(TurnEventParser.parse("""{"type":"something-new","x":1}"""))
        assertNull(TurnEventParser.parse("not json"))
    }

    @Test
    fun photosUsedUp() {
        val event = TurnEventParser.parse(
            """{"type":"photos-used-up","retry_at":"2026-10-21T10:00:00+00:00"}""",
        )
        assertTrue(event is TurnEvent.PhotosUsedUp)
    }
}

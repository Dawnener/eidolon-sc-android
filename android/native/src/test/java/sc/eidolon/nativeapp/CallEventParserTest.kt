package sc.eidolon.nativeapp

import ai.pipecat.client.types.Value
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.data.api.CallEvent
import sc.eidolon.nativeapp.data.api.CallEventParser

class CallEventParserTest {
    @Test
    fun parsesSpokenTurnsAndEmotion() {
        val user = CallEventParser.parse("""{"type":"user","text":"こんばんは","reply":1}""")
        assertEquals(CallEvent.User("こんばんは", 1), user)

        val delta = CallEventParser.parse(
            """{"type":"delta","segment":0,"lang":"ja","text":"もしもし"}""",
        )
        assertEquals(CallEvent.Delta(0, "ja", "もしもし"), delta)

        val emotion = CallEventParser.parse(
            """{"type":"emotion","emotion":"happy","nod":"yes"}""",
        )
        assertEquals(CallEvent.Emotion("happy", "yes"), emotion)
    }

    @Test
    fun parsesHowTheCallEnded() {
        val ended = CallEventParser.parse(
            """{"type":"ended","reason":"duration_limit","message":"时间到了","counts":true,
               "cooldown_until":"2026-10-22T12:00:00+00:00"}""",
        ) as CallEvent.Ended

        assertEquals("duration_limit", ended.reason)
        assertEquals("时间到了", ended.message)
        assertEquals("2026-10-22T12:00:00Z", ended.cooldownUntil.toString())
    }

    @Test
    fun parsesAServerMessageAsPipecatHandsItOver() {
        val element = EidolonJson.parseToJsonElement(
            """{"type":"delta","segment":2,"lang":"zh","text":"你好"}""",
        )
        val value = EidolonJson.decodeFromJsonElement<Value>(element)

        val payload = EidolonJson.encodeToString(Value.serializer(), value)

        assertEquals(CallEvent.Delta(2, "zh", "你好"), CallEventParser.parse(payload))
    }

    @Test
    fun ignoresEventsTheCallScreenDoesNotNeed() {
        assertNull(CallEventParser.parse("""{"type":"segment-playing","reply":1,"segment":0}"""))
        assertNull(CallEventParser.parse("not json"))
        assertTrue(CallEventParser.parse("""{"type":"error"}""") is CallEvent.Error)
    }
}

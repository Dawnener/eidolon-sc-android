package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.data.api.SseDecoder

class SseDecoderTest {
    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)

    @Test
    fun frameSplitAcrossChunks() {
        val decoder = SseDecoder()
        assertTrue(decoder.feed(bytes("data: {\"a\":")).isEmpty())
        assertEquals(listOf("{\"a\":1}"), decoder.feed(bytes("1}\n\n")))
    }

    @Test
    fun multipleFramesInOneChunk() {
        val decoder = SseDecoder()
        assertEquals(
            listOf("one", "two"),
            decoder.feed(bytes("data: one\n\ndata: two\n\n")),
        )
    }

    @Test
    fun multiLineDataJoinsWithNewline() {
        val decoder = SseDecoder()
        assertEquals(listOf("a\nb"), decoder.feed(bytes("data: a\ndata: b\n\n")))
    }

    @Test
    fun utf8CharacterSplitAcrossChunks() {
        val whole = bytes("data: 你好\n\n")
        val decoder = SseDecoder()
        assertTrue(decoder.feed(whole.copyOfRange(0, 8)).isEmpty())
        assertEquals(listOf("你好"), decoder.feed(whole.copyOfRange(8, whole.size)))
    }

    @Test
    fun crlfFrames() {
        val decoder = SseDecoder()
        assertEquals(listOf("a"), decoder.feed(bytes("data: a\r\n\r\n")))
    }

    @Test
    fun partialFrameAtEofIsDropped() {
        val decoder = SseDecoder()
        assertTrue(decoder.feed(bytes("data: a\n")).isEmpty())
        assertTrue(decoder.finish().isEmpty())
    }

    @Test
    fun ignoresEmptyAndUnknownFields() {
        val decoder = SseDecoder()
        assertEquals(
            listOf("payload"),
            decoder.feed(bytes("event: message\nid: 1\ndata: payload\n\n\n\n")),
        )
    }
}

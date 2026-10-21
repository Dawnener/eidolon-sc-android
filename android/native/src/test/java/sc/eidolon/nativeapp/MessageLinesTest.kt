package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.model.ChatLine
import sc.eidolon.nativeapp.model.Entry
import sc.eidolon.nativeapp.model.EventKind
import sc.eidolon.nativeapp.model.MessageLines
import sc.eidolon.nativeapp.model.Segment
import java.time.Instant
import java.time.ZoneOffset

class MessageLinesTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-21T12:00:00Z")

    @Test
    fun addsDaySeparatorAndGroupsSameSide() {
        val entries = listOf(
            Entry.User("1", "你好", Instant.parse("2026-10-21T10:00:00Z")),
            Entry.User("2", "在吗", Instant.parse("2026-10-21T10:01:00Z")),
            Entry.Character("3", listOf(Segment("はい", "在")), false, Instant.parse("2026-10-21T10:02:00Z")),
        )
        val lines = MessageLines.linesOf(entries, now, zone)
        assertEquals(4, lines.size)
        assertTrue(lines[0] is ChatLine.Day)
        assertEquals("今天", (lines[0] as ChatLine.Day).label)
        assertTrue((lines[1] as ChatLine.Item).first)
        assertFalse((lines[1] as ChatLine.Item).last)
        assertFalse((lines[2] as ChatLine.Item).first)
        assertTrue((lines[2] as ChatLine.Item).last)
        assertTrue((lines[3] as ChatLine.Item).first)
    }

    @Test
    fun eventBreaksGroupingAndNewDayAddsSeparator() {
        val entries = listOf(
            Entry.Character("1", listOf(Segment("a", "a")), false, Instant.parse("2026-10-21T10:00:00Z")),
            Entry.Event("2", EventKind.HungUp, Instant.parse("2026-10-21T10:01:00Z")),
            Entry.Character("3", listOf(Segment("b", "b")), false, Instant.parse("2026-10-22T10:00:00Z")),
        )
        val lines = MessageLines.linesOf(entries, now, zone)
        assertEquals(5, lines.size)
        assertTrue(lines[1] is ChatLine.Item)
        assertTrue((lines[1] as ChatLine.Item).last)
        assertTrue(lines[2] is ChatLine.Item)
        assertTrue((lines[2] as ChatLine.Item).first)
        assertTrue(lines[3] is ChatLine.Day)
        assertEquals("10月22日", (lines[3] as ChatLine.Day).label)
    }

    @Test
    fun reachOutStartsANewGroup() {
        val entries = listOf(
            Entry.Character("1", listOf(Segment("a", "a")), false, Instant.parse("2026-10-21T10:00:00Z")),
            Entry.Character(
                "2",
                listOf(Segment("b", "b")),
                false,
                Instant.parse("2026-10-21T10:01:00Z"),
                reachOut = true,
            ),
        )
        val lines = MessageLines.linesOf(entries, now, zone)
        val items = lines.filterIsInstance<ChatLine.Item>()
        assertTrue(items[0].last)
        assertTrue(items[1].first)
    }
}

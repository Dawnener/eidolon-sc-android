package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test
import sc.eidolon.nativeapp.core.Time
import java.time.Instant
import java.time.ZoneOffset

class TimeTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-21T12:00:00Z")

    @Test
    fun parsesOffsetTimestamps() {
        assertEquals(
            Instant.parse("2026-10-21T09:05:00Z"),
            Time.parseInstant("2026-10-21T09:05:00+00:00"),
        )
        assertEquals(null, Time.parseInstant(null))
        assertEquals(null, Time.parseInstant("nope"))
    }

    @Test
    fun listTimeAndDayLabels() {
        val today = Instant.parse("2026-10-21T09:05:00Z")
        val yesterday = Instant.parse("2026-10-20T09:05:00Z")
        val earlier = Instant.parse("2026-03-02T09:05:00Z")
        assertEquals("9:05", Time.formatListTime(today, now, zone))
        assertEquals("昨天", Time.formatListTime(yesterday, now, zone))
        assertEquals("3月2日", Time.formatListTime(earlier, now, zone))
        assertEquals("今天", Time.formatDaySeparator(today, now, zone))
        assertEquals("昨天", Time.formatDaySeparator(yesterday, now, zone))
        assertEquals("3月2日", Time.formatDaySeparator(earlier, now, zone))
    }

    @Test
    fun memoryDate() {
        assertEquals(
            "2026年10月21日",
            Time.formatMemoryDate(Instant.parse("2026-10-21T09:05:00Z"), zone),
        )
    }
}

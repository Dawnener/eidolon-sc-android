package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.model.ConversationLog
import sc.eidolon.nativeapp.model.Entry
import sc.eidolon.nativeapp.model.EventKind
import sc.eidolon.nativeapp.model.Lang
import sc.eidolon.nativeapp.model.LogAction
import sc.eidolon.nativeapp.model.Recorded
import sc.eidolon.nativeapp.model.Segment
import java.time.Instant

class ConversationLogTest {
    private val at = Instant.parse("2026-10-21T09:00:00Z")

    @Test
    fun loadedMapsRecordedEntries() {
        val log = ConversationLog().reduce(
            LogAction.Loaded(
                listOf(
                    Recorded.UserMessage(1, "你好", at),
                    Recorded.CharacterMessage(2, "こんにちは", "你好", listOf(Segment("こんにちは", "你好")), at),
                    Recorded.ConversationEvent(3, EventKind.HungUp, at),
                    Recorded.ReachOut(4, "おはよう", "早上好", listOf(Segment("おはよう", "早上好")), at),
                ),
            ),
        )
        assertEquals(listOf("m1", "m2", "e3", "r4"), log.entries.map { it.key })
        assertTrue((log.entries[3] as Entry.Character).reachOut)
        assertNull(log.turn)
    }

    @Test
    fun submittedThenConfirmed() {
        val submitted = ConversationLog().reduce(LogAction.Submitted("你好", at))
        assertEquals(2, submitted.entries.size)
        assertFalse(submitted.turn!!.confirmed)
        val confirmed = submitted.reduce(LogAction.UserSaid("你好", at.plusSeconds(1)))
        assertEquals(2, confirmed.entries.size)
        assertTrue(confirmed.turn!!.confirmed)
    }

    @Test
    fun deltasAccumulateBySegmentAndLanguage() {
        var log = ConversationLog().reduce(LogAction.Submitted("hi", at))
        log = log.reduce(LogAction.Delta(0, Lang.Ja, "こん"))
        log = log.reduce(LogAction.Delta(0, Lang.Ja, "にちは"))
        log = log.reduce(LogAction.Delta(0, Lang.Zh, "你"))
        log = log.reduce(LogAction.Delta(1, Lang.Ja, "元気？"))
        val reply = log.entries.last() as Entry.Character
        assertEquals("こんにちは", reply.segments[0].ja)
        assertEquals("你", reply.segments[0].zh)
        assertEquals("元気？", reply.segments[1].ja)
    }

    @Test
    fun doneReplacesPendingReplyAndAppendsEnded() {
        var log = ConversationLog().reduce(LogAction.Submitted("hi", at))
        log = log.reduce(LogAction.Delta(0, Lang.Ja, "部分"))
        val message = Recorded.CharacterMessage(
            id = 7,
            ja = "こんにちは",
            zh = "你好",
            segments = listOf(Segment("こんにちは", "你好")),
            at = at.plusSeconds(2),
        )
        log = log.reduce(
            LogAction.Done(
                message,
                Recorded.ConversationEvent(8, EventKind.Ended, at.plusSeconds(3)),
            ),
        )
        assertEquals(listOf("new-0", "m7", "e8"), log.entries.map { it.key })
        assertNull(log.turn)
        assertTrue(log.isEnded)
    }

    @Test
    fun errorRemovesBothOptimisticEntries() {
        var log = ConversationLog().reduce(LogAction.Submitted("hi", at))
        log = log.reduce(LogAction.Error)
        assertTrue(log.entries.isEmpty())
        assertNull(log.turn)
    }

    @Test
    fun eventIsAppendedOnce() {
        val change = sc.eidolon.nativeapp.model.AffinityChange(up = true, tier = "trust")
        val event = Recorded.ConversationEvent(9, EventKind.Gift, at, "花", "bouquet", change)
        var log = ConversationLog().reduce(LogAction.EventRecorded(event))
        log = log.reduce(LogAction.EventRecorded(event))
        assertEquals(1, log.entries.size)
        val stored = log.entries.single() as Entry.Event
        assertEquals("花", stored.giftName)
        assertEquals("bouquet", stored.giftId)
        assertEquals("trust", stored.affinity?.tier)
    }
}

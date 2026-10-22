package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.model.AffinityStatus
import sc.eidolon.nativeapp.model.BackpackItem
import sc.eidolon.nativeapp.model.DailyTask
import sc.eidolon.nativeapp.model.DailyTasks
import sc.eidolon.nativeapp.model.DrawItem
import sc.eidolon.nativeapp.model.DrawOutcome
import sc.eidolon.nativeapp.model.GiftCharacter
import sc.eidolon.nativeapp.model.GiftState
import sc.eidolon.nativeapp.model.GiftUpdates
import sc.eidolon.nativeapp.model.GivenGift
import sc.eidolon.nativeapp.model.Reward
import java.time.Instant

class GiftUpdatesTest {
    private fun state() = GiftState(
        open = true,
        draws = 1,
        maxDraws = 7,
        backpack = listOf(BackpackItem("cake", 1), BackpackItem("book", 2)),
        resetCards = 0,
        characters = listOf(
            GiftCharacter("mano", "trust"),
            GiftCharacter("hiori", "distant"),
        ),
        dailyTasks = DailyTasks(
            tasks = listOf(
                DailyTask("one_turn", false, 0, 1, 1),
                DailyTask("one_gift", false, 0, 1, 1),
            ),
        ),
    )

    @Test
    fun drawMergesGiftsAndResetCards() {
        val result = DrawOutcome(
            drawn = listOf(DrawItem.Gift("cake"), DrawItem.Gift("candy"), DrawItem.ResetCard),
            draws = 2,
            rewarded = Reward(draws = 1, resetCards = 1),
        )

        val updated = GiftUpdates.withDrawn(state(), result)

        assertEquals(2, updated.draws)
        assertEquals(2, updated.backpack.first { it.giftId == "cake" }.count)
        assertEquals(1, updated.backpack.first { it.giftId == "candy" }.count)
        assertEquals(2, updated.resetCards)
        assertEquals(3, updated.backpack.size)
    }

    @Test
    fun givingUsesServerLeftAndUpdatesAffinityAndTasks() {
        val given = GivenGift(
            change = null,
            affinity = AffinityStatus("close"),
            left = 0,
            rewarded = Reward(
                draws = 1,
                resetCards = 1,
                dailyTasks = listOf("one_gift"),
            ),
        )

        val updated = GiftUpdates.afterGiving(state(), "hiori", "book", given)

        assertTrue(updated.backpack.none { it.giftId == "book" })
        assertEquals(2, updated.draws)
        assertEquals(1, updated.resetCards)
        assertEquals("close", updated.characters.first { it.id == "hiori" }.tier)
        assertTrue(updated.dailyTasks!!.tasks.first { it.id == "one_gift" }.done)
        assertEquals(1, updated.dailyTasks!!.doneCount)
    }

    @Test
    fun givingOneOfSeveralKeepsTheRest() {
        val given = GivenGift(affinity = AffinityStatus("trust"), left = 1)
        val updated = GiftUpdates.afterGiving(state(), "mano", "book", given)
        assertEquals(1, updated.backpack.first { it.giftId == "book" }.count)
    }

    @Test
    fun withDailyDoneOnlyTouchesNamedTasks() {
        val tasks = state().dailyTasks!!
        val updated = GiftUpdates.withDailyDone(tasks, listOf("one_turn"))
        assertTrue(updated.tasks.first { it.id == "one_turn" }.done)
        assertTrue(!updated.tasks.first { it.id == "one_gift" }.done)
    }

    @Test
    fun closedStateStillWorks() {
        val closed = state().copy(open = false, dailyTasks = null, characters = emptyList())
        val updated = GiftUpdates.withDrawn(closed, DrawOutcome(listOf(DrawItem.ResetCard), 0))
        assertEquals(1, updated.resetCards)
        assertEquals(0, updated.draws)
    }
}

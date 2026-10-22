package sc.eidolon.nativeapp

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.core.Time
import sc.eidolon.nativeapp.data.api.DrawResultDto
import sc.eidolon.nativeapp.data.api.GiftsStateDto
import sc.eidolon.nativeapp.data.api.GivenDto
import sc.eidolon.nativeapp.data.api.HerAchievementsDto
import sc.eidolon.nativeapp.data.api.ResetResultDto
import sc.eidolon.nativeapp.data.api.toDomain
import sc.eidolon.nativeapp.model.AchievementGoal
import sc.eidolon.nativeapp.model.AchievementProgress
import sc.eidolon.nativeapp.model.DrawItem
import sc.eidolon.nativeapp.model.GiftCategory
import sc.eidolon.nativeapp.model.GiftRarity

class GiftsMappingTest {
    @Test
    fun mapsTheGiftStateAndItsTasksAndAchievements() {
        val payload = """
            {
              "open": true,
              "draws": 2,
              "max_draws": 7,
              "backpack": [{"gift_id":"cake","count":2},{"gift_id":"book","count":1}],
              "reset_cards": 1,
              "next_draw_at": "2026-10-23T16:00:00+00:00",
              "resettable": [{"id":"mano","tier":"distant","cooling":false}],
              "characters": [
                {"id":"mano","tier":"trust","cooling":false,"cooldown_until":null},
                {"id":"hiori","tier":"cold","cooling":true,"cooldown_until":"2026-10-22T10:00:00+00:00"}
              ],
              "daily_tasks": {
                "tasks": [
                  {"id":"one_turn","done":true,"progress":1,"goal":1,"draws":1},
                  {"id":"ten_turns","done":false,"progress":3,"goal":10,"draws":1}
                ],
                "renews_at": "2026-10-23T16:00:00+00:00"
              },
              "achievements": [
                {"id":"characters","progress":2,
                 "stages":[{"goal":3,"draws":1,"unlocked_at":null},
                           {"goal":10,"draws":2,"unlocked_at":null}]},
                {"id":"days","progress":null,
                 "stages":[{"goal":7,"draws":1,"unlocked_at":"2026-10-21T00:00:00+00:00"}]}
              ],
              "rewarded_at": "2026-10-22T03:00:00+00:00"
            }
        """.trimIndent()

        val state = EidolonJson.decodeFromString<GiftsStateDto>(payload).toDomain()

        assertTrue(state.open)
        assertEquals(2, state.draws)
        assertEquals(7, state.maxDraws)
        assertEquals("cake", state.backpack.first().giftId)
        assertEquals(2, state.backpack.first().count)
        assertEquals(1, state.resetCards)
        assertEquals(Time.parseInstant("2026-10-23T16:00:00+00:00"), state.nextDrawAt)
        assertEquals("mano", state.resettable.single().id)
        assertTrue(state.characters.last().cooling)
        assertEquals(
            Time.parseInstant("2026-10-22T10:00:00+00:00"),
            state.characters.last().cooldownUntil,
        )
        assertEquals(1, state.dailyTasks?.doneCount)
        assertEquals(2, state.dailyTasks?.tasks?.size)
        assertEquals(AchievementProgress.Count(2), state.achievements?.first()?.progress)
        assertEquals(AchievementProgress.None, state.achievements?.last()?.progress)
        assertEquals(
            AchievementGoal.Count(3),
            state.achievements?.first()?.stages?.first()?.goal,
        )
        assertEquals(
            Time.parseInstant("2026-10-21T00:00:00+00:00"),
            state.achievements?.last()?.stages?.single()?.unlockedAt,
        )
        assertEquals(Time.parseInstant("2026-10-22T03:00:00+00:00"), state.rewardedAt)
    }

    @Test
    fun keepsTheClosedStateAndExistingInventory() {
        val payload = """
            {"open":false,"draws":0,"max_draws":7,
             "backpack":[{"gift_id":"cake","count":1}],"reset_cards":2,
             "next_draw_at":null,"resettable":[],"characters":[],
             "daily_tasks":null,"achievements":null,"rewarded_at":null}
        """.trimIndent()

        val state = EidolonJson.decodeFromString<GiftsStateDto>(payload).toDomain()

        assertFalse(state.open)
        assertEquals(1, state.backpack.single().count)
        assertEquals(2, state.resetCards)
        assertNull(state.dailyTasks)
        assertNull(state.achievements)
        assertNull(state.nextDrawAt)
    }

    @Test
    fun mapsDrawGiveAndResetResults() {
        val draw = EidolonJson.decodeFromString<DrawResultDto>(
            """{"drawn":[{"kind":"gift","gift_id":"cake"},{"kind":"reset_card"}],
                "draws":3,
                "rewarded":{"draws":1,"reset_cards":0,"daily_tasks":["one_gift"],
                  "achievements":[{"achievement":"draws","character_id":null,"stage":1,"draws":1}],
                  "completed":[],"rewarded_at":"2026-10-22T03:00:00+00:00"}}""",
        ).toDomain()
        assertEquals(2, draw.drawn.size)
        assertEquals(DrawItem.Gift("cake"), draw.drawn.first())
        assertEquals(DrawItem.ResetCard, draw.drawn.last())
        assertEquals(3, draw.draws)
        assertEquals(1, draw.rewarded?.draws)
        assertEquals(listOf("one_gift"), draw.rewarded?.dailyTasks)

        val given = EidolonJson.decodeFromString<GivenDto>(
            """{"conversation_id":"c1",
                "change":{"up":true,"tier_changed":false,"tier":"trust","cooldown":false,"thawed":false},
                "affinity":{"tier":"trust","cooling":false,"cooldown_until":null},
                "left":1,"rewarded":null}""",
        ).toDomain()
        assertEquals("c1", given.conversationId)
        assertEquals(true, given.change?.up)
        assertEquals("trust", given.affinity?.tier)
        assertEquals(1, given.left)

        val reset = EidolonJson.decodeFromString<ResetResultDto>(
            """{"brought_back":["mano","hiori"],"reset_cards":0}""",
        ).toDomain()
        assertEquals(listOf("mano", "hiori"), reset.broughtBack)
        assertEquals(0, reset.resetCards)
    }

    @Test
    fun mapsPerCharacterAchievementsWithTierGoals() {
        val payload = """
            {
              "achievements": [
                {"id":"turns","progress":42,
                 "stages":[{"goal":10,"draws":1,"unlocked_at":"2026-10-21T00:00:00+00:00"},
                           {"goal":100,"draws":2,"unlocked_at":null}]},
                {"id":"tier","progress":"close",
                 "stages":[{"goal":"close","draws":1,"unlocked_at":"2026-10-21T00:00:00+00:00"},
                           {"goal":"intimate","draws":2,"unlocked_at":null}]}
              ],
              "complete":{"completed_at":null,"reset_cards":1}
            }
        """.trimIndent()

        val achievements = EidolonJson.decodeFromString<HerAchievementsDto>(payload).toDomain()

        assertEquals(AchievementProgress.Tier("close"), achievements.achievements.last().progress)
        assertEquals(
            AchievementGoal.Tier("intimate"),
            achievements.achievements.last().stages.last().goal,
        )
        assertEquals(1, achievements.complete.resetCards)
        assertNull(achievements.complete.completedAt)
    }

    @Test
    fun mapsGiftTaxonomies() {
        val gift = EidolonJson.decodeFromString<sc.eidolon.nativeapp.data.api.GiftDto>(
            """{"id":"cake","name":"蛋糕","category":"food","rarity":"rare","retired":false}""",
        ).toDomain()
        assertEquals(GiftCategory.Food, gift.category)
        assertEquals(GiftRarity.Rare, gift.rarity)
    }
}

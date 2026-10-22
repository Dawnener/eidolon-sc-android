package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sc.eidolon.nativeapp.model.AchievementGoal
import sc.eidolon.nativeapp.model.AchievementProgress
import sc.eidolon.nativeapp.model.AchievementStage
import sc.eidolon.nativeapp.model.AffinityChange
import sc.eidolon.nativeapp.model.AffinityStatus
import sc.eidolon.nativeapp.model.GivenGift
import sc.eidolon.nativeapp.model.Reward
import sc.eidolon.nativeapp.model.RewardAchievement
import sc.eidolon.nativeapp.model.RewardCompletion
import sc.eidolon.nativeapp.model.achievementGoalText
import sc.eidolon.nativeapp.model.achievementProgressText
import sc.eidolon.nativeapp.model.achievementStageText
import sc.eidolon.nativeapp.model.cueText
import sc.eidolon.nativeapp.model.givenNote
import sc.eidolon.nativeapp.model.rewardNoticeText
import sc.eidolon.nativeapp.model.tierLabel
import sc.eidolon.nativeapp.model.untilText
import java.time.Instant
import java.time.ZoneOffset

class GiftTextTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val zone = ZoneOffset.UTC

    @Test
    fun cuePriorityAndWording() {
        assertEquals(
            "她 现在不想说话了",
            cueText(AffinityChange(cooldown = true, thawed = true, up = true), "她"),
        )
        assertEquals(
            "她 愿意和你说话了",
            cueText(AffinityChange(thawed = true), "她"),
        )
        assertEquals(
            "她 和你的关系变成了「亲近」",
            cueText(AffinityChange(tierChanged = true, tier = "close"), "她"),
        )
        assertEquals("她 似乎更开心了一点", cueText(AffinityChange(up = true), "她"))
        assertEquals("她 似乎有点不高兴", cueText(AffinityChange(up = false), "她"))
        assertNull(cueText(null, "她"))
    }

    @Test
    fun timeWordingUsesTodayAndTomorrow() {
        assertEquals(
            "今天 15:30",
            untilText(Instant.parse("2026-10-21T15:30:00Z"), now, zone),
        )
        assertEquals(
            "明天 1:00",
            untilText(Instant.parse("2026-10-22T01:00:00Z"), now, zone),
        )
        assertEquals("之后", untilText(null, now, zone))
    }

    @Test
    fun achievementTextHandlesCountsAndTiers() {
        assertEquals("亲近", tierLabel("close"))
        assertEquals("420", achievementProgressText(AchievementProgress.Count(420)))
        assertEquals("亲密", achievementProgressText(AchievementProgress.Tier("intimate")))
        assertEquals("—", achievementProgressText(AchievementProgress.None))
        assertEquals("1000", achievementGoalText(AchievementGoal.Count(1000)))
        assertEquals("亲密", achievementGoalText(AchievementGoal.Tier("intimate")))
        assertEquals(
            "下一个：亲密",
            achievementStageText(AchievementStage(AchievementGoal.Tier("intimate"), 2)),
        )
    }

    @Test
    fun rewardNoticeIsCompact() {
        val reward = Reward(
            draws = 2,
            resetCards = 1,
            dailyTasks = listOf("one_turn", "one_gift"),
            achievements = listOf(RewardAchievement("turns", "mano", 1, 1)),
            completed = listOf(RewardCompletion("mano", 1)),
        )
        assertEquals(
            "抽奖 +2 · 重置卡 +1 · 每日任务完成 2 项 · 成就解锁 1 个阶段 · 集齐 1 位角色",
            rewardNoticeText(reward),
        )
        assertNull(rewardNoticeText(Reward()))
    }

    @Test
    fun givenNotePrefersThawAndThenCooldown() {
        val thawed = GivenGift(
            change = AffinityChange(thawed = true),
            affinity = AffinityStatus("trust"),
            left = 0,
        )
        assertEquals("真乃 愿意和你说话了", givenNote("真乃", thawed, now, zone))

        val cooling = GivenGift(
            affinity = AffinityStatus("cold", Instant.parse("2026-10-22T01:00:00Z")),
            left = 0,
        )
        assertEquals("真乃 还没消气，明天 1:00 后会愿意说话", givenNote("真乃", cooling, now, zone))
        assertEquals(
            "已经送给真乃",
            givenNote("真乃", GivenGift(affinity = AffinityStatus("trust"), left = 0), now, zone),
        )
    }
}

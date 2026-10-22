package sc.eidolon.nativeapp.model

import sc.eidolon.nativeapp.core.Time
import java.time.Instant
import java.time.ZoneId

fun tierLabel(tier: String?): String = when (tier) {
    "cold" -> "冷淡"
    "distant" -> "疏远"
    "trust" -> "信任"
    "close" -> "亲近"
    "intimate" -> "亲密"
    else -> tier.orEmpty()
}

fun tierNote(tier: String?): String = when (tier) {
    "cold" -> "她和你还很生疏。"
    "distant" -> "你们之间还有些距离。"
    "trust" -> "她愿意和你正常说话。"
    "close" -> "她和你已经很亲近了。"
    "intimate" -> "她把你当成特别的人。"
    else -> "关系会随着你们的往来变化。"
}

fun cueText(change: AffinityChange?, name: String): String? {
    if (change == null) return null
    return when {
        change.cooldown -> "$name 现在不想说话了"
        change.thawed -> "$name 愿意和你说话了"
        change.tierChanged -> "$name 和你的关系变成了「${tierLabel(change.tier)}」"
        change.up == true -> "$name 似乎更开心了一点"
        change.up == false -> "$name 似乎有点不高兴"
        else -> null
    }
}

fun untilText(instant: Instant?, now: Instant, zone: ZoneId): String {
    if (instant == null) return "之后"
    val date = Time.localDate(instant, zone)
    val today = Time.localDate(now, zone)
    val clock = Time.formatClock(instant, zone)
    return when {
        date == today -> "今天 $clock"
        date == today.plusDays(1) -> "明天 $clock"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日 $clock"
        else -> "${date.year}/${date.monthValue}/${date.dayOfMonth} $clock"
    }
}

fun GiftCategory.label(): String = when (this) {
    GiftCategory.Food -> "吃的"
    GiftCategory.Drink -> "饮品"
    GiftCategory.Flower -> "花草"
    GiftCategory.Trinket -> "小物件"
    GiftCategory.Unknown -> "其他"
}

fun GiftRarity.label(): String = when (this) {
    GiftRarity.Common -> "普通"
    GiftRarity.Uncommon -> "少见"
    GiftRarity.Rare -> "稀有"
    GiftRarity.Unknown -> "未知"
}

fun dailyTaskName(id: String): String = when (id) {
    "one_turn" -> "和她说一句话"
    "one_gift" -> "送出一件礼物"
    "ten_turns" -> "和她说十句话"
    else -> id
}

fun achievementName(id: String): String = when (id) {
    "turns" -> "对话次数"
    "call_time" -> "通话时长"
    "gifts" -> "送出的礼物"
    "tier" -> "关系档位"
    "characters" -> "认识的角色"
    "intimate_characters" -> "亲密角色"
    "days" -> "来访天数"
    "draws" -> "抽奖次数"
    else -> id
}

fun achievementProgressText(progress: AchievementProgress): String = when (progress) {
    is AchievementProgress.Count -> "${progress.value}"
    is AchievementProgress.Tier -> tierLabel(progress.tier)
    AchievementProgress.None -> "—"
}

fun achievementGoalText(goal: AchievementGoal): String = when (goal) {
    is AchievementGoal.Count -> "${goal.value}"
    is AchievementGoal.Tier -> tierLabel(goal.tier)
}

fun achievementStageText(stage: AchievementStage): String = when (val goal = stage.goal) {
    is AchievementGoal.Count -> "下一个：$goal.value"
    is AchievementGoal.Tier -> "下一个：${tierLabel(goal.tier)}"
}

fun achievementRewardText(draws: Int): String = "抽奖 ×$draws"

fun nextDrawText(instant: Instant?, now: Instant, zone: ZoneId): String =
    if (instant == null) "下一次发放时间未知" else "${untilText(instant, now, zone)} 再发 1 次"

fun dailyRenewText(instant: Instant?, now: Instant, zone: ZoneId): String =
    if (instant == null) "每天更新" else "${untilText(instant, now, zone)} 换新"

fun rewardNoticeText(reward: Reward): String? {
    if (reward.isEmpty) return null
    val parts = buildList {
        if (reward.draws > 0) add("抽奖 +${reward.draws}")
        if (reward.resetCards > 0) add("重置卡 +${reward.resetCards}")
        if (reward.dailyTasks.isNotEmpty()) add("每日任务完成 ${reward.dailyTasks.size} 项")
        if (reward.achievements.isNotEmpty()) add("成就解锁 ${reward.achievements.size} 个阶段")
        if (reward.completed.isNotEmpty()) add("集齐 ${reward.completed.size} 位角色")
    }
    return parts.joinToString(" · ").ifBlank { null }
}

fun givenNote(
    name: String,
    given: GivenGift,
    now: Instant,
    zone: ZoneId,
): String {
    val change = given.change
    return when {
        change?.thawed == true -> "$name 愿意和你说话了"
        change?.cooldown == true -> "$name 现在不想说话"
        given.affinity?.cooldownUntil != null ->
            "$name 还没消气，${untilText(given.affinity.cooldownUntil, now, zone)} 后会愿意说话"
        else -> "已经送给$name"
    }
}

/** All five tiers, low to high, for the affinity ladder. */
val TIER_LADDER = listOf("cold", "distant", "trust", "close", "intimate")

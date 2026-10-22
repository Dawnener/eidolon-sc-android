package sc.eidolon.nativeapp.model

import java.time.Instant

enum class GiftCategory { Food, Drink, Flower, Trinket, Unknown }

enum class GiftRarity { Common, Uncommon, Rare, Unknown }

data class GiftInfo(
    val id: String,
    val name: String,
    val category: GiftCategory,
    val rarity: GiftRarity,
    val retired: Boolean,
)

data class BackpackItem(val giftId: String, val count: Int)

data class GiftCharacter(
    val id: String,
    val tier: String? = null,
    val cooling: Boolean = false,
    val cooldownUntil: Instant? = null,
)

data class ResettableCharacter(
    val id: String,
    val tier: String? = null,
    val cooling: Boolean = false,
)

data class DailyTask(
    val id: String,
    val done: Boolean,
    val progress: Int,
    val goal: Int,
    val draws: Int,
)

data class DailyTasks(
    val tasks: List<DailyTask>,
    val renewsAt: Instant? = null,
) {
    val doneCount: Int get() = tasks.count { it.done }
    val allDone: Boolean get() = tasks.isNotEmpty() && tasks.all { it.done }
}

sealed interface AchievementProgress {
    data class Count(val value: Long) : AchievementProgress

    data class Tier(val tier: String) : AchievementProgress

    data object None : AchievementProgress
}

sealed interface AchievementGoal {
    data class Count(val value: Long) : AchievementGoal

    data class Tier(val tier: String) : AchievementGoal
}

data class AchievementStage(
    val goal: AchievementGoal,
    val draws: Int,
    val unlockedAt: Instant? = null,
) {
    val unlocked: Boolean get() = unlockedAt != null
}

data class Achievement(
    val id: String,
    val progress: AchievementProgress,
    val stages: List<AchievementStage>,
)

data class CompleteInfo(
    val completedAt: Instant? = null,
    val resetCards: Int = 0,
)

data class HerAchievements(
    val achievements: List<Achievement>,
    val complete: CompleteInfo,
)

data class GiftState(
    val open: Boolean,
    val draws: Int,
    val maxDraws: Int,
    val backpack: List<BackpackItem>,
    val resetCards: Int,
    val nextDrawAt: Instant? = null,
    val resettable: List<ResettableCharacter> = emptyList(),
    val characters: List<GiftCharacter> = emptyList(),
    val dailyTasks: DailyTasks? = null,
    val achievements: List<Achievement>? = null,
    val rewardedAt: Instant? = null,
)

sealed interface DrawItem {
    data class Gift(val giftId: String) : DrawItem

    data object ResetCard : DrawItem
}

data class RewardAchievement(
    val achievement: String,
    val characterId: String? = null,
    val stage: Int,
    val draws: Int,
)

data class RewardCompletion(
    val characterId: String,
    val resetCards: Int,
)

data class Reward(
    val draws: Int = 0,
    val resetCards: Int = 0,
    val dailyTasks: List<String> = emptyList(),
    val achievements: List<RewardAchievement> = emptyList(),
    val completed: List<RewardCompletion> = emptyList(),
    val rewardedAt: Instant? = null,
) {
    val isEmpty: Boolean
        get() = draws == 0 && resetCards == 0 && dailyTasks.isEmpty() &&
            achievements.isEmpty() && completed.isEmpty()
}

data class DrawOutcome(
    val drawn: List<DrawItem>,
    val draws: Int,
    val rewarded: Reward? = null,
)

data class GivenGift(
    val conversationId: String? = null,
    val change: AffinityChange? = null,
    val affinity: AffinityStatus? = null,
    val left: Int,
    val rewarded: Reward? = null,
)

data class ResetOutcome(
    val broughtBack: List<String>,
    val resetCards: Int,
)

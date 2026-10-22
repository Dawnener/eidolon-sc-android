package sc.eidolon.nativeapp.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import sc.eidolon.nativeapp.core.Time
import sc.eidolon.nativeapp.model.Achievement
import sc.eidolon.nativeapp.model.AchievementGoal
import sc.eidolon.nativeapp.model.AchievementProgress
import sc.eidolon.nativeapp.model.AchievementStage
import sc.eidolon.nativeapp.model.BackpackItem
import sc.eidolon.nativeapp.model.CompleteInfo
import sc.eidolon.nativeapp.model.DailyTask
import sc.eidolon.nativeapp.model.DailyTasks
import sc.eidolon.nativeapp.model.DrawItem
import sc.eidolon.nativeapp.model.DrawOutcome
import sc.eidolon.nativeapp.model.GiftCategory
import sc.eidolon.nativeapp.model.GiftCharacter
import sc.eidolon.nativeapp.model.GiftInfo
import sc.eidolon.nativeapp.model.GiftRarity
import sc.eidolon.nativeapp.model.GiftState
import sc.eidolon.nativeapp.model.GivenGift
import sc.eidolon.nativeapp.model.HerAchievements
import sc.eidolon.nativeapp.model.ResetOutcome
import sc.eidolon.nativeapp.model.ResettableCharacter
import sc.eidolon.nativeapp.model.Reward
import sc.eidolon.nativeapp.model.RewardAchievement
import sc.eidolon.nativeapp.model.RewardCompletion

@Serializable
data class GiftDto(
    val id: String,
    val name: String,
    val category: String = "unknown",
    val rarity: String = "unknown",
    val retired: Boolean = false,
)

@Serializable
data class BackpackDto(
    @SerialName("gift_id") val giftId: String,
    val count: Int = 0,
)

@Serializable
data class GiftCharacterDto(
    val id: String,
    val tier: String? = null,
    val cooling: Boolean = false,
    @SerialName("cooldown_until") val cooldownUntil: String? = null,
)

@Serializable
data class ResettableDto(
    val id: String,
    val tier: String? = null,
    val cooling: Boolean = false,
)

@Serializable
data class DailyTaskDto(
    val id: String,
    val done: Boolean = false,
    val progress: Int = 0,
    val goal: Int = 0,
    val draws: Int = 0,
)

@Serializable
data class DailyTasksDto(
    val tasks: List<DailyTaskDto> = emptyList(),
    @SerialName("renews_at") val renewsAt: String? = null,
)

@Serializable
data class AchievementStageDto(
    val goal: JsonElement,
    val draws: Int = 0,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
)

@Serializable
data class AchievementDto(
    val id: String,
    val progress: JsonElement? = null,
    val stages: List<AchievementStageDto> = emptyList(),
)

@Serializable
data class CompleteDto(
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("reset_cards") val resetCards: Int = 0,
)

@Serializable
data class HerAchievementsDto(
    val achievements: List<AchievementDto> = emptyList(),
    val complete: CompleteDto = CompleteDto(),
)

@Serializable
data class GiftsStateDto(
    val open: Boolean = false,
    val draws: Int = 0,
    @SerialName("max_draws") val maxDraws: Int = 0,
    val backpack: List<BackpackDto> = emptyList(),
    @SerialName("reset_cards") val resetCards: Int = 0,
    @SerialName("next_draw_at") val nextDrawAt: String? = null,
    val resettable: List<ResettableDto> = emptyList(),
    val characters: List<GiftCharacterDto> = emptyList(),
    @SerialName("daily_tasks") val dailyTasks: DailyTasksDto? = null,
    val achievements: List<AchievementDto>? = null,
    @SerialName("rewarded_at") val rewardedAt: String? = null,
)

@Serializable
data class DrawRequest(val all: Boolean = false)

@Serializable
data class DrawnDto(
    val kind: String,
    @SerialName("gift_id") val giftId: String? = null,
)

@Serializable
data class DrawResultDto(
    val drawn: List<DrawnDto> = emptyList(),
    val draws: Int = 0,
    val rewarded: RewardedDto? = null,
)

@Serializable
data class GiveGiftRequest(@SerialName("gift_id") val giftId: String)

@Serializable
data class GivenDto(
    @SerialName("conversation_id") val conversationId: String? = null,
    val change: AffinityDto? = null,
    val affinity: AffinityStatusDto? = null,
    val left: Int = 0,
    val rewarded: RewardedDto? = null,
)

@Serializable
data class ResetResultDto(
    @SerialName("brought_back") val broughtBack: List<String> = emptyList(),
    @SerialName("reset_cards") val resetCards: Int = 0,
)

@Serializable
data class RewardAchievementDto(
    val achievement: String,
    @SerialName("character_id") val characterId: String? = null,
    val stage: Int = 0,
    val draws: Int = 0,
)

@Serializable
data class RewardCompletionDto(
    @SerialName("character_id") val characterId: String,
    @SerialName("reset_cards") val resetCards: Int = 0,
)

@Serializable
data class RewardedDto(
    val draws: Int = 0,
    @SerialName("reset_cards") val resetCards: Int = 0,
    @SerialName("daily_tasks") val dailyTasks: List<String> = emptyList(),
    val achievements: List<RewardAchievementDto> = emptyList(),
    val completed: List<RewardCompletionDto> = emptyList(),
    @SerialName("rewarded_at") val rewardedAt: String? = null,
)

fun GiftDto.toDomain(): GiftInfo = GiftInfo(
    id = id,
    name = name,
    category = when (category) {
        "food" -> GiftCategory.Food
        "drink" -> GiftCategory.Drink
        "flower" -> GiftCategory.Flower
        "trinket" -> GiftCategory.Trinket
        else -> GiftCategory.Unknown
    },
    rarity = when (rarity) {
        "common" -> GiftRarity.Common
        "uncommon" -> GiftRarity.Uncommon
        "rare" -> GiftRarity.Rare
        else -> GiftRarity.Unknown
    },
    retired = retired,
)

fun GiftsStateDto.toDomain(): GiftState = GiftState(
    open = open,
    draws = draws,
    maxDraws = maxDraws,
    backpack = backpack.map { BackpackItem(it.giftId, it.count) },
    resetCards = resetCards,
    nextDrawAt = Time.parseInstant(nextDrawAt),
    resettable = resettable.map {
        ResettableCharacter(it.id, it.tier, it.cooling)
    },
    characters = characters.map {
        GiftCharacter(
            id = it.id,
            tier = it.tier,
            cooling = it.cooling,
            cooldownUntil = Time.parseInstant(it.cooldownUntil),
        )
    },
    dailyTasks = dailyTasks?.toDomain(),
    achievements = achievements?.map { it.toDomain() },
    rewardedAt = Time.parseInstant(rewardedAt),
)

fun DailyTasksDto.toDomain(): DailyTasks = DailyTasks(
    tasks = tasks.map { DailyTask(it.id, it.done, it.progress, it.goal, it.draws) },
    renewsAt = Time.parseInstant(renewsAt),
)

private fun JsonElement?.asProgress(): AchievementProgress = when {
    this == null || this is JsonNull -> AchievementProgress.None
    this is JsonPrimitive && isString -> AchievementProgress.Tier(content)
    this is JsonPrimitive -> AchievementProgress.Count(longOrNull ?: 0)
    else -> AchievementProgress.None
}

private fun JsonElement.asGoal(): AchievementGoal = when {
    this is JsonPrimitive && isString -> AchievementGoal.Tier(content)
    this is JsonPrimitive -> AchievementGoal.Count(longOrNull ?: contentOrNull?.toLongOrNull() ?: 0)
    else -> AchievementGoal.Count(0)
}

fun AchievementDto.toDomain(): Achievement = Achievement(
    id = id,
    progress = progress.asProgress(),
    stages = stages.map {
        AchievementStage(
            goal = it.goal.asGoal(),
            draws = it.draws,
            unlockedAt = Time.parseInstant(it.unlockedAt),
        )
    },
)

fun HerAchievementsDto.toDomain(): HerAchievements = HerAchievements(
    achievements = achievements.map { it.toDomain() },
    complete = CompleteInfo(
        completedAt = Time.parseInstant(complete.completedAt),
        resetCards = complete.resetCards,
    ),
)

fun DrawResultDto.toDomain(): DrawOutcome = DrawOutcome(
    drawn = drawn.map {
        if (it.kind == "reset_card" || it.giftId == null) {
            DrawItem.ResetCard
        } else {
            DrawItem.Gift(it.giftId)
        }
    },
    draws = draws,
    rewarded = rewarded?.toDomain(),
)

fun GivenDto.toDomain(): GivenGift = GivenGift(
    conversationId = conversationId,
    change = change?.toDomain(),
    affinity = affinity?.toDomain(),
    left = left,
    rewarded = rewarded?.toDomain(),
)

fun ResetResultDto.toDomain(): ResetOutcome = ResetOutcome(
    broughtBack = broughtBack,
    resetCards = resetCards,
)

fun RewardedDto.toDomain(): Reward = Reward(
    draws = draws,
    resetCards = resetCards,
    dailyTasks = dailyTasks,
    achievements = achievements.map {
        RewardAchievement(it.achievement, it.characterId, it.stage, it.draws)
    },
    completed = completed.map { RewardCompletion(it.characterId, it.resetCards) },
    rewardedAt = Time.parseInstant(rewardedAt),
)

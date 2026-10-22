package sc.eidolon.nativeapp.model

import java.time.Instant

data class Character(
    val id: String,
    val name: String,
    val intro: String,
    val color: String,
    val avatarUrl: String?,
    val nameZh: String? = null,
    val nameKana: String? = null,
    val nameRomaji: String? = null,
    val unit: String? = null,
    val unitReading: String? = null,
    val unitOrder: Int? = null,
    val idolId: Int? = null,
)

data class Segment(val ja: String, val zh: String)

enum class Lang { Ja, Zh }

enum class PhotoStatus { Pending, Ready, Failed }

data class Photo(val id: String, val status: PhotoStatus, val reason: String? = null)

data class AffinityChange(
    val up: Boolean? = null,
    val tierChanged: Boolean = false,
    val tier: String? = null,
    val cooldown: Boolean = false,
    val thawed: Boolean = false,
)

data class AffinityStatus(val tier: String? = null, val cooldownUntil: Instant? = null)

enum class EventKind {
    HungUp,
    Gift,
    Ended;

    companion object {
        fun fromWire(value: String?): EventKind? = when (value) {
            "hung_up" -> HungUp
            "gift" -> Gift
            "ended" -> Ended
            else -> null
        }
    }
}

sealed interface Recorded {
    data class UserMessage(val id: Int, val text: String, val at: Instant) : Recorded

    data class CharacterMessage(
        val id: Int,
        val ja: String,
        val zh: String,
        val segments: List<Segment>,
        val at: Instant,
        val affinity: AffinityChange? = null,
        val photo: Photo? = null,
    ) : Recorded

    data class ConversationEvent(
        val id: Int,
        val kind: EventKind,
        val at: Instant,
        val giftName: String? = null,
        val giftId: String? = null,
        val affinity: AffinityChange? = null,
    ) : Recorded

    data class ReachOut(
        val id: Int,
        val ja: String,
        val zh: String,
        val segments: List<Segment>,
        val at: Instant,
    ) : Recorded
}

data class LastPreview(
    val at: Instant,
    val role: String? = null,
    val text: String? = null,
    val ja: String? = null,
    val zh: String? = null,
    val photo: Boolean = false,
    val reachOut: Boolean = false,
    val eventKind: EventKind? = null,
    val giftName: String? = null,
)

data class LatestConversation(
    val characterId: String,
    val conversationId: String,
    val createdAt: Instant,
    val lastMessage: LastPreview? = null,
    val unseenReachOut: Boolean = false,
)

data class Conversation(
    val id: String,
    val characterId: String,
    val createdAt: Instant,
    val title: String? = null,
    val lastMessage: LastPreview? = null,
    val ended: Boolean = false,
)

data class Account(
    val id: String,
    val name: String,
    val signInMethods: List<SignInMethod>,
    val isAdmin: Boolean,
    val exemptingKeys: List<String>,
    val reachOuts: Boolean?,
    val sponsorVersion: String?,
)

/** The producer profile the characters are told about; every field may be left empty. */
data class ProducerProfile(
    val formOfAddress: String? = null,
    val gender: String? = null,
    val timezone: String? = null,
    val birthdayMonth: Int? = null,
    val birthdayDay: Int? = null,
)

data class SignInMethod(val provider: String, val displayName: String? = null, val email: String? = null)

/** The model kept on the server for reach-outs; the key itself never comes back. */
data class ReachOutModel(
    val configured: Boolean,
    val baseUrl: String? = null,
    val model: String? = null,
    val protocol: String? = null,
    val last4: String? = null,
)

data class Memory(
    val id: Int,
    val text: String,
    val sourceCharacterId: String? = null,
    val sourceCharacterName: String? = null,
    val createdAt: Instant,
    val superseded: Boolean = false,
)

data class Stats(
    val daysKnown: Int,
    val visits: StatsVisits,
    val allHours: List<Int>,
    val heatmap: StatsHeatmap,
    val affinity: List<StatsTier>?,
    val achievements: StatsAchievements?,
    val range: StatsRange,
)

data class StatsVisits(val current: Int, val longest: Int)

data class StatsHeatmap(val from: String, val counts: List<Int>)

data class StatsTier(val tier: String, val characters: List<String>)

data class StatsAchievements(val stages: Int, val complete: Int)

data class StatsRange(
    val span: String,
    val turns: Int,
    val byHour: List<Int>,
    val characters: List<CharacterStats>,
    val calls: StatsCalls,
    val gifts: StatsGifts?,
)

data class CharacterStats(
    val characterId: String,
    val turns: Int,
    val daysKnown: Int,
    val callSeconds: Double,
)

data class StatsCalls(val count: Int, val seconds: Double)

data class StatsGifts(val given: Int, val liked: Int, val most: StatsGiftMost? = null)

data class StatsGiftMost(val characterId: String, val given: Int)

sealed interface TurnEvent {
    data class User(val text: String) : TurnEvent

    data class PhotoStart(val id: String) : TurnEvent

    data class Delta(val segment: Int, val lang: Lang, val text: String) : TurnEvent

    data class PhotosUsedUp(val retryAt: Instant) : TurnEvent

    data class Done(
        val message: Recorded.CharacterMessage,
        val ended: Recorded.ConversationEvent? = null,
        val affinity: AffinityStatus? = null,
        val rewarded: Reward? = null,
    ) : TurnEvent

    data class Error(val message: String) : TurnEvent
}

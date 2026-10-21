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

data class SignInMethod(val provider: String, val displayName: String? = null, val email: String? = null)

data class Memory(
    val id: Int,
    val text: String,
    val sourceCharacterId: String? = null,
    val sourceCharacterName: String? = null,
    val createdAt: Instant,
    val superseded: Boolean = false,
)

sealed interface TurnEvent {
    data class User(val text: String) : TurnEvent

    data class PhotoStart(val id: String) : TurnEvent

    data class Delta(val segment: Int, val lang: Lang, val text: String) : TurnEvent

    data class PhotosUsedUp(val retryAt: Instant) : TurnEvent

    data class Done(
        val message: Recorded.CharacterMessage,
        val ended: Recorded.ConversationEvent? = null,
        val affinity: AffinityStatus? = null,
    ) : TurnEvent

    data class Error(val message: String) : TurnEvent
}

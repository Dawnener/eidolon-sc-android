package sc.eidolon.nativeapp.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sc.eidolon.nativeapp.core.Time
import sc.eidolon.nativeapp.model.Account
import sc.eidolon.nativeapp.model.AffinityChange
import sc.eidolon.nativeapp.model.AffinityStatus
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.Conversation
import sc.eidolon.nativeapp.model.EventKind
import sc.eidolon.nativeapp.model.LastPreview
import sc.eidolon.nativeapp.model.LatestConversation
import sc.eidolon.nativeapp.model.Memory
import sc.eidolon.nativeapp.model.OwnKeysRequest
import sc.eidolon.nativeapp.model.Photo
import sc.eidolon.nativeapp.model.PhotoStatus
import sc.eidolon.nativeapp.model.ProducerProfile
import sc.eidolon.nativeapp.model.Recorded
import sc.eidolon.nativeapp.model.ReachOutModel
import sc.eidolon.nativeapp.model.Segment
import sc.eidolon.nativeapp.model.SignInMethod
import sc.eidolon.nativeapp.model.Stats
import sc.eidolon.nativeapp.model.StatsAchievements
import sc.eidolon.nativeapp.model.StatsCalls
import sc.eidolon.nativeapp.model.StatsGiftMost
import sc.eidolon.nativeapp.model.StatsGifts
import sc.eidolon.nativeapp.model.StatsHeatmap
import sc.eidolon.nativeapp.model.StatsRange
import sc.eidolon.nativeapp.model.StatsTier
import sc.eidolon.nativeapp.model.StatsVisits
import sc.eidolon.nativeapp.model.CharacterStats
import java.time.Instant

@Serializable
data class AuthProvidersDto(
    val providers: List<String> = emptyList(),
    @SerialName("dev_login") val devLogin: Boolean = false,
    val contact: String? = null,
)

@Serializable
data class HealthDto(
    val ok: Boolean = false,
    @SerialName("deployment_id") val deploymentId: String? = null,
)

@Serializable
data class ReachOutModelDto(
    val configured: Boolean = false,
    @SerialName("base_url") val baseUrl: String? = null,
    val model: String? = null,
    val protocol: String? = null,
    val last4: String? = null,
)

fun ReachOutModelDto.toDomain(): ReachOutModel = ReachOutModel(
    configured = configured,
    baseUrl = baseUrl,
    model = model,
    protocol = protocol,
    last4 = last4,
)

@Serializable
data class BirthdayDto(val month: Int, val day: Int)

@Serializable
data class ProfileDto(
    @SerialName("form_of_address") val formOfAddress: String? = null,
    val gender: String? = null,
    val timezone: String? = null,
    val birthday: BirthdayDto? = null,
)

@Serializable
data class ProfileRequest(
    @SerialName("form_of_address") val formOfAddress: String? = null,
    val gender: String? = null,
    val timezone: String? = null,
    val birthday: BirthdayDto? = null,
)

fun ProfileDto.toDomain(): ProducerProfile = ProducerProfile(
    formOfAddress = formOfAddress,
    gender = gender,
    timezone = timezone,
    birthdayMonth = birthday?.month,
    birthdayDay = birthday?.day,
)

@Serializable
data class PushEventDto(
    val type: String = "reach_out",
    @SerialName("character_id") val characterId: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    val title: String = "",
    val body: String = "",
)

@Serializable
data class ReachOutSwitchRequest(val on: Boolean)

@Serializable
data class ExemptionDto(val turns: Boolean = false, val calls: Boolean = false)

@Serializable
data class DevLoginRequest(val name: String)

@Serializable
data class MobileExchangeRequest(val code: String)

@Serializable
data class ConsentRequest(
    @SerialName("agree_to_terms") val agreeToTerms: Boolean,
    @SerialName("at_least_13") val atLeast13: Boolean,
)

@Serializable
data class DeleteAccountRequest(val confirm: Boolean)

@Serializable
data class SignInMethodDto(
    val provider: String,
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
)

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    @SerialName("sign_in_methods") val signInMethods: List<SignInMethodDto> = emptyList(),
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("exempting_keys") val exemptingKeys: List<String> = emptyList(),
    @SerialName("reach_outs") val reachOuts: Boolean? = null,
    @SerialName("sponsor_version") val sponsorVersion: String? = null,
)

@Serializable
data class CharacterDto(
    val id: String,
    val name: String,
    val intro: String = "",
    val color: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("name_zh") val nameZh: String? = null,
    @SerialName("name_kana") val nameKana: String? = null,
    @SerialName("name_romaji") val nameRomaji: String? = null,
    val unit: String? = null,
    @SerialName("unit_reading") val unitReading: String? = null,
    @SerialName("unit_order") val unitOrder: Int? = null,
    @SerialName("idol_id") val idolId: Int? = null,
)

@Serializable
data class LastMessageDto(
    val role: String? = null,
    val text: String? = null,
    val ja: String? = null,
    val zh: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val photo: Boolean? = null,
    @SerialName("reach_out") val reachOut: Boolean? = null,
    val type: String? = null,
    val kind: String? = null,
    @SerialName("gift_name") val giftName: String? = null,
)

@Serializable
data class LatestConversationDto(
    @SerialName("character_id") val characterId: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_message") val lastMessage: LastMessageDto? = null,
    @SerialName("unseen_reach_out") val unseenReachOut: Boolean = false,
)

@Serializable
data class ConversationDto(
    val id: String,
    @SerialName("character_id") val characterId: String,
    @SerialName("created_at") val createdAt: String,
    val title: String? = null,
    @SerialName("last_message") val lastMessage: LastMessageDto? = null,
    val ended: Boolean = false,
)

@Serializable
data class NewConversationRequest(@SerialName("character_id") val characterId: String)

@Serializable
data class SegmentDto(val ja: String, val zh: String)

@Serializable
data class AffinityDto(
    val up: Boolean? = null,
    @SerialName("tier_changed") val tierChanged: Boolean = false,
    val tier: String? = null,
    val cooldown: Boolean = false,
    val thawed: Boolean = false,
)

@Serializable
data class PhotoDto(val id: String, val status: String, val reason: String? = null)

@Serializable
data class MessageDto(
    val id: Int,
    val role: String,
    val text: String? = null,
    val ja: String? = null,
    val zh: String? = null,
    @SerialName("created_at") val createdAt: String,
    val segments: List<SegmentDto> = emptyList(),
    val affinity: AffinityDto? = null,
    val photo: PhotoDto? = null,
)

@Serializable
data class EventDto(
    val type: String = "event",
    val id: Int,
    val kind: String,
    @SerialName("created_at") val createdAt: String,
    val affinity: AffinityDto? = null,
    @SerialName("gift_id") val giftId: String? = null,
    @SerialName("gift_name") val giftName: String? = null,
)

@Serializable
data class ReachOutDto(
    val type: String = "reach_out",
    val id: Int,
    val ja: String,
    val zh: String,
    val segments: List<SegmentDto> = emptyList(),
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AffinityStatusDto(
    val tier: String? = null,
    @SerialName("cooldown_until") val cooldownUntil: String? = null,
)

@Serializable
data class TurnRequestDto(
    val text: String,
    @SerialName("own_keys") val ownKeys: OwnKeysRequest? = null,
)

@Serializable
data class DeltaDto(val segment: Int, val lang: String, val text: String)

@Serializable
data class UserEventDto(val text: String)

@Serializable
data class PhotoEventDto(val id: String, val status: String = "pending")

@Serializable
data class PhotosUsedUpDto(@SerialName("retry_at") val retryAt: String)

@Serializable
data class ErrorEventDto(val message: String)

@Serializable
data class DoneDto(
    val message: MessageDto,
    val affinity: AffinityStatusDto? = null,
    val ended: EventDto? = null,
    val rewarded: RewardedDto? = null,
)

@Serializable
data class MemoryDto(
    val id: Int,
    val text: String,
    @SerialName("source_character_id") val sourceCharacterId: String? = null,
    @SerialName("source_character_name") val sourceCharacterName: String? = null,
    @SerialName("created_at") val createdAt: String,
    val superseded: Boolean = false,
)

fun CharacterDto.toDomain(): Character = Character(
    id = id,
    name = name,
    intro = intro,
    color = color,
    avatarUrl = avatarUrl,
    nameZh = nameZh,
    nameKana = nameKana,
    nameRomaji = nameRomaji,
    unit = unit,
    unitReading = unitReading,
    unitOrder = unitOrder,
    idolId = idolId,
)

fun AffinityDto.toDomain(): AffinityChange = AffinityChange(
    up = up,
    tierChanged = tierChanged,
    tier = tier,
    cooldown = cooldown,
    thawed = thawed,
)

fun PhotoDto.toDomain(): Photo = Photo(
    id = id,
    status = when (status) {
        "ready" -> PhotoStatus.Ready
        "failed" -> PhotoStatus.Failed
        else -> PhotoStatus.Pending
    },
    reason = reason,
)

fun AffinityStatusDto.toDomain(): AffinityStatus = AffinityStatus(
    tier = tier,
    cooldownUntil = Time.parseInstant(cooldownUntil),
)

fun LastMessageDto.toPreview(fallbackAt: Instant): LastPreview {
    val at = Time.parseInstant(createdAt) ?: fallbackAt
    val event = EventKind.fromWire(kind)
    return if (type == "event" || event != null) {
        LastPreview(at = at, eventKind = event, giftName = giftName)
    } else {
        LastPreview(
            at = at,
            role = role,
            text = text,
            ja = ja,
            zh = zh,
            photo = photo == true,
            reachOut = reachOut == true,
        )
    }
}

fun LatestConversationDto.toDomain(): LatestConversation {
    val created = Time.parseInstant(createdAt) ?: Instant.EPOCH
    return LatestConversation(
        characterId = characterId,
        conversationId = conversationId,
        createdAt = created,
        lastMessage = lastMessage?.toPreview(created),
        unseenReachOut = unseenReachOut,
    )
}

fun ConversationDto.toDomain(): Conversation {
    val created = Time.parseInstant(createdAt) ?: Instant.EPOCH
    return Conversation(
        id = id,
        characterId = characterId,
        createdAt = created,
        title = title,
        lastMessage = lastMessage?.toPreview(created),
        ended = ended,
    )
}

fun AccountDto.toDomain(): Account = Account(
    id = id,
    name = name,
    signInMethods = signInMethods.map { SignInMethod(it.provider, it.displayName, it.email) },
    isAdmin = isAdmin,
    exemptingKeys = exemptingKeys,
    reachOuts = reachOuts,
    sponsorVersion = sponsorVersion,
)

fun MemoryDto.toDomain(): Memory = Memory(
    id = id,
    text = text,
    sourceCharacterId = sourceCharacterId,
    sourceCharacterName = sourceCharacterName,
    createdAt = Time.parseInstant(createdAt) ?: Instant.EPOCH,
    superseded = superseded,
)

fun MessageDto.toRecorded(): Recorded {
    val at = Time.parseInstant(createdAt) ?: Instant.EPOCH
    if (role == "user") {
        return Recorded.UserMessage(id = id, text = text.orEmpty(), at = at)
    }
    val parts = segments.ifEmpty {
        listOf(SegmentDto(ja.orEmpty(), zh.orEmpty()))
    }.map { Segment(it.ja, it.zh) }
    return Recorded.CharacterMessage(
        id = id,
        ja = ja.orEmpty(),
        zh = zh.orEmpty(),
        segments = parts,
        at = at,
        affinity = affinity?.toDomain(),
        photo = photo?.toDomain(),
    )
}

fun EventDto.toRecorded(): Recorded.ConversationEvent? {
    val eventKind = EventKind.fromWire(kind) ?: return null
    return Recorded.ConversationEvent(
        id = id,
        kind = eventKind,
        at = Time.parseInstant(createdAt) ?: Instant.EPOCH,
        giftName = giftName,
        giftId = giftId,
        affinity = affinity?.toDomain(),
    )
}

fun ReachOutDto.toRecorded(): Recorded.ReachOut = Recorded.ReachOut(
    id = id,
    ja = ja,
    zh = zh,
    segments = segments.map { Segment(it.ja, it.zh) },
    at = Time.parseInstant(createdAt) ?: Instant.EPOCH,
)

@Serializable
data class StatsDto(
    @SerialName("days_known") val daysKnown: Int = 0,
    val visits: VisitsDto = VisitsDto(),
    @SerialName("all_hours") val allHours: List<Int> = emptyList(),
    val heatmap: HeatmapDto = HeatmapDto(),
    val affinity: List<TierGroupDto>? = null,
    val achievements: AchievementsDto? = null,
    val range: InRangeDto = InRangeDto(),
)

@Serializable
data class VisitsDto(val current: Int = 0, val longest: Int = 0)

@Serializable
data class HeatmapDto(val from: String = "", val counts: List<Int> = emptyList())

@Serializable
data class TierGroupDto(val tier: String = "", val characters: List<String> = emptyList())

@Serializable
data class AchievementsDto(val stages: Int = 0, val complete: Int = 0)

@Serializable
data class InRangeDto(
    val span: String = "all",
    val turns: Int = 0,
    @SerialName("by_hour") val byHour: List<Int> = emptyList(),
    val characters: List<CharacterInRangeDto> = emptyList(),
    val calls: CallsDto = CallsDto(),
    val gifts: GiftsDto? = null,
)

@Serializable
data class CharacterInRangeDto(
    @SerialName("character_id") val characterId: String,
    val turns: Int = 0,
    @SerialName("days_known") val daysKnown: Int = 0,
    @SerialName("call_seconds") val callSeconds: Double = 0.0,
)

@Serializable
data class CallsDto(val count: Int = 0, val seconds: Double = 0.0)

@Serializable
data class GiftsDto(
    val given: Int = 0,
    val liked: Int = 0,
    val most: GiftMostDto? = null,
)

@Serializable
data class GiftMostDto(
    @SerialName("character_id") val characterId: String,
    val given: Int = 0,
)

fun StatsDto.toDomain(): Stats = Stats(
    daysKnown = daysKnown,
    visits = StatsVisits(visits.current, visits.longest),
    allHours = allHours,
    heatmap = StatsHeatmap(heatmap.from, heatmap.counts),
    affinity = affinity?.map { StatsTier(it.tier, it.characters) },
    achievements = achievements?.let { StatsAchievements(it.stages, it.complete) },
    range = StatsRange(
        span = range.span,
        turns = range.turns,
        byHour = range.byHour,
        characters = range.characters.map {
            CharacterStats(it.characterId, it.turns, it.daysKnown, it.callSeconds)
        },
        calls = StatsCalls(range.calls.count, range.calls.seconds),
        gifts = range.gifts?.let {
            StatsGifts(it.given, it.liked, it.most?.let { m -> StatsGiftMost(m.characterId, m.given) })
        },
    ),
)

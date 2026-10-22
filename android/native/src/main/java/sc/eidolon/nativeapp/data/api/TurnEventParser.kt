package sc.eidolon.nativeapp.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.core.Time
import sc.eidolon.nativeapp.model.Lang
import sc.eidolon.nativeapp.model.Recorded
import sc.eidolon.nativeapp.model.TurnEvent

object TurnEventParser {
    fun parse(payload: String, json: Json = EidolonJson): TurnEvent? {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        return when (root.string("type")) {
            "user" -> TurnEvent.User(root.string("text").orEmpty())
            "photo" -> root.string("id")?.let { TurnEvent.PhotoStart(it) }
            "delta" -> {
                val segment = root["segment"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
                val lang = when (root.string("lang")) {
                    "ja" -> Lang.Ja
                    "zh" -> Lang.Zh
                    else -> return null
                }
                TurnEvent.Delta(segment, lang, root.string("text").orEmpty())
            }
            "photos-used-up" -> root.string("retry_at")?.let(Time::parseInstant)
                ?.let { TurnEvent.PhotosUsedUp(it) }
            "done" -> {
                val message = root["message"]?.let {
                    runCatching { json.decodeFromJsonElement<MessageDto>(it) }.getOrNull()
                }?.toRecorded()
                if (message !is Recorded.CharacterMessage) return null
                val ended = root["ended"]?.let {
                    runCatching { json.decodeFromJsonElement<EventDto>(it) }.getOrNull()
                }?.toRecorded()
                val affinity = root["affinity"]?.let {
                    runCatching { json.decodeFromJsonElement<AffinityStatusDto>(it) }.getOrNull()
                }?.toDomain()
                val rewarded = root["rewarded"]?.let {
                    runCatching { json.decodeFromJsonElement<RewardedDto>(it) }.getOrNull()
                }?.toDomain()
                TurnEvent.Done(
                    message = message,
                    ended = ended,
                    affinity = affinity,
                    rewarded = rewarded,
                )
            }
            "error" -> TurnEvent.Error(root.string("message").orEmpty().ifBlank { "角色暂时无法回复，请稍后再试。" })
            else -> null
        }
    }

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull
}

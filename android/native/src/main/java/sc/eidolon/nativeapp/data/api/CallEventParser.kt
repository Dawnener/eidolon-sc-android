package sc.eidolon.nativeapp.data.api

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.core.Time
import java.time.Instant

/** What the backend sends over the call data channel. */
sealed interface CallEvent {
    data class User(val text: String, val reply: Int? = null) : CallEvent

    data class Delta(val segment: Int, val lang: String, val text: String) : CallEvent

    data class Emotion(val emotion: String, val nod: String?) : CallEvent

    data class Ended(
        val reason: String,
        val message: String,
        val cooldownUntil: Instant? = null,
    ) : CallEvent

    data class Error(val message: String) : CallEvent
}

object CallEventParser {
    fun parse(payload: String): CallEvent? {
        val root = runCatching { EidolonJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        return when (root.string("type")) {
            "user" -> CallEvent.User(
                text = root.string("text").orEmpty(),
                reply = root.int("reply"),
            )
            "delta" -> {
                val segment = root.int("segment") ?: return null
                CallEvent.Delta(
                    segment = segment,
                    lang = root.string("lang").orEmpty(),
                    text = root.string("text").orEmpty(),
                )
            }
            "emotion" -> CallEvent.Emotion(
                emotion = root.string("emotion").orEmpty().ifBlank { "calm" },
                nod = root.string("nod"),
            )
            "ended" -> CallEvent.Ended(
                reason = root.string("reason").orEmpty(),
                message = root.string("message").orEmpty(),
                cooldownUntil = root.string("cooldown_until")?.let(Time::parseInstant),
            )
            "error" -> CallEvent.Error(
                root.string("message").orEmpty().ifBlank { "角色暂时无法回复，请稍后再试。" },
            )
            else -> null
        }
    }

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun kotlinx.serialization.json.JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
}

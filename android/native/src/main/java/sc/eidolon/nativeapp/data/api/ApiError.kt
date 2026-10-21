package sc.eidolon.nativeapp.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.core.Time
import java.time.Instant

class ApiError(
    val status: Int,
    override val message: String,
    val retryAt: Instant? = null,
    val cooldownUntil: Instant? = null,
    val retryAfterSeconds: Long? = null,
) : Exception(message) {
    val isUnauthorized: Boolean get() = status == 401
    val isConsentRequired: Boolean get() = status == 428
    val isBanned: Boolean get() = status == 403 && message.contains("封禁")
    val isOriginRejected: Boolean get() = status == 403 && message.contains("其他网站")

    companion object {
        fun from(status: Int, body: String?, retryAfter: String? = null): ApiError {
            val root = runCatching { EidolonJson.parseToJsonElement(body.orEmpty()).jsonObject }.getOrNull()
            val detail = root?.get("detail")
            val text = when (detail) {
                is JsonPrimitive -> detail.contentOrNull
                is JsonArray -> detail.firstOrNull()?.let { item ->
                    (item as? JsonObject)?.get("msg")?.jsonPrimitive?.contentOrNull
                }
                else -> null
            }?.removePrefix("Value error, ")?.takeIf { it.isNotBlank() }
                ?: "请求失败（$status）"
            return ApiError(
                status = status,
                message = text,
                retryAt = (root?.get("retry_at") as? JsonPrimitive)?.contentOrNull?.let(Time::parseInstant),
                cooldownUntil = (root?.get("cooldown_until") as? JsonPrimitive)?.contentOrNull
                    ?.let(Time::parseInstant),
                retryAfterSeconds = retryAfter?.toLongOrNull()?.takeIf { it >= 0 },
            )
        }
    }
}

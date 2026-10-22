package sc.eidolon.nativeapp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import sc.eidolon.nativeapp.core.EidolonJson
import java.net.URI

@Serializable
data class OwnLlmRequest(
    @SerialName("base_url") val baseUrl: String,
    @SerialName("api_key") val apiKey: String,
    val model: String,
    val protocol: String = "chat-completions",
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    @SerialName("call_reasoning_effort") val callReasoningEffort: String? = null,
    @SerialName("extra_body") val extraBody: JsonObject? = null,
)

@Serializable
data class OwnMemoryLlmRequest(
    @SerialName("base_url") val baseUrl: String,
    @SerialName("api_key") val apiKey: String,
    val model: String,
    val protocol: String = "chat-completions",
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    @SerialName("extra_body") val extraBody: JsonObject? = null,
)

@Serializable
data class OwnTtsRequest(
    @SerialName("api_key") val apiKey: String,
    val model: String? = null,
)

@Serializable
data class OwnSttRequest(
    @SerialName("api_key") val apiKey: String,
    val provider: String = "soniox",
    val region: String? = null,
)

@Serializable
data class OwnImageRequest(
    @SerialName("api_format") val apiFormat: String = "novelai",
    @SerialName("base_url") val baseUrl: String = "https://image.novelai.net",
    @SerialName("api_key") val apiKey: String,
    val model: String = "nai-diffusion-5-curated",
)

@Serializable
data class OwnKeysRequest(
    val llm: OwnLlmRequest? = null,
    @SerialName("memory_llm") val memoryLlm: OwnMemoryLlmRequest? = null,
    val tts: OwnTtsRequest? = null,
    val stt: OwnSttRequest? = null,
    val image: OwnImageRequest? = null,
)

@Serializable
data class LlmModelsRequest(
    val protocol: String = "chat-completions",
    @SerialName("base_url") val baseUrl: String,
    @SerialName("api_key") val apiKey: String,
)

@Serializable
data class KeyCheckResponse(
    @SerialName("base_url") val baseUrl: String? = null,
    val model: String? = null,
)

@Serializable
data class SttCheckResponse(val region: String? = null)

@Serializable
data class LlmModelsResponse(val models: List<String> = emptyList())

object OwnKeysRules {
    val LLM_PROTOCOLS = setOf("chat-completions", "responses", "anthropic")
    val REASONING_EFFORTS = setOf("none", "minimal", "low", "medium", "high", "xhigh", "max")
    val TTS_MODELS = setOf("s2.1-pro-free", "s2.1-pro", "drama-3", "s2-pro", "s1")
    val STT_PROVIDERS = setOf("soniox", "bailian")
    val STT_REGIONS = setOf("beijing", "singapore")
    val IMAGE_FORMATS = setOf("novelai", "openai")
    val FORBIDDEN_EXTRA_BODY = setOf(
        "model", "stream", "stream_options", "messages", "input",
        "instructions", "system", "tools", "tool_choice",
    )

    fun last4(apiKey: String): String = apiKey.takeLast(4)

    fun masked(apiKey: String): String = "****${last4(apiKey)}"

    fun filledServices(keys: OwnKeysRequest): List<String> = buildList {
        if (keys.llm != null) add("LLM")
        if (keys.memoryLlm != null) add("后台模型")
        if (keys.tts != null) add("TTS")
        if (keys.stt != null) add("STT")
        if (keys.image != null) add("生图")
    }

    fun summary(keys: OwnKeysRequest?): String {
        val services = keys?.let(::filledServices).orEmpty()
        return if (services.isEmpty()) "全部用部署者的服务" else "已填 ${services.joinToString("、")}"
    }

    /** 文本回合只带 LLM、后台 LLM 和生图；TTS/STT 是通话用的。 */
    fun turnOnly(keys: OwnKeysRequest?): OwnKeysRequest? {
        if (keys == null) return null
        val llm = keys.llm
        val memory = keys.memoryLlm?.takeIf { llm != null }
        if (llm == null && keys.image == null) return null
        return OwnKeysRequest(llm = llm, memoryLlm = memory, image = keys.image)
    }

    /** 通话只用 LLM、TTS 和 STT；后台模型和生图不参与。 */
    fun callOnly(keys: OwnKeysRequest?): OwnKeysRequest? {
        if (keys == null) return null
        val call = OwnKeysRequest(llm = keys.llm, tts = keys.tts, stt = keys.stt)
        return if (callServices(call).isEmpty()) null else call
    }

    fun callServices(keys: OwnKeysRequest?): List<String> = buildList {
        if (keys?.llm != null) add("llm")
        if (keys?.tts != null) add("tts")
        if (keys?.stt != null) add("stt")
    }

    fun deleteLlm(keys: OwnKeysRequest): OwnKeysRequest = keys.copy(llm = null, memoryLlm = null)

    fun encode(keys: OwnKeysRequest): String = EidolonJson.encodeToString(OwnKeysRequest.serializer(), keys)

    fun decode(value: String?): OwnKeysRequest? {
        if (value.isNullOrBlank()) return null
        return runCatching { EidolonJson.decodeFromString(OwnKeysRequest.serializer(), value) }.getOrNull()
    }

    fun validateBaseUrl(value: String): String? {
        val text = value.trim()
        if (text.isEmpty() || text.length > 500) return "接口地址长度不对"
        return runCatching {
            val uri = URI(text)
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                "接口地址要像 https://api.example.com/v1 这样"
            } else null
        }.getOrElse { "接口地址要像 https://api.example.com/v1 这样" }
    }

    fun validateApiKey(value: String): String? {
        if (value.length !in 8..500) return "密钥长度不对"
        if (value.any { it.code !in 0x21..0x7e }) return "密钥只能包含可见 ASCII 字符"
        return null
    }

    fun validateModel(value: String): String? =
        if (value.isBlank() || value.length > 200) "模型名不能为空，最多 200 字" else null

    fun validateEffort(protocol: String, effort: String?): String? {
        if (effort == null) return null
        if (effort !in REASONING_EFFORTS) return "思考强度不对"
        if (protocol == "anthropic" && effort == "minimal") return "Anthropic 接口没有 minimal"
        return null
    }

    fun validateLlm(request: OwnLlmRequest): String? {
        validateBaseUrl(request.baseUrl)?.let { return it }
        validateApiKey(request.apiKey)?.let { return it }
        validateModel(request.model)?.let { return it }
        if (request.protocol !in LLM_PROTOCOLS) return "接口类型不对"
        validateEffort(request.protocol, request.reasoningEffort)?.let { return it }
        validateEffort(request.protocol, request.callReasoningEffort)?.let { return it }
        request.extraBody?.let { body ->
            val forbidden = body.keys.firstOrNull { it in FORBIDDEN_EXTRA_BODY }
            if (forbidden != null) return "额外参数不能包含 $forbidden"
        }
        return null
    }

    fun validateMemoryLlm(request: OwnMemoryLlmRequest): String? {
        validateBaseUrl(request.baseUrl)?.let { return it }
        validateApiKey(request.apiKey)?.let { return it }
        validateModel(request.model)?.let { return it }
        if (request.protocol !in LLM_PROTOCOLS) return "接口类型不对"
        validateEffort(request.protocol, request.reasoningEffort)?.let { return it }
        request.extraBody?.let { body ->
            val forbidden = body.keys.firstOrNull { it in FORBIDDEN_EXTRA_BODY }
            if (forbidden != null) return "额外参数不能包含 $forbidden"
        }
        return null
    }

    fun validateTts(request: OwnTtsRequest): String? {
        validateApiKey(request.apiKey)?.let { return it }
        if (request.model != null && request.model !in TTS_MODELS) return "语音合成模型不对"
        return null
    }

    fun validateStt(request: OwnSttRequest): String? {
        validateApiKey(request.apiKey)?.let { return it }
        if (request.provider !in STT_PROVIDERS) return "语音识别服务不对"
        if (request.region != null && request.region !in STT_REGIONS) return "语音识别地域不对"
        return null
    }

    fun validateImage(request: OwnImageRequest): String? {
        if (request.apiFormat !in IMAGE_FORMATS) return "生图接口格式不对"
        validateBaseUrl(request.baseUrl)?.let { return it }
        validateApiKey(request.apiKey)?.let { return it }
        validateModel(request.model)?.let { return it }
        return null
    }

    fun validate(keys: OwnKeysRequest): String? {
        keys.llm?.let { validateLlm(it)?.let { message -> return message } }
        keys.memoryLlm?.let {
            if (keys.llm == null) return "后台模型要和对话 LLM 一起填写"
            validateMemoryLlm(it)?.let { message -> return message }
        }
        keys.tts?.let { validateTts(it)?.let { message -> return message } }
        keys.stt?.let { validateStt(it)?.let { message -> return message } }
        keys.image?.let { validateImage(it)?.let { message -> return message } }
        return null
    }
}

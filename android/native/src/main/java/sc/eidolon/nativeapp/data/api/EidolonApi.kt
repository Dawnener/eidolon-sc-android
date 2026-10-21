package sc.eidolon.nativeapp.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.model.Account
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.Conversation
import sc.eidolon.nativeapp.model.KeyCheckResponse
import sc.eidolon.nativeapp.model.LatestConversation
import sc.eidolon.nativeapp.model.LlmModelsRequest
import sc.eidolon.nativeapp.model.LlmModelsResponse
import sc.eidolon.nativeapp.model.Memory
import sc.eidolon.nativeapp.model.OwnImageRequest
import sc.eidolon.nativeapp.model.OwnKeysRequest
import sc.eidolon.nativeapp.model.OwnKeysRules
import sc.eidolon.nativeapp.model.OwnLlmRequest
import sc.eidolon.nativeapp.model.OwnMemoryLlmRequest
import sc.eidolon.nativeapp.model.OwnSttRequest
import sc.eidolon.nativeapp.model.OwnTtsRequest
import sc.eidolon.nativeapp.model.Recorded
import sc.eidolon.nativeapp.model.SttCheckResponse
import sc.eidolon.nativeapp.model.TurnEvent
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EidolonApi(
    private val http: HttpFactory,
    private val baseUrl: () -> String?,
) {
    private val json = EidolonJson

    suspend fun providers(): AuthProvidersDto = getJson("/api/auth/providers")

    suspend fun health(): HealthDto = getJson("/api/health")

    suspend fun devLogin(name: String) {
        postNoContent(
            "/api/auth/dev-login",
            json.encodeToString(DevLoginRequest.serializer(), DevLoginRequest(name)),
        )
    }

    suspend fun logout() {
        postNoContent("/api/auth/logout")
    }

    suspend fun account(): Account = getJson<AccountDto>("/api/account").toDomain()

    suspend fun consent() {
        postNoContent(
            "/api/account/consent",
            json.encodeToString(
                ConsentRequest.serializer(),
                ConsentRequest(agreeToTerms = true, atLeast13 = true),
            ),
        )
    }

    suspend fun deleteAccount() {
        postNoContent(
            "/api/account/delete",
            json.encodeToString(DeleteAccountRequest.serializer(), DeleteAccountRequest(true)),
        )
    }

    suspend fun characters(): List<Character> =
        getJson<List<CharacterDto>>("/api/characters").map { it.toDomain() }

    suspend fun latestConversations(): List<LatestConversation> =
        getJson<List<LatestConversationDto>>("/api/conversations/latest").map { it.toDomain() }

    suspend fun conversations(characterId: String): List<Conversation> =
        getJson<List<ConversationDto>>(
            "/api/conversations",
            mapOf("character_id" to characterId),
        ).map { it.toDomain() }

    suspend fun createConversation(characterId: String): Conversation =
        postJson<NewConversationRequest, ConversationDto>(
            "/api/conversations",
            NewConversationRequest(characterId),
        ).toDomain()

    suspend fun messages(conversationId: String): List<Recorded> {
        val raw = getRaw("/api/conversations/$conversationId/messages")
        return json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
            runCatching {
                val obj = element.jsonObject
                when (obj.string("type")) {
                    "reach_out" -> json.decodeFromJsonElement<ReachOutDto>(obj).toRecorded()
                    "event" -> json.decodeFromJsonElement<EventDto>(obj).toRecorded()
                    else -> json.decodeFromJsonElement<MessageDto>(obj).toRecorded()
                }
            }.getOrNull()
        }
    }

    /** 文本回合的 SSE；调用方取消协程会取消底层请求。 */
    fun turn(conversationId: String, text: String, ownKeys: OwnKeysRequest? = null): Flow<TurnEvent> = flow {
        val body = TurnRequestDto(text = text, ownKeys = OwnKeysRules.turnOnly(ownKeys))
        val request = Request.Builder()
            .url(url("/api/conversations/$conversationId/turns"))
            .header("Accept", "text/event-stream")
            .post(json.encodeToString(TurnRequestDto.serializer(), body).toRequestBody(JSON))
            .build()
        val call = http.sse.newCall(request)
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        val response = try {
            call.execute()
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw e
        }
        try {
            if (!response.isSuccessful) {
                throw ApiError.from(
                    response.code,
                    response.body?.string().orEmpty(),
                    response.header("Retry-After"),
                )
            }
            val source = response.body?.source() ?: throw ApiError.from(500, "空的回复流")
            val decoder = SseDecoder()
            val buffer = Buffer()
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer, 8192)
                if (read == -1L) break
                for (payload in decoder.feed(buffer.readByteArray())) {
                    val event = TurnEventParser.parse(payload, json) ?: continue
                    emit(event)
                    if (event is TurnEvent.Done || event is TurnEvent.Error) return@flow
                }
            }
            for (payload in decoder.finish()) {
                val event = TurnEventParser.parse(payload, json) ?: continue
                emit(event)
                if (event is TurnEvent.Done || event is TurnEvent.Error) return@flow
            }
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun memories(): List<Memory> =
        getJson<List<MemoryDto>>("/api/memories").map { it.toDomain() }

    suspend fun deleteMemory(id: Int) {
        deleteNoContent("/api/memories/$id")
    }

    suspend fun clearMemories() {
        deleteNoContent("/api/memories")
    }

    suspend fun checkLlm(request: OwnLlmRequest): KeyCheckResponse =
        postJson("/api/account/keys/llm/check", request)

    suspend fun checkMemoryLlm(request: OwnMemoryLlmRequest): KeyCheckResponse =
        postJson("/api/account/keys/memory-llm/check", request)

    suspend fun checkTts(request: OwnTtsRequest): JsonObject =
        postJson("/api/account/keys/tts/check", request)

    suspend fun checkStt(request: OwnSttRequest): SttCheckResponse =
        postJson("/api/account/keys/stt/check", request)

    suspend fun checkImage(request: OwnImageRequest): KeyCheckResponse =
        postJson("/api/account/keys/image/check", request)

    suspend fun listLlmModels(request: LlmModelsRequest): LlmModelsResponse =
        postJson("/api/account/keys/llm/models", request)

    suspend fun exemption(own: String): ExemptionDto =
        getJson("/api/account/keys/exemption", mapOf("own" to own))

    private fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val base = baseUrl() ?: error("没有设置服务器地址")
        val builder = base.toHttpUrl().newBuilder().encodedPath(path)
        query.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    private suspend fun execute(client: OkHttpClient, request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        android.util.Log.w(
                            "EidolonNative",
                            "request failed ${call.request().url.encodedPath}: " +
                                "${e::class.java.simpleName} ${e.message.orEmpty()}",
                        )
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response) else response.close()
                    }
                },
            )
        }

    private suspend fun getRaw(path: String, query: Map<String, String> = emptyMap()): String =
        // 响应体必须在 IO 线程读；enqueue 只负责把请求发出去，恢复后是在调用方的主线程上
        withContext(Dispatchers.IO) {
            val response = execute(http.api, Request.Builder().url(url(path, query)).get().build())
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw ApiError.from(it.code, body, it.header("Retry-After"))
                body
            }
        }

    private suspend inline fun <reified T> getJson(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): T = json.decodeFromString<T>(getRaw(path, query))

    private suspend inline fun <reified B, reified T> postJson(path: String, body: B): T {
        val text = post(path, json.encodeToString(body))
        return json.decodeFromString<T>(text)
    }

    private suspend fun post(path: String, body: String?): String = withContext(Dispatchers.IO) {
        val requestBody = (body ?: "").toRequestBody(JSON)
        val response = execute(http.api, Request.Builder().url(url(path)).post(requestBody).build())
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ApiError.from(it.code, text, it.header("Retry-After"))
            text
        }
    }

    private suspend fun postNoContent(path: String, body: String? = null) {
        post(path, body)
    }

    private suspend fun deleteNoContent(path: String) = withContext(Dispatchers.IO) {
        val response = execute(http.api, Request.Builder().url(url(path)).delete().build())
        response.use {
            if (!it.isSuccessful) {
                throw ApiError.from(
                    it.code,
                    it.body?.string().orEmpty(),
                    it.header("Retry-After"),
                )
            }
        }
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

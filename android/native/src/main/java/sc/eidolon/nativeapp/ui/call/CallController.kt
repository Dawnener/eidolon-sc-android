package sc.eidolon.nativeapp.ui.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pipecat.client.PipecatClient
import ai.pipecat.client.PipecatClientOptions
import ai.pipecat.client.PipecatEventCallbacks
import ai.pipecat.client.result.RTVIException
import ai.pipecat.client.small_webrtc_transport.IceConfig
import ai.pipecat.client.small_webrtc_transport.IceServer
import ai.pipecat.client.small_webrtc_transport.PipecatClientSmallWebRTC
import ai.pipecat.client.small_webrtc_transport.SmallWebRTCTransport
import ai.pipecat.client.small_webrtc_transport.SmallWebRTCTransportConnectParams
import ai.pipecat.client.types.APIRequest
import ai.pipecat.client.types.BotReadyData
import ai.pipecat.client.types.Transcript
import ai.pipecat.client.types.Value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.data.api.CallEvent
import sc.eidolon.nativeapp.data.api.CallEventParser
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.data.api.RequestHeaders
import sc.eidolon.nativeapp.data.local.AccessStore
import sc.eidolon.nativeapp.data.local.CookieStore
import sc.eidolon.nativeapp.data.local.OwnKeysStore
import sc.eidolon.nativeapp.data.local.PreferencesStore
import sc.eidolon.nativeapp.data.local.ServerState
import sc.eidolon.nativeapp.model.OwnKeysRequest
import sc.eidolon.nativeapp.model.OwnKeysRules

/** The call screen listens to these; all are delivered on the main thread. */
interface CallListener {
    fun onDialing()
    fun onConnected(maxSeconds: Int?, exempt: Boolean)
    fun onSpeaking(speaking: Boolean)
    fun onHearing(text: String)
    fun onEvent(event: CallEvent)
    fun onEnded(message: String?)
    fun onError(message: String)
}

/**
 * A Pipecat SmallWebRTC call against the existing `/api/ice-servers` and `/api/offer` endpoints.
 *
 * The SDK does the SDP/ICE work; this class supplies the native session cookie, Origin and the
 * call request data, carries the user's own LLM/TTS/STT keys when present, and maps the RTVI
 * callbacks/events to the call screen.
 */
class CallController(
    private val context: Context,
    private val api: EidolonApi,
    private val prefs: PreferencesStore,
    private val ownKeys: OwnKeysStore,
    private val cookies: CookieStore,
    private val serverState: ServerState,
    private val access: AccessStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var client: PipecatClientSmallWebRTC? = null
    private var transport: SmallWebRTCTransport? = null
    private var connectTimeout: Job? = null
    private var hungUp = false
    private var ready = false
    private var released = false
    private var closing = false

    fun start(conversationId: String, listener: CallListener) {
        scope.launch {
            listener.onDialing()
            try {
                val base = serverState.url?.trimEnd('/') ?: error("没有设置服务器地址")
                val userId = prefs.userId() ?: error("请先登录")
                val keys = ownKeys.load(userId)
                val callKeys = OwnKeysRules.callOnly(keys)
                val setup = api.callSetup(conversationId, OwnKeysRules.callServices(callKeys))
                if (released) return@launch
                val iceConfig = IceConfig(
                    setup.iceServers.map {
                        IceServer(urls = it.urls, username = it.username, credential = it.credential)
                    },
                )

                val transport = SmallWebRTCTransport(context, iceConfig)
                val callbacks = object : PipecatEventCallbacks() {
                    override fun onBotReady(data: BotReadyData) {
                        ready = true
                        connectTimeout?.cancel()
                        post { listener.onConnected(setup.maxCallSeconds, setup.callExempt) }
                    }

                    override fun onBotStartedSpeaking() {
                        post { listener.onSpeaking(true) }
                    }

                    override fun onBotStoppedSpeaking() {
                        post { listener.onSpeaking(false) }
                    }

                    override fun onUserTranscript(data: Transcript) {
                        post { listener.onHearing(if (data.final) "" else data.text) }
                    }

                    override fun onServerMessage(message: Value) {
                        val payload = runCatching {
                            EidolonJson.encodeToString(Value.serializer(), message)
                        }.getOrNull() ?: return
                        val event = CallEventParser.parse(payload) ?: return
                        post { listener.onEvent(event) }
                    }

                    override fun onBackendError(message: String) {
                        post { listener.onError(message.ifBlank { "通话服务出错了" }) }
                    }

                    override fun onDisconnected() {
                        if (!hungUp) post { listener.onEnded(null) }
                    }
                }
                val client = PipecatClient(
                    transport,
                    PipecatClientOptions(callbacks = callbacks, enableMic = true, enableCam = false),
                )
                this@CallController.client = client
                this@CallController.transport = transport

                val request = CallRequestData(
                    conversationId = conversationId,
                    muted = false,
                    quality = "standard",
                    ownKeys = callKeys,
                )
                val requestData = EidolonJson.decodeFromJsonElement<Value>(
                    EidolonJson.encodeToJsonElement(CallRequestData.serializer(), request),
                )
                val headers = requestHeaders(base)
                takeAudioFocus()
                val params = SmallWebRTCTransportConnectParams(
                    webrtcRequestParams = APIRequest(
                        endpoint = "$base/api/offer",
                        requestData = requestData,
                        headers = headers,
                        timeoutMs = 60_000,
                    ),
                    iceConfig = iceConfig,
                )
                client.connect(params).await()
                connectTimeout = scope.launch {
                    delay(30_000)
                    if (!ready) {
                        post { listener.onError("接通超时，请检查网络后重试") }
                        disconnectQuietly()
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("EidolonCall", "call start failed", error)
                val raw = when (error) {
                    is RTVIException -> error.error.exception?.message ?: error.message
                    else -> error.message
                }
                val message = if (raw?.contains("Failed to connect to LLM") == true) {
                    "无法建立语音连接，请检查网络或 TURN 配置"
                } else {
                    raw ?: "无法接通，请稍后再试"
                }
                listener.onError(message)
                disconnectQuietly()
            }
        }
    }

    fun setMuted(muted: Boolean) {
        client?.enableMic(!muted)
    }

    fun setSpeaker(on: Boolean) {
        client?.updateMic(
            if (on) SmallWebRTCTransport.AudioDevices.Speakerphone.id
            else SmallWebRTCTransport.AudioDevices.Earpiece.id,
        )
    }

    suspend fun hangUp() {
        if (closing) return
        closing = true
        hungUp = true
        connectTimeout?.cancel()
        connectTimeout = null
        val current = client
        if (current != null) {
            runCatching { current.sendClientMessage("hang-up", Value.Null) }
            delay(150)
            runCatching { current.disconnect().await() }
        }
        release()
    }

    /** The screen left without the hang-up button: still tell the backend, then release. */
    fun close() {
        scope.launch { hangUp() }
    }

    fun release() {
        if (released) return
        released = true
        dropAudioFocus()
        connectTimeout?.cancel()
        connectTimeout = null
        runCatching { client?.release() }
        runCatching { transport?.release() }
        client = null
        transport = null
    }

    private fun disconnectQuietly() {
        scope.launch {
            runCatching { client?.disconnect()?.await() }
            release()
        }
    }

    private fun post(block: () -> Unit) {
        main.post {
            if (!released) block()
        }
    }

    private fun takeAudioFocus() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun dropAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
    }

    private fun requestHeaders(base: String): Map<String, String> {
        val url = base.toHttpUrl()
        val cookie = cookies.loadForRequest(url)
            .joinToString("; ") { "${it.name}=${it.value}" }
        return buildMap {
            if (cookie.isNotEmpty()) put("Cookie", cookie)
            put("Origin", RequestHeaders.originFor(base))
            put("X-Timezone", RequestHeaders.timezone())
            put("Accept", "application/json")
            RequestHeaders.accessHeaders(base, url, access.current())?.let {
                put("CF-Access-Client-Id", it.first)
                put("CF-Access-Client-Secret", it.second)
            }
            RequestHeaders.basicAuthHeader(base, url, access.currentBasic())?.let {
                put("Authorization", it)
            }
        }
    }

    @Serializable
    private data class CallRequestData(
        @SerialName("conversation_id") val conversationId: String,
        val muted: Boolean,
        val quality: String,
        @SerialName("own_keys") val ownKeys: OwnKeysRequest? = null,
    )
}

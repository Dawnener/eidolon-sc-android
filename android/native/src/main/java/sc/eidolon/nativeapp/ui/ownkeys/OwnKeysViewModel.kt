package sc.eidolon.nativeapp.ui.ownkeys

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.data.api.ExemptionDto
import sc.eidolon.nativeapp.data.local.OwnKeysStore
import sc.eidolon.nativeapp.model.OwnKeysRequest
import sc.eidolon.nativeapp.model.OwnKeysRules
import sc.eidolon.nativeapp.model.OwnLlmRequest
import sc.eidolon.nativeapp.model.OwnMemoryLlmRequest
import sc.eidolon.nativeapp.model.OwnSttRequest
import sc.eidolon.nativeapp.model.OwnTtsRequest

enum class OwnKeySection { Llm, MemoryLlm, Tts, Stt }

data class OwnKeysState(
    val loading: Boolean = true,
    val keys: OwnKeysRequest = OwnKeysRequest(),
    val editing: OwnKeySection? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val exemption: ExemptionDto? = null,
)

class OwnKeysViewModel(
    private val accountId: String,
    private val store: OwnKeysStore,
    private val api: EidolonApi,
) : ViewModel() {
    var state by mutableStateOf(OwnKeysState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            val keys = runCatching { store.load(accountId) }.getOrNull() ?: OwnKeysRequest()
            state = state.copy(loading = false, keys = keys)
            refreshExemption()
        }
    }

    fun edit(section: OwnKeySection) {
        if (state.busy) return
        state = state.copy(editing = section, error = null, notice = null)
    }

    fun cancelEdit() {
        if (state.busy) return
        state = state.copy(editing = null, error = null)
    }

    fun saveLlm(request: OwnLlmRequest) = launchSave {
        val resolved = request.copy(
            apiKey = request.apiKey.ifBlank { state.keys.llm?.apiKey.orEmpty() },
        )
        OwnKeysRules.validateLlm(resolved)?.let { error(it) }
        val checked = api.checkLlm(resolved)
        val normalized = resolved.copy(
            baseUrl = checked.baseUrl ?: resolved.baseUrl,
            model = checked.model ?: resolved.model,
        )
        persist(state.keys.copy(llm = normalized))
        "已保存对话 LLM"
    }

    fun saveMemoryLlm(request: OwnMemoryLlmRequest) = launchSave {
        if (state.keys.llm == null) error("先填写对话 LLM")
        val resolved = request.copy(
            apiKey = request.apiKey.ifBlank { state.keys.memoryLlm?.apiKey.orEmpty() },
        )
        OwnKeysRules.validateMemoryLlm(resolved)?.let { error(it) }
        val checked = api.checkMemoryLlm(resolved)
        val normalized = resolved.copy(
            baseUrl = checked.baseUrl ?: resolved.baseUrl,
            model = checked.model ?: resolved.model,
        )
        persist(state.keys.copy(memoryLlm = normalized))
        "已保存后台模型"
    }

    fun saveTts(request: OwnTtsRequest) = launchSave {
        val resolved = request.copy(
            apiKey = request.apiKey.ifBlank { state.keys.tts?.apiKey.orEmpty() },
        )
        OwnKeysRules.validateTts(resolved)?.let { error(it) }
        api.checkTts(resolved)
        persist(state.keys.copy(tts = resolved))
        "已保存语音合成"
    }

    fun saveStt(request: OwnSttRequest) = launchSave {
        val resolved = request.copy(
            apiKey = request.apiKey.ifBlank { state.keys.stt?.apiKey.orEmpty() },
        )
        OwnKeysRules.validateStt(resolved)?.let { error(it) }
        val checked = api.checkStt(resolved)
        persist(state.keys.copy(stt = resolved.copy(region = checked.region ?: resolved.region)))
        "已保存语音识别"
    }

    fun delete(section: OwnKeySection) {
        if (state.busy) return
        viewModelScope.launch {
            state = state.copy(busy = true, error = null, notice = null)
            try {
                val keys = when (section) {
                    OwnKeySection.Llm -> OwnKeysRules.deleteLlm(state.keys)
                    OwnKeySection.MemoryLlm -> state.keys.copy(memoryLlm = null)
                    OwnKeySection.Tts -> state.keys.copy(tts = null)
                    OwnKeySection.Stt -> state.keys.copy(stt = null)
                }
                persist(keys)
                state = state.copy(notice = "已删除")
            } catch (error: Exception) {
                state = state.copy(error = error.message ?: "删除失败")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    private fun launchSave(block: suspend () -> String) {
        if (state.busy) return
        viewModelScope.launch {
            state = state.copy(busy = true, error = null, notice = null)
            try {
                val notice = block()
                state = state.copy(editing = null, notice = notice)
            } catch (error: Exception) {
                state = state.copy(error = error.message ?: "保存失败")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    private suspend fun persist(keys: OwnKeysRequest) {
        if (accountId.isBlank()) error("还没有登录")
        store.save(accountId, keys)
        state = state.copy(keys = keys)
        refreshExemption()
    }

    private fun refreshExemption() {
        val keys = state.keys
        val services = buildList {
            if (keys.llm != null) add("llm")
            if (keys.tts != null) add("tts")
            if (keys.stt != null) add("stt")
        }
        if (services.isEmpty()) {
            state = state.copy(exemption = null)
            return
        }
        viewModelScope.launch {
            runCatching { api.exemption(services.joinToString(",")) }
                .onSuccess { state = state.copy(exemption = it) }
        }
    }

    private fun error(message: String): Nothing = throw IllegalArgumentException(message)
}

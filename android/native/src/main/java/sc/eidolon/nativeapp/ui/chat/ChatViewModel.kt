package sc.eidolon.nativeapp.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.data.api.ApiError
import sc.eidolon.nativeapp.data.local.OwnKeysStore
import sc.eidolon.nativeapp.data.repo.CharacterRepository
import sc.eidolon.nativeapp.data.repo.ConversationRepository
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.ConversationLog
import sc.eidolon.nativeapp.model.Failure
import sc.eidolon.nativeapp.model.LogAction
import sc.eidolon.nativeapp.model.TurnEvent
import java.time.Instant

data class ChatState(
    val loading: Boolean = true,
    val character: Character? = null,
    val conversationId: String? = null,
    val log: ConversationLog = ConversationLog(),
    val busy: Boolean = false,
    val draft: String = "",
    val failure: Failure? = null,
    val ended: Boolean = false,
    val coolingUntil: Instant? = null,
    val openError: String? = null,
)

class ChatViewModel(
    private val characterId: String,
    private val charactersRepo: CharacterRepository,
    private val conversationsRepo: ConversationRepository,
    private val ownKeysStore: OwnKeysStore,
    private val accountId: () -> String?,
) : ViewModel() {
    var state by mutableStateOf(ChatState())
        private set

    private var turnJob: Job? = null

    init {
        // 列表刚拉过角色，先用缓存把头像/名字显示出来，共享元素才有目标
        state = state.copy(character = charactersRepo.cached(characterId))
        open()
    }

    fun retryOpen() {
        state = state.copy(loading = true, openError = null)
        open()
    }

    private fun open() {
        viewModelScope.launch {
            try {
                val character = charactersRepo.all().firstOrNull { it.id == characterId }
                    ?: error("角色不存在")
                val conversations = conversationsRepo.forCharacter(characterId)
                val conversation = conversations.firstOrNull() ?: conversationsRepo.create(characterId)
                val messages = conversationsRepo.messages(conversation.id)
                state = state.copy(
                    loading = false,
                    character = character,
                    conversationId = conversation.id,
                    log = ConversationLog().reduce(LogAction.Loaded(messages)),
                    ended = conversation.ended,
                    openError = null,
                )
            } catch (error: Exception) {
                state = state.copy(
                    loading = false,
                    openError = error.message ?: "对话没能打开",
                )
            }
        }
    }

    fun onDraftChange(value: String) {
        state = state.copy(draft = value.takeCodePoints(MAX_CHARS))
    }

    fun send() {
        if (state.busy || state.ended) return
        if (state.coolingUntil?.isAfter(Instant.now()) == true) return
        val text = state.draft.trim()
        if (text.isEmpty()) return
        if (text.codePointCount(0, text.length) > MAX_CHARS) {
            state = state.copy(failure = Failure("这句话太长了，最多 $MAX_CHARS 字。"))
            return
        }
        val conversationId = state.conversationId ?: return
        val sentAt = Instant.now()
        apply(LogAction.Submitted(text, sentAt))
        state = state.copy(busy = true, draft = "", failure = null)
        turnJob = viewModelScope.launch {
            var terminal = false
            try {
                val keys = accountId()?.let { id -> runCatching { ownKeysStore.load(id) }.getOrNull() }
                conversationsRepo.turn(conversationId, text, keys).collect { event ->
                    when (event) {
                        is TurnEvent.User -> apply(LogAction.UserSaid(event.text, sentAt))
                        is TurnEvent.PhotoStart -> apply(LogAction.Photo(event.id))
                        is TurnEvent.Delta -> apply(
                            LogAction.Delta(event.segment, event.lang, event.text),
                        )
                        is TurnEvent.PhotosUsedUp -> apply(LogAction.PhotosUsedUp(event.retryAt))
                        is TurnEvent.Done -> {
                            apply(LogAction.Done(event.message, event.ended))
                            terminal = true
                        }
                        is TurnEvent.Error -> {
                            apply(LogAction.Interrupted)
                            state = state.copy(failure = Failure(event.message), draft = text)
                            terminal = true
                        }
                    }
                }
                if (!terminal) {
                    apply(LogAction.Interrupted)
                    state = state.copy(
                        failure = Failure("回复中断了，请再试一次"),
                        draft = text,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiError) {
                apply(LogAction.Interrupted)
                val failure = when {
                    error.cooldownUntil != null -> {
                        state = state.copy(coolingUntil = error.cooldownUntil)
                        Failure(error.message, until = error.cooldownUntil)
                    }
                    error.status == 409 && error.message.contains("结束") -> {
                        state = state.copy(ended = true)
                        Failure(error.message)
                    }
                    else -> Failure(
                        error.message,
                        toOwnKeys = error.status == 429 && error.retryAt == null,
                        until = error.retryAt,
                    )
                }
                state = state.copy(failure = failure, draft = text)
            } catch (error: Exception) {
                apply(LogAction.Interrupted)
                state = state.copy(
                    failure = Failure("回复中断了，请再试一次"),
                    draft = text,
                )
            } finally {
                state = state.copy(busy = false, ended = state.ended || state.log.isEnded)
            }
        }
    }

    fun newConversation() {
        if (state.busy) return
        viewModelScope.launch {
            try {
                val conversation = conversationsRepo.create(characterId)
                state = state.copy(
                    conversationId = conversation.id,
                    log = ConversationLog(),
                    ended = false,
                    coolingUntil = null,
                    draft = "",
                    failure = null,
                )
            } catch (error: Exception) {
                state = state.copy(failure = Failure(error.message ?: "新对话没能开始"))
            }
        }
    }

    fun dismissFailure() {
        state = state.copy(failure = null)
    }

    private fun apply(action: LogAction) {
        val log = state.log.reduce(action)
        state = state.copy(log = log, ended = log.isEnded)
    }

    override fun onCleared() {
        turnJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val MAX_CHARS = 2000
    }
}

private fun String.takeCodePoints(max: Int): String {
    if (codePointCount(0, length) <= max) return this
    return substring(0, offsetByCodePoints(0, max))
}

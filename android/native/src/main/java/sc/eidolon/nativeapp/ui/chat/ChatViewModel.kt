package sc.eidolon.nativeapp.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.data.api.ApiError
import sc.eidolon.nativeapp.data.local.OwnKeysStore
import sc.eidolon.nativeapp.data.repo.CharacterRepository
import sc.eidolon.nativeapp.data.repo.ConversationRepository
import sc.eidolon.nativeapp.data.repo.GiftsRepository
import sc.eidolon.nativeapp.model.AffinityChange
import sc.eidolon.nativeapp.model.AffinityStatus
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.Conversation
import sc.eidolon.nativeapp.model.ConversationLog
import sc.eidolon.nativeapp.model.Failure
import sc.eidolon.nativeapp.model.HerAchievements
import sc.eidolon.nativeapp.model.LogAction
import sc.eidolon.nativeapp.model.TurnEvent
import sc.eidolon.nativeapp.model.rewardNoticeText
import java.time.Instant

data class AffinityCue(val change: AffinityChange, val id: Long = System.nanoTime())

data class PastConversation(
    val conversation: Conversation,
    val log: ConversationLog,
    val ended: Boolean,
)

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
    val affinity: AffinityStatus? = null,
    val affinityCue: AffinityCue? = null,
    val rewardNotice: String? = null,
    val herAchievements: HerAchievements? = null,
    val achievementsLoading: Boolean = false,
    val achievementsError: String? = null,
    val openError: String? = null,
    val history: List<Conversation> = emptyList(),
    val historyLoading: Boolean = false,
    val viewing: PastConversation? = null,
)

class ChatViewModel(
    private val characterId: String,
    private val charactersRepo: CharacterRepository,
    private val conversationsRepo: ConversationRepository,
    private val giftsRepo: GiftsRepository,
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
        state = state.copy(loading = true, openError = null, viewing = null)
        open()
    }

    private fun open() {
        viewModelScope.launch {
            try {
                val character = charactersRepo.all().firstOrNull { it.id == characterId }
                    ?: error("角色不存在")
                val conversations = conversationsRepo.forCharacter(characterId)
                val conversation = conversations.firstOrNull() ?: conversationsRepo.create(characterId)
                val history = if (conversations.isEmpty()) listOf(conversation) else conversations
                // 和消息并行取好感度，让礼物/成就入口和聊天内容一起出现，而不是晚一拍。
                val affinityJob = async {
                    try {
                        giftsRepo.affinity(characterId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                }
                val messages = conversationsRepo.messages(conversation.id)
                val affinity = affinityJob.await()
                state = state.copy(
                    loading = false,
                    character = character,
                    conversationId = conversation.id,
                    log = ConversationLog().reduce(LogAction.Loaded(messages)),
                    ended = conversation.ended,
                    affinity = affinity,
                    coolingUntil = affinity?.cooldownUntil,
                    openError = null,
                    history = history,
                    viewing = null,
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
        if (state.busy || state.ended || state.viewing != null) return
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
                            val cue = event.message.affinity?.let { AffinityCue(it) }
                            state = state.copy(
                                affinity = event.affinity ?: state.affinity,
                                coolingUntil = if (event.affinity != null) {
                                    event.affinity.cooldownUntil
                                } else {
                                    state.coolingUntil
                                },
                                affinityCue = cue ?: state.affinityCue,
                                rewardNotice = event.rewarded?.let { rewardNoticeText(it) }
                                    ?: state.rewardNotice,
                            )
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
                    coolingUntil = state.affinity?.cooldownUntil,
                    draft = "",
                    failure = null,
                    viewing = null,
                    history = conversationsRepo.forCharacter(characterId),
                )
            } catch (error: Exception) {
                state = state.copy(failure = Failure(error.message ?: "新对话没能开始"))
            }
        }
    }

    /** 回到聊天页时刷新消息和好感度，让从礼物页送出的礼物事件出现。 */
    fun refreshOnResume() {
        if (state.loading || state.busy || state.viewing != null) return
        val conversationId = state.conversationId ?: return
        viewModelScope.launch {
            try {
                val messages = conversationsRepo.messages(conversationId)
                val affinity = runCatching { giftsRepo.affinity(characterId) }.getOrNull()
                val currentAffinity = affinity ?: state.affinity
                val log = ConversationLog().reduce(LogAction.Loaded(messages))
                state = state.copy(
                    log = log,
                    ended = log.isEnded,
                    affinity = currentAffinity,
                    coolingUntil = currentAffinity?.cooldownUntil,
                )
            } catch (_: Exception) {
                // 刷新失败不打断当前聊天。
            }
        }
    }

    fun loadHerAchievements() {
        if (state.achievementsLoading) return
        state = state.copy(achievementsLoading = true, achievementsError = null)
        viewModelScope.launch {
            try {
                val achievements = giftsRepo.achievements(characterId)
                state = state.copy(
                    achievementsLoading = false,
                    herAchievements = achievements,
                    achievementsError = if (achievements == null) "成就暂时没有开放" else null,
                )
            } catch (error: Exception) {
                state = state.copy(
                    achievementsLoading = false,
                    achievementsError = error.message ?: "成就没能读取",
                )
            }
        }
    }

    fun clearAffinityCue() {
        state = state.copy(affinityCue = null)
    }

    fun dismissRewardNotice() {
        state = state.copy(rewardNotice = null)
    }

    fun loadHistory() {
        if (state.historyLoading) return
        state = state.copy(historyLoading = true)
        viewModelScope.launch {
            try {
                state = state.copy(
                    history = conversationsRepo.forCharacter(characterId),
                    historyLoading = false,
                )
            } catch (error: Exception) {
                state = state.copy(
                    historyLoading = false,
                    failure = Failure(error.message ?: "历史对话没能读取"),
                )
            }
        }
    }

    /** 打开一段历史对话；她当前的那段就是回到当前，不再单独加载。 */
    fun viewPast(conversation: Conversation) {
        if (conversation.id == state.conversationId) {
            state = state.copy(viewing = null)
            return
        }
        viewModelScope.launch {
            try {
                val messages = conversationsRepo.messages(conversation.id)
                state = state.copy(
                    viewing = PastConversation(
                        conversation = conversation,
                        log = ConversationLog().reduce(LogAction.Loaded(messages)),
                        ended = conversation.ended,
                    ),
                )
            } catch (error: Exception) {
                state = state.copy(failure = Failure(error.message ?: "这段历史对话没能打开"))
            }
        }
    }

    fun backToCurrent() {
        state = state.copy(viewing = null)
    }

    fun deleteConversation(conversation: Conversation) {
        if (conversation.id == state.conversationId) return
        viewModelScope.launch {
            try {
                conversationsRepo.delete(conversation.id)
                if (state.viewing?.conversation?.id == conversation.id) {
                    state = state.copy(viewing = null)
                }
                state = state.copy(history = conversationsRepo.forCharacter(characterId))
            } catch (error: Exception) {
                state = state.copy(
                    failure = Failure(error.message ?: "历史对话没能删除"),
                )
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

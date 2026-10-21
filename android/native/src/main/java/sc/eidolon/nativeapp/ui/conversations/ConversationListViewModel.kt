package sc.eidolon.nativeapp.ui.conversations

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.data.repo.CharacterRepository
import sc.eidolon.nativeapp.data.repo.ConversationRepository
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.ConversationList
import sc.eidolon.nativeapp.model.ConversationRow
import sc.eidolon.nativeapp.model.LatestConversation

data class ConversationListState(
    val loading: Boolean = true,
    val characters: List<Character> = emptyList(),
    val latest: Map<String, LatestConversation> = emptyMap(),
    val rows: List<ConversationRow> = emptyList(),
    val query: String = "",
    val error: String? = null,
)

class ConversationListViewModel(
    private val charactersRepo: CharacterRepository,
    private val conversationsRepo: ConversationRepository,
) : ViewModel() {
    var state by mutableStateOf(ConversationListState())
        private set

    private var busy = false

    fun load(quiet: Boolean = false) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                coroutineScope {
                    val characters = async { charactersRepo.all() }
                    val latest = async { conversationsRepo.latest() }
                    val all = characters.await()
                    val latestMap = latest.await().associateBy { it.characterId }
                    state = state.copy(
                        loading = false,
                        characters = all,
                        latest = latestMap,
                        rows = ConversationList.arrange(all, latestMap, state.query),
                        error = null,
                    )
                }
            } catch (error: Exception) {
                state = state.copy(
                    loading = false,
                    error = error.message ?: "对话列表载入失败",
                )
            } finally {
                busy = false
            }
        }
    }

    fun setQuery(query: String) {
        state = state.copy(
            query = query,
            rows = ConversationList.arrange(state.characters, state.latest, query),
        )
    }
}

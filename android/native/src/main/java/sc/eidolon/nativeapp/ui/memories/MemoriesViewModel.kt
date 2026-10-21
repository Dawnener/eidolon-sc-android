package sc.eidolon.nativeapp.ui.memories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.data.api.ApiError
import sc.eidolon.nativeapp.data.repo.CharacterRepository
import sc.eidolon.nativeapp.data.repo.MemoryRepository
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.Memory

data class MemoriesState(
    val loading: Boolean = true,
    val memories: List<Memory> = emptyList(),
    val characters: Map<String, Character> = emptyMap(),
    val error: String? = null,
    val busyId: Int? = null,
    val clearing: Boolean = false,
)

class MemoriesViewModel(
    private val memoriesRepo: MemoryRepository,
    private val charactersRepo: CharacterRepository,
) : ViewModel() {
    var state by mutableStateOf(MemoriesState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            try {
                coroutineScope {
                    val memories = async { memoriesRepo.all() }
                    val characters = async { charactersRepo.all() }
                    state = state.copy(
                        loading = false,
                        memories = memories.await(),
                        characters = characters.await().associateBy { it.id },
                    )
                }
            } catch (error: Exception) {
                state = state.copy(loading = false, error = error.message ?: "记忆载入失败")
            }
        }
    }

    fun delete(id: Int) {
        if (state.busyId != null || state.clearing) return
        viewModelScope.launch {
            state = state.copy(busyId = id, error = null)
            try {
                memoriesRepo.delete(id)
                state = state.copy(busyId = null, memories = memoriesRepo.all())
            } catch (error: ApiError) {
                if (error.status == 404) {
                    state = state.copy(
                        busyId = null,
                        memories = state.memories.filterNot { it.id == id },
                    )
                } else {
                    state = state.copy(busyId = null, error = error.message)
                }
            } catch (error: Exception) {
                state = state.copy(busyId = null, error = error.message ?: "删除失败")
            }
        }
    }

    fun clear() {
        if (state.clearing || state.busyId != null) return
        viewModelScope.launch {
            state = state.copy(clearing = true, error = null)
            try {
                memoriesRepo.clear()
                state = state.copy(clearing = false, memories = emptyList())
            } catch (error: Exception) {
                state = state.copy(clearing = false, error = error.message ?: "清空失败")
            }
        }
    }
}

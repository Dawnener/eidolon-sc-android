package sc.eidolon.nativeapp.ui.gifts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.data.api.ApiError
import sc.eidolon.nativeapp.data.repo.CharacterRepository
import sc.eidolon.nativeapp.data.repo.GiftsRepository
import sc.eidolon.nativeapp.model.Character
import sc.eidolon.nativeapp.model.DrawOutcome
import sc.eidolon.nativeapp.model.GiftCategory
import sc.eidolon.nativeapp.model.GiftInfo
import sc.eidolon.nativeapp.model.GiftState
import sc.eidolon.nativeapp.model.GiftUpdates
import sc.eidolon.nativeapp.model.GivenGift
import sc.eidolon.nativeapp.model.ResetOutcome
import sc.eidolon.nativeapp.model.givenNote

data class GiftsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val catalog: List<GiftInfo> = emptyList(),
    val giftState: GiftState? = null,
    val characters: List<Character> = emptyList(),
    val pickedCharacterId: String? = null,
    val selectedGiftId: String? = null,
    val drawing: Boolean = false,
    val drawResult: DrawOutcome? = null,
    val drawError: String? = null,
    val giving: Boolean = false,
    val resetting: Boolean = false,
    val notice: String? = null,
    val justDoneTasks: Set<String> = emptySet(),
    val selectedCategory: GiftCategory? = null,
) {
    val closed: Boolean get() = giftState != null && !giftState.open
    val pickedCharacter: Character? get() = characters.firstOrNull { it.id == pickedCharacterId }
    val selectedGift: GiftInfo? get() = catalog.firstOrNull { it.id == selectedGiftId }
    val filteredGifts: List<GiftInfo>
        get() {
            val held = giftState?.backpack?.associateBy { it.giftId }.orEmpty()
            return catalog.filter { gift ->
                held.containsKey(gift.id) && (selectedCategory == null || gift.category == selectedCategory)
            }
        }
    fun countOf(giftId: String): Int =
        giftState?.backpack?.firstOrNull { it.giftId == giftId }?.count ?: 0

    fun giftCharacter(characterId: String) =
        giftState?.characters?.firstOrNull { it.id == characterId }

    fun characterName(id: String): String =
        characters.firstOrNull { it.id == id }?.name ?: id
}

class GiftsViewModel(
    private val giftsRepo: GiftsRepository,
    private val charactersRepo: CharacterRepository,
) : ViewModel() {
    var state by mutableStateOf(GiftsUiState())
        private set
    private var loadId = 0
    private var started = false

    fun start(characterId: String?) {
        if (started) return
        started = true
        state = state.copy(pickedCharacterId = characterId)
        load()
    }

    fun load() {
        val id = ++loadId
        state = state.copy(
            loading = state.giftState == null,
            refreshing = state.giftState != null,
            error = null,
        )
        viewModelScope.launch {
            try {
                val catalog = async { giftsRepo.catalog() }
                val gifts = async { giftsRepo.state() }
                val characters = async { runCatching { charactersRepo.all() }.getOrDefault(emptyList()) }
                val loadedCatalog = catalog.await()
                val loadedGifts = gifts.await()
                val loadedCharacters = characters.await()
                if (id != loadId) return@launch
                val selected = state.pickedCharacterId
                    ?.takeIf { picked -> loadedCharacters.any { it.id == picked } }
                state = state.copy(
                    loading = false,
                    refreshing = false,
                    error = null,
                    catalog = loadedCatalog,
                    giftState = loadedGifts,
                    characters = loadedCharacters,
                    pickedCharacterId = selected,
                )
            } catch (error: ApiError) {
                if (id != loadId) return@launch
                state = if (error.giftsClosed) {
                    state.copy(
                        loading = false,
                        refreshing = false,
                        error = null,
                        giftState = state.giftState?.copy(open = false),
                    )
                } else {
                    state.copy(
                        loading = false,
                        refreshing = false,
                        error = error.message,
                    )
                }
            } catch (error: Exception) {
                if (id != loadId) return@launch
                state = state.copy(
                    loading = false,
                    refreshing = false,
                    error = error.message ?: "礼物没能读取",
                )
            }
        }
    }

    fun refresh(quiet: Boolean = true) {
        if (state.loading || state.refreshing) return
        if (state.catalog.isEmpty()) {
            load()
            return
        }
        val id = ++loadId
        if (!quiet) state = state.copy(refreshing = true)
        viewModelScope.launch {
            try {
                val gifts = giftsRepo.state()
                val characters = runCatching { charactersRepo.all() }.getOrDefault(state.characters)
                if (id != loadId) return@launch
                state = state.copy(
                    giftState = gifts,
                    characters = characters,
                    error = null,
                    refreshing = false,
                )
            } catch (error: ApiError) {
                if (id != loadId) return@launch
                if (error.giftsClosed) {
                    state = state.copy(
                        giftState = state.giftState?.copy(open = false),
                        error = null,
                        refreshing = false,
                    )
                } else {
                    state = state.copy(error = error.message, refreshing = false)
                }
            } catch (error: Exception) {
                if (id != loadId) return@launch
                state = state.copy(error = error.message ?: "礼物没能读取", refreshing = false)
            }
        }
    }

    fun selectCategory(category: GiftCategory?) {
        state = state.copy(selectedCategory = category)
    }

    fun clearPicked() {
        state = state.copy(pickedCharacterId = null)
    }

    fun pickCharacter(characterId: String?) {
        state = state.copy(pickedCharacterId = characterId)
    }

    fun openGive(giftId: String) {
        state = state.copy(selectedGiftId = giftId, drawResult = null)
    }

    fun closeGive() {
        state = state.copy(selectedGiftId = null)
    }

    fun draw(all: Boolean) {
        if (state.drawing || state.giving || state.resetting) return
        if ((state.giftState?.draws ?: 0) <= 0) return
        state = state.copy(drawing = true, drawError = null, drawResult = null)
        viewModelScope.launch {
            try {
                val result = giftsRepo.draw(all)
                state = state.copy(
                    drawing = false,
                    drawResult = result,
                    giftState = state.giftState?.let { GiftUpdates.withDrawn(it, result) },
                    notice = result.rewarded?.let { sc.eidolon.nativeapp.model.rewardNoticeText(it) },
                )
                refresh()
            } catch (error: ApiError) {
                state = state.copy(
                    drawing = false,
                    drawError = error.message,
                    notice = error.message,
                    giftState = if (error.giftsClosed) {
                        state.giftState?.copy(open = false)
                    } else {
                        state.giftState
                    },
                )
            } catch (error: Exception) {
                state = state.copy(
                    drawing = false,
                    drawError = error.message ?: "抽奖失败",
                    notice = error.message ?: "抽奖失败",
                )
            }
        }
    }

    fun closeDrawResult() {
        state = state.copy(drawResult = null, drawError = null)
    }

    fun give(characterId: String) {
        val giftId = state.selectedGiftId ?: return
        if (state.giving) return
        state = state.copy(giving = true)
        viewModelScope.launch {
            try {
                val given = giftsRepo.give(characterId, giftId)
                val name = state.characterName(characterId)
                state = state.copy(
                    giving = false,
                    selectedGiftId = null,
                    giftState = state.giftState?.let {
                        GiftUpdates.afterGiving(it, characterId, giftId, given)
                    },
                    pickedCharacterId = if (state.pickedCharacterId == characterId) {
                        characterId
                    } else {
                        state.pickedCharacterId
                    },
                    justDoneTasks = state.justDoneTasks + given.rewarded?.dailyTasks.orEmpty(),
                    notice = givenNotice(name, given),
                )
                refresh()
            } catch (error: ApiError) {
                state = state.copy(
                    giving = false,
                    notice = error.message,
                    giftState = if (error.giftsClosed) {
                        state.giftState?.copy(open = false)
                    } else {
                        state.giftState
                    },
                )
            } catch (error: Exception) {
                state = state.copy(giving = false, notice = error.message ?: "礼物没能送出")
            }
        }
    }

    fun useResetCard(onDone: (ResetOutcome) -> Unit = {}) {
        if (state.resetting) return
        state = state.copy(resetting = true)
        viewModelScope.launch {
            try {
                val outcome = giftsRepo.reset()
                state = state.copy(
                    resetting = false,
                    notice = if (outcome.broughtBack.isEmpty()) {
                        "已使用一张重置卡"
                    } else {
                        "已使用一张重置卡：${outcome.broughtBack.joinToString("、") { state.characterName(it) }}"
                    },
                )
                onDone(outcome)
                refresh()
            } catch (error: ApiError) {
                state = state.copy(
                    resetting = false,
                    notice = error.message,
                    giftState = if (error.giftsClosed) {
                        state.giftState?.copy(open = false)
                    } else {
                        state.giftState
                    },
                )
            } catch (error: Exception) {
                state = state.copy(resetting = false, notice = error.message ?: "重置卡没能使用")
            }
        }
    }

    fun consumeNotice() {
        state = state.copy(notice = null)
    }

    fun clearJustDone(taskIds: Collection<String>) {
        state = state.copy(justDoneTasks = state.justDoneTasks - taskIds.toSet())
    }
}

private fun givenNotice(name: String, given: GivenGift): String =
    givenNote(name, given, java.time.Instant.now(), sc.eidolon.nativeapp.core.Time.zone())

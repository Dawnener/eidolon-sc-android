package sc.eidolon.nativeapp.data.repo

import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.model.Character

class CharacterRepository(private val api: EidolonApi) {
    @Volatile
    private var cache: List<Character> = emptyList()

    suspend fun all(): List<Character> = api.characters().also { cache = it }

    /** 列表已经拉过角色时，聊天页可以立刻拿到头像/颜色，共享元素才有目标。 */
    fun cached(id: String): Character? = cache.firstOrNull { it.id == id }
}

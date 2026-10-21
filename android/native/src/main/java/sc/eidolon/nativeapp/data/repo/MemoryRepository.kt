package sc.eidolon.nativeapp.data.repo

import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.model.Memory

class MemoryRepository(private val api: EidolonApi) {
    suspend fun all(): List<Memory> = api.memories()

    suspend fun delete(id: Int) = api.deleteMemory(id)

    suspend fun clear() = api.clearMemories()
}

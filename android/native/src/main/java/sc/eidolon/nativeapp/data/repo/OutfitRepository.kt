package sc.eidolon.nativeapp.data.repo

import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.model.OutfitCard
import sc.eidolon.nativeapp.model.Stand

/** Her outfits: the list lives beside the stand assets, not on the backend. */
class OutfitRepository(private val api: EidolonApi) {
    /** The list changes when new cards sync; fetch it each time a call screen opens. */
    suspend fun list(stand: Stand): List<OutfitCard> {
        val url = stand.outfitsUrl ?: return emptyList()
        return api.outfits(url)
    }
}

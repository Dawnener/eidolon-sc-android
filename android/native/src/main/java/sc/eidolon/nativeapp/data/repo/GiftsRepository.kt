package sc.eidolon.nativeapp.data.repo

import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.model.AffinityStatus
import sc.eidolon.nativeapp.model.DrawOutcome
import sc.eidolon.nativeapp.model.GiftInfo
import sc.eidolon.nativeapp.model.GiftState
import sc.eidolon.nativeapp.model.GivenGift
import sc.eidolon.nativeapp.model.HerAchievements
import sc.eidolon.nativeapp.model.ResetOutcome

class GiftsRepository(private val api: EidolonApi) {
    suspend fun catalog(): List<GiftInfo> = api.giftCatalog()

    suspend fun state(): GiftState = api.gifts()

    suspend fun draw(all: Boolean): DrawOutcome = api.drawGifts(all)

    suspend fun give(characterId: String, giftId: String): GivenGift =
        api.giveGift(characterId, giftId)

    suspend fun reset(): ResetOutcome = api.useResetCard()

    suspend fun affinity(characterId: String): AffinityStatus? = api.affinity(characterId)

    suspend fun achievements(characterId: String): HerAchievements? =
        api.characterAchievements(characterId)
}

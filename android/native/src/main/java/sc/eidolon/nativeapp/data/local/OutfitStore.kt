package sc.eidolon.nativeapp.data.local

/** Which outfit she wears on calls, per character and user, remembered across launches. */
class OutfitStore(
    private val prefs: PreferencesStore,
    private val serverState: ServerState,
) {
    suspend fun load(userId: String, characterId: String): String? {
        val namespace = serverState.keyNamespace ?: return null
        return prefs.outfit(namespace, userId, characterId)
    }

    suspend fun save(userId: String, characterId: String, outfitId: String) {
        val namespace = serverState.keyNamespace ?: return
        prefs.setOutfit(namespace, userId, characterId, outfitId)
    }
}

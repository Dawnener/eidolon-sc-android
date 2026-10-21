package sc.eidolon.nativeapp.data.repo

import kotlinx.coroutines.flow.Flow
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.model.Conversation
import sc.eidolon.nativeapp.model.LatestConversation
import sc.eidolon.nativeapp.model.OwnKeysRequest
import sc.eidolon.nativeapp.model.Recorded
import sc.eidolon.nativeapp.model.TurnEvent

class ConversationRepository(private val api: EidolonApi) {
    suspend fun latest(): List<LatestConversation> = api.latestConversations()

    suspend fun forCharacter(characterId: String): List<Conversation> = api.conversations(characterId)

    suspend fun create(characterId: String): Conversation = api.createConversation(characterId)

    suspend fun delete(conversationId: String) = api.deleteConversation(conversationId)

    suspend fun messages(conversationId: String): List<Recorded> = api.messages(conversationId)

    fun turn(conversationId: String, text: String, ownKeys: OwnKeysRequest?): Flow<TurnEvent> =
        api.turn(conversationId, text, ownKeys)
}

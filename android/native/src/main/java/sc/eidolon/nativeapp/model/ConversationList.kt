package sc.eidolon.nativeapp.model

import java.time.Instant

sealed interface Preview {
    data class You(val text: String) : Preview

    data class Her(val text: String, val photo: Boolean, val reachOut: Boolean) : Preview

    data class Event(val kind: EventKind, val giftName: String?) : Preview

    data object None : Preview
}

data class ConversationRow(
    val character: Character,
    val latest: LatestConversation?,
    val preview: Preview,
    /** 排序用的时间：只算真正的消息；空的对话不算。 */
    val at: Instant?,
    /** 行上显示的时间：空对话用创建时间，让人知道它是刚开的。 */
    val displayAt: Instant?,
    val unseen: Boolean,
    val hasConversation: Boolean,
)

object ConversationList {
    fun arrange(
        characters: List<Character>,
        latest: Map<String, LatestConversation>,
        query: String = "",
    ): List<ConversationRow> {
        val indexed = characters.mapIndexedNotNull { index, character ->
            if (!CharacterSearch.matches(query, character)) return@mapIndexedNotNull null
            val conversation = latest[character.id]
            val preview = previewOf(conversation)
            // 只按最后一条消息排序：点开一个没聊过的角色会创建空对话，但不应该因此置顶
            val sortAt = conversation?.lastMessage?.at
            val displayAt = conversation?.lastMessage?.at ?: conversation?.createdAt
            IndexedRow(
                index = index,
                row = ConversationRow(
                    character = character,
                    latest = conversation,
                    preview = preview,
                    at = sortAt,
                    displayAt = displayAt,
                    unseen = conversation?.unseenReachOut == true,
                    hasConversation = conversation != null,
                ),
            )
        }
        return indexed.sortedWith(
            compareBy<IndexedRow> { if (it.row.at == null) 1 else 0 }
                .thenByDescending { it.row.at ?: Instant.MIN }
                .thenBy { it.index },
        ).map { it.row }
    }

    fun previewOf(conversation: LatestConversation?): Preview {
        val last = conversation?.lastMessage ?: return Preview.None
        val event = last.eventKind
        if (event != null) return Preview.Event(event, last.giftName)
        if (last.role == "user") return Preview.You(last.text.orEmpty())
        val text = last.zh?.takeIf { it.isNotBlank() } ?: last.ja.orEmpty()
        return Preview.Her(text, last.photo, last.reachOut)
    }

    private data class IndexedRow(val index: Int, val row: ConversationRow)
}

package sc.eidolon.nativeapp.model

import java.time.Instant

/** 聊天记录里的一行，或一轮正在进行中的消息。 */
sealed interface Entry {
    val key: String
    val at: Instant

    data class User(
        override val key: String,
        val text: String,
        override val at: Instant,
    ) : Entry

    data class Character(
        override val key: String,
        val segments: List<Segment>,
        val pending: Boolean,
        override val at: Instant,
        val affinity: AffinityChange? = null,
        val photo: Photo? = null,
        val photosUsedUp: Instant? = null,
        val reachOut: Boolean = false,
    ) : Entry

    data class Event(
        override val key: String,
        val kind: EventKind,
        override val at: Instant,
        val giftName: String? = null,
        val giftId: String? = null,
        val affinity: AffinityChange? = null,
    ) : Entry
}

data class Turn(val confirmed: Boolean, val userKey: String, val replyKey: String)

data class ConversationLog(
    val entries: List<Entry> = emptyList(),
    val turn: Turn? = null,
) {
    val isEnded: Boolean
        get() = entries.any { it is Entry.Event && it.kind == EventKind.Ended }

    fun reduce(action: LogAction): ConversationLog = when (action) {
        is LogAction.Loaded -> ConversationLog(action.recorded.map(::toEntry), null)
        is LogAction.Submitted -> if (turn != null) this else opened(action.text, action.at, false)
        is LogAction.UserSaid -> when {
            turn == null -> opened(action.text, action.at, true)
            !turn.confirmed -> copy(turn = turn.copy(confirmed = true))
            else -> this
        }
        is LogAction.Delta -> {
            val turn = turn ?: return this
            copy(entries = replace(turn.replyKey) { entry ->
                if (entry !is Entry.Character) entry else entry.withDelta(action)
            })
        }
        is LogAction.Photo -> {
            val turn = turn ?: return this
            copy(entries = replace(turn.replyKey) { entry ->
                if (entry !is Entry.Character) entry
                else entry.copy(photo = Photo(action.id, PhotoStatus.Pending))
            })
        }
        is LogAction.PhotosUsedUp -> {
            val turn = turn ?: return this
            copy(entries = replace(turn.replyKey) { entry ->
                if (entry !is Entry.Character) entry else entry.copy(photosUsedUp = action.retryAt)
            })
        }
        is LogAction.Done -> {
            val turn = turn ?: return this
            val recorded = toEntry(action.message)
            val entries = replace(turn.replyKey) { shown ->
                if (recorded !is Entry.Character) recorded
                else recorded.copy(
                    photo = settledPhoto(shown, recorded),
                    photosUsedUp = (shown as? Entry.Character)?.photosUsedUp,
                )
            }
            val withEnded = if (action.ended != null) entries + toEntry(action.ended) else entries
            ConversationLog(withEnded, null)
        }
        LogAction.Error, LogAction.Interrupted -> withoutTurn()
        is LogAction.EventRecorded -> if (entries.any { it.key == "e${action.event.id}" }) {
            this
        } else {
            copy(entries = entries + toEntry(action.event))
        }
    }

    private fun opened(text: String, at: Instant, confirmed: Boolean): ConversationLog {
        val position = entries.size
        val turn = Turn(confirmed, "new-$position", "new-${position + 1}")
        return ConversationLog(
            entries = entries + Entry.User(turn.userKey, text, at) +
                Entry.Character(
                    key = turn.replyKey,
                    segments = emptyList(),
                    pending = true,
                    at = at,
                ),
            turn = turn,
        )
    }

    private fun withoutTurn(): ConversationLog {
        val turn = turn ?: return this
        return ConversationLog(entries.filter { it.key != turn.userKey && it.key != turn.replyKey }, null)
    }

    private fun replace(key: String, update: (Entry) -> Entry): List<Entry> =
        entries.map { if (it.key == key) update(it) else it }
}

sealed interface LogAction {
    data class Loaded(val recorded: List<Recorded>) : LogAction

    data class Submitted(val text: String, val at: Instant) : LogAction

    data class UserSaid(val text: String, val at: Instant) : LogAction

    data class Delta(val segment: Int, val lang: Lang, val text: String) : LogAction

    data class Photo(val id: String) : LogAction

    data class PhotosUsedUp(val retryAt: Instant) : LogAction

    data class Done(
        val message: Recorded.CharacterMessage,
        val ended: Recorded.ConversationEvent? = null,
    ) : LogAction

    data object Error : LogAction

    data object Interrupted : LogAction

    data class EventRecorded(val event: Recorded.ConversationEvent) : LogAction
}

private fun Entry.Character.withDelta(action: LogAction.Delta): Entry.Character {
    val updated = segments.toMutableList()
    while (updated.size <= action.segment) updated.add(Segment("", ""))
    val current = updated[action.segment]
    updated[action.segment] = when (action.lang) {
        Lang.Ja -> current.copy(ja = current.ja + action.text)
        Lang.Zh -> current.copy(zh = current.zh + action.text)
    }
    return copy(segments = updated)
}

/** 已经显示出来的那张照片如果就是记录里的同一张、而且已经有结果，保留它；否则用记录里的。 */
private fun settledPhoto(shown: Entry, recorded: Entry.Character): Photo? {
    val before = (shown as? Entry.Character)?.photo
    val after = recorded.photo
    return if (before != null && after != null && before.id == after.id && before.status != PhotoStatus.Pending) {
        before
    } else {
        after
    }
}

fun toEntry(recorded: Recorded): Entry = when (recorded) {
    is Recorded.UserMessage -> Entry.User("m${recorded.id}", recorded.text, recorded.at)
    is Recorded.CharacterMessage -> Entry.Character(
        key = "m${recorded.id}",
        segments = recorded.segments.ifEmpty { listOf(Segment(recorded.ja, recorded.zh)) },
        pending = false,
        at = recorded.at,
        affinity = recorded.affinity,
        photo = recorded.photo,
    )
    is Recorded.ConversationEvent -> Entry.Event(
        key = "e${recorded.id}",
        kind = recorded.kind,
        at = recorded.at,
        giftName = recorded.giftName,
        giftId = recorded.giftId,
        affinity = recorded.affinity,
    )
    is Recorded.ReachOut -> Entry.Character(
        key = "r${recorded.id}",
        segments = recorded.segments.ifEmpty { listOf(Segment(recorded.ja, recorded.zh)) },
        pending = false,
        at = recorded.at,
        reachOut = true,
    )
}

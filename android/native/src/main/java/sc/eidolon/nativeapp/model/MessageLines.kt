package sc.eidolon.nativeapp.model

import sc.eidolon.nativeapp.core.Time
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface ChatLine {
    data class Day(val label: String, val date: LocalDate) : ChatLine

    data class Item(val entry: Entry, val first: Boolean, val last: Boolean) : ChatLine
}

object MessageLines {
    fun linesOf(entries: List<Entry>, now: Instant, zone: ZoneId): List<ChatLine> {
        val lines = mutableListOf<ChatLine>()
        var previousDate: LocalDate? = null
        entries.forEachIndexed { index, entry ->
            val date = Time.localDate(entry.at, zone)
            if (date != previousDate) {
                lines += ChatLine.Day(Time.formatDaySeparator(entry.at, now, zone), date)
                previousDate = date
            }
            val previous = entries.getOrNull(index - 1)
            val next = entries.getOrNull(index + 1)
            val first = previous == null || !sameGroup(previous, entry, zone)
            val last = next == null || !sameGroup(entry, next, zone)
            lines += ChatLine.Item(entry, first, last)
        }
        return lines
    }

    private fun sameGroup(a: Entry, b: Entry, zone: ZoneId): Boolean {
        if (a is Entry.Event || b is Entry.Event) return false
        if (!Time.isSameDay(a.at, b.at, zone)) return false
        if (b is Entry.Character && b.reachOut) return false
        return when {
            a is Entry.User && b is Entry.User -> true
            a is Entry.Character && b is Entry.Character -> true
            else -> false
        }
    }
}

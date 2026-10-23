package sc.eidolon.nativeapp.model

/** The segments of one reply, in the order she said them, as one line of subtitles. */
fun joinedSegments(segments: Map<Int, String>): String =
    segments.entries.sortedBy { it.key }.joinToString(separator = "") { it.value }

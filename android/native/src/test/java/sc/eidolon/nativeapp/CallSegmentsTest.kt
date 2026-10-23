package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test
import sc.eidolon.nativeapp.model.joinedSegments

class CallSegmentsTest {
    @Test
    fun joinsEverySegmentOfAReplyInTheOrderSheSaidThem() {
        val segments = linkedMapOf(2 to "第三句。", 0 to "第一句。", 1 to "第二句。")

        assertEquals("第一句。第二句。第三句。", joinedSegments(segments))
    }

    @Test
    fun noSegmentsIsBlank() {
        assertEquals("", joinedSegments(emptyMap()))
    }
}

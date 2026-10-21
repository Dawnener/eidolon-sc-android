package sc.eidolon.nativeapp.data.api

import java.io.ByteArrayOutputStream

/**
 * 后端每个 SSE 事件是一行 `data: <json>` 加一个空行；没有 event/id，也没有 [DONE]。
 *
 * 这里按字节累积，只在拿到完整的空行分隔后才按 UTF-8 解码，避免多字节字符被网络分块切断。
 */
class SseDecoder {
    private val pending = ByteArrayOutputStream()

    fun feed(bytes: ByteArray): List<String> {
        pending.write(bytes)
        return drain()
    }

    /** 流结束时调用：完整的帧照常返回，未结束的残帧丢弃。 */
    fun finish(): List<String> {
        val frames = drain()
        pending.reset()
        return frames
    }

    private fun drain(): List<String> {
        val data = pending.toByteArray()
        var start = 0
        val frames = mutableListOf<String>()
        while (true) {
            val end = delimiterAt(data, start) ?: break
            val frame = data.copyOfRange(start, end.index)
            decodeFrame(frame)?.let(frames::add)
            start = end.index + end.length
        }
        if (start > 0) {
            pending.reset()
            pending.write(data, start, data.size - start)
        }
        return frames
    }

    private data class Delimiter(val index: Int, val length: Int)

    private fun delimiterAt(data: ByteArray, from: Int): Delimiter? {
        var i = from
        while (i < data.size - 1) {
            if (data[i] == '\n'.code.toByte() && data[i + 1] == '\n'.code.toByte()) {
                return Delimiter(i, 2)
            }
            if (
                i + 3 < data.size &&
                data[i] == '\r'.code.toByte() &&
                data[i + 1] == '\n'.code.toByte() &&
                data[i + 2] == '\r'.code.toByte() &&
                data[i + 3] == '\n'.code.toByte()
            ) {
                return Delimiter(i, 4)
            }
            i++
        }
        return null
    }

    private fun decodeFrame(frame: ByteArray): String? {
        val text = frame.toString(Charsets.UTF_8)
        val data = text.split('\n').mapNotNull { line ->
            val clean = line.removeSuffix("\r")
            if (clean.startsWith("data:")) clean.removePrefix("data:").removePrefix(" ") else null
        }.filter { it.isNotEmpty() }
        return data.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}

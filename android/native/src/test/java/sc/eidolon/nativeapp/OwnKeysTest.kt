package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import sc.eidolon.nativeapp.model.OwnImageRequest
import sc.eidolon.nativeapp.model.OwnKeysRequest
import sc.eidolon.nativeapp.model.OwnKeysRules
import sc.eidolon.nativeapp.model.OwnLlmRequest
import sc.eidolon.nativeapp.model.OwnMemoryLlmRequest
import sc.eidolon.nativeapp.model.OwnSttRequest
import sc.eidolon.nativeapp.model.OwnTtsRequest

class OwnKeysTest {
    private val llm = OwnLlmRequest(
        baseUrl = "https://api.deepseek.com/anthropic",
        apiKey = "sk-12345678",
        model = "deepseek-v4-flash",
        protocol = "anthropic",
    )
    private val memory = OwnMemoryLlmRequest(
        baseUrl = "https://api.deepseek.com/anthropic",
        apiKey = "sk-12345678",
        model = "deepseek-v4-flash",
    )
    private val tts = OwnTtsRequest(apiKey = "fish-12345678")
    private val stt = OwnSttRequest(apiKey = "soniox-12345678")
    private val image = OwnImageRequest(apiKey = "novelai-12345678")

    @Test
    fun turnOnlyKeepsLlmMemoryAndImage() {
        val keys = OwnKeysRequest(llm = llm, memoryLlm = memory, tts = tts, stt = stt, image = image)
        val turn = OwnKeysRules.turnOnly(keys)
        assertNotNull(turn)
        assertEquals(llm, turn!!.llm)
        assertEquals(memory, turn.memoryLlm)
        assertEquals(image, turn.image)
        assertNull(turn.tts)
        assertNull(turn.stt)
    }

    @Test
    fun turnOnlyOmitsKeysWithoutLlmOrImage() {
        assertNull(OwnKeysRules.turnOnly(OwnKeysRequest(tts = tts, stt = stt)))
        assertNull(OwnKeysRules.turnOnly(null))
    }

    @Test
    fun deletingLlmAlsoDeletesMemory() {
        val keys = OwnKeysRequest(llm = llm, memoryLlm = memory)
        val cleared = OwnKeysRules.deleteLlm(keys)
        assertNull(cleared.llm)
        assertNull(cleared.memoryLlm)
    }

    @Test
    fun validationRejectsBadShapes() {
        assertNull(OwnKeysRules.validate(OwnKeysRequest(llm = llm, image = image)))
        assertEquals(
            "接口地址要像 https://api.example.com/v1 这样",
            OwnKeysRules.validate(OwnKeysRequest(llm = llm.copy(baseUrl = "not-a-url"))),
        )
        assertEquals(
            "密钥长度不对",
            OwnKeysRules.validate(OwnKeysRequest(llm = llm.copy(apiKey = "short"))),
        )
        assertEquals(
            "后台模型要和对话 LLM 一起填写",
            OwnKeysRules.validate(OwnKeysRequest(memoryLlm = memory)),
        )
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val keys = OwnKeysRequest(llm = llm, image = image)
        assertEquals(keys, OwnKeysRules.decode(OwnKeysRules.encode(keys)))
        assertNull(OwnKeysRules.decode("not json"))
    }

    @Test
    fun summaryAndMasking() {
        val keys = OwnKeysRequest(llm = llm, memoryLlm = memory, tts = tts, stt = stt, image = image)
        assertEquals("已填 LLM、后台模型、TTS、STT、生图", OwnKeysRules.summary(keys))
        assertEquals("全部用部署者的服务", OwnKeysRules.summary(null))
        assertEquals("****5678", OwnKeysRules.masked("sk-12345678"))
    }
}

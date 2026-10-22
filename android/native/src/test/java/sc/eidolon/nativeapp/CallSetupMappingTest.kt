package sc.eidolon.nativeapp

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.data.api.CallSetupDto
import sc.eidolon.nativeapp.data.api.toDomain

class CallSetupMappingTest {
    @Test
    fun mapsIceServersLimitsAndExemption() {
        val payload = """
            {
              "iceServers": [
                {"urls": ["stun:stun.example.org"]},
                {"urls": ["turn:turn.example.org:3478"], "username": "u", "credential": "p"}
              ],
              "maxCallSeconds": 900,
              "callExempt": true
            }
        """.trimIndent()

        val setup = EidolonJson.decodeFromString<CallSetupDto>(payload).toDomain()

        assertEquals(2, setup.iceServers.size)
        assertEquals(listOf("stun:stun.example.org"), setup.iceServers[0].urls)
        assertEquals("u", setup.iceServers[1].username)
        assertEquals(900, setup.maxCallSeconds)
        assertTrue(setup.callExempt)
    }
}

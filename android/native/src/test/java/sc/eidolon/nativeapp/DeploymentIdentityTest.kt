package sc.eidolon.nativeapp

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.data.api.HealthDto
import sc.eidolon.nativeapp.data.local.ServerState

class DeploymentIdentityTest {
    @Test
    fun ownKeysFollowTheDeploymentAcrossAddresses() {
        val state = ServerState()
        state.url = "http://192.0.2.10:8080"
        state.deploymentId = "deployment-a"
        assertEquals("deployment-a", state.keyNamespace)

        state.url = "http://198.51.100.20:8080"
        assertEquals("deployment-a", state.keyNamespace)
    }

    @Test
    fun anOlderServerWithoutAnIdKeepsKeysPerAddress() {
        val state = ServerState()
        state.url = "http://198.51.100.20:8080"
        assertEquals("198.51.100.20:8080", state.keyNamespace)
    }

    @Test
    fun healthCarriesTheDeploymentId() {
        val health = EidolonJson.decodeFromString<HealthDto>(
            """{"ok":true,"deployment_id":"abc"}""",
        )
        assertEquals("abc", health.deploymentId)
        assertNull(EidolonJson.decodeFromString<HealthDto>("""{"ok":true}""").deploymentId)
    }
}

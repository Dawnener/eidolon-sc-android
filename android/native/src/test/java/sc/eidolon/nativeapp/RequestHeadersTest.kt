package sc.eidolon.nativeapp

import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.data.api.RequestHeaders
import sc.eidolon.nativeapp.data.local.AccessCredentials
import sc.eidolon.nativeapp.data.local.BasicAuthCredentials

class RequestHeadersTest {
    @Test
    fun httpDevOriginIsLocalhost() {
        assertEquals(
            "http://localhost:3000",
            RequestHeaders.originFor("http://192.0.2.10:8080"),
        )
    }

    @Test
    fun httpsOriginIsTheBaseOrigin() {
        assertEquals("https://eidolon.example.com", RequestHeaders.originFor("https://eidolon.example.com"))
        assertEquals(
            "https://eidolon.example.com:8443",
            RequestHeaders.originFor("https://eidolon.example.com:8443/api"),
        )
    }

    @Test
    fun timezoneFallsBackForNonIanaIds() {
        assertEquals("Asia/Shanghai", RequestHeaders.timezone(""))
        assertEquals("Asia/Shanghai", RequestHeaders.timezone("GMT+08:00"))
        assertEquals("Asia/Shanghai", RequestHeaders.timezone("UTC+8"))
        assertEquals("Asia/Tokyo", RequestHeaders.timezone("Asia/Tokyo"))
    }

    @Test
    fun mutatingMethods() {
        assertTrue(RequestHeaders.isMutating("POST"))
        assertTrue(RequestHeaders.isMutating("delete"))
        assertFalse(RequestHeaders.isMutating("GET"))
        assertFalse(RequestHeaders.isMutating("HEAD"))
        assertFalse(RequestHeaders.isMutating("OPTIONS"))
    }

    @Test
    fun accessTokenOnlyGoesToTheConfiguredHttpsHost() {
        val credentials = AccessCredentials("client-id", "client-secret")
        val base = "https://eidolon.example.com"
        assertEquals(
            "client-id" to "client-secret",
            RequestHeaders.accessHeaders(
                base,
                "https://eidolon.example.com/api/health".toHttpUrl(),
                credentials,
            ),
        )
        assertNull(
            RequestHeaders.accessHeaders(
                base,
                "https://other.example.com/api/health".toHttpUrl(),
                credentials,
            ),
        )
        assertNull(
            RequestHeaders.accessHeaders(
                base,
                "https://eidolon.example.com:8443/api/health".toHttpUrl(),
                credentials,
            ),
        )
        assertNull(
            RequestHeaders.accessHeaders(
                "http://192.0.2.10:8080",
                "http://192.0.2.10:8080/api/health".toHttpUrl(),
                credentials,
            ),
        )
        assertNull(
            RequestHeaders.accessHeaders(base, "https://eidolon.example.com/api/health".toHttpUrl(), null),
        )
    }

    @Test
    fun basicAuthOnlyGoesToTheConfiguredHttpsHost() {
        val credentials = BasicAuthCredentials("eidolon", "secret")
        val base = "https://eidolon.example.com"
        assertEquals(
            Credentials.basic("eidolon", "secret", Charsets.UTF_8),
            RequestHeaders.basicAuthHeader(
                base,
                "https://eidolon.example.com/api/health".toHttpUrl(),
                credentials,
            ),
        )
        assertNull(
            RequestHeaders.basicAuthHeader(
                base,
                "https://other.example.com/api/health".toHttpUrl(),
                credentials,
            ),
        )
        assertNull(
            RequestHeaders.basicAuthHeader(
                "http://192.0.2.10:8080",
                "http://192.0.2.10:8080/api/health".toHttpUrl(),
                credentials,
            ),
        )
        assertNull(
            RequestHeaders.basicAuthHeader(base, "https://eidolon.example.com/api/health".toHttpUrl(), null),
        )
    }
}

package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.data.api.RequestHeaders

class RequestHeadersTest {
    @Test
    fun httpDevOriginIsLocalhost() {
        assertEquals(
            "http://localhost:3000",
            RequestHeaders.originFor("http://192.168.114.40:8080"),
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
}

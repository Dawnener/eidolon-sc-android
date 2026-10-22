package sc.eidolon.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.data.api.ApiError
import sc.eidolon.nativeapp.core.Time

class ApiErrorTest {
    @Test
    fun cooldown409() {
        val error = ApiError.from(
            409,
            """{"detail":"她现在不想说话","cooldown_until":"2026-10-21T10:00:00+00:00"}""",
        )
        assertEquals("她现在不想说话", error.message)
        assertTrue(error.cooldownUntil != null)
    }

    @Test
    fun limit429WithRetryAfter() {
        val error = ApiError.from(
            429,
            """{"detail":"这一小时用完了","retry_at":"2026-10-21T10:00:00+00:00"}""",
            retryAfter = "120",
        )
        assertEquals(120L, error.retryAfterSeconds)
        assertEquals(Time.parseInstant("2026-10-21T10:00:00+00:00"), error.retryAt)
    }

    @Test
    fun ownKeysOnlyLimitHasNullRetryAt() {
        val error = ApiError.from(429, """{"detail":"只能用自带密钥","retry_at":null}""")
        assertNull(error.retryAt)
    }

    @Test
    fun pydanticArrayDetailTakesFirstMessage() {
        val error = ApiError.from(
            422,
            """{"detail":[{"type":"value_error","msg":"Value error, 密钥不对"}]}""",
        )
        assertEquals("密钥不对", error.message)
    }

    @Test
    fun bannedAndOriginErrorsAreDistinguished() {
        assertTrue(ApiError.from(403, """{"detail":"这位用户已被封禁"}""").isBanned)
        assertTrue(ApiError.from(403, """{"detail":"请求来自其他网站，已拒绝"}""").isOriginRejected)
    }

    @Test
    fun giftsClosed409IsDistinctFromOtherConflicts() {
        val closed = ApiError.from(
            409,
            """{"detail":"礼物没有开放","gifts_closed":true}""",
        )
        assertTrue(closed.giftsClosed)
        assertTrue(!ApiError.from(409, """{"detail":"没有抽奖次数了"}""").giftsClosed)
    }

    @Test
    fun packageErrorCodesAreKept() {
        val error = ApiError.from(413, """{"detail":"too big","code":"too_large"}""")
        assertEquals("too_large", error.code)
        assertEquals(null, ApiError.from(409, """{"detail":"plain"}""").code)
    }

    @Test
    fun nonJsonBodyFallsBack() {
        val error = ApiError.from(500, "<html>oops</html>")
        assertEquals("请求失败（500）", error.message)
    }
}

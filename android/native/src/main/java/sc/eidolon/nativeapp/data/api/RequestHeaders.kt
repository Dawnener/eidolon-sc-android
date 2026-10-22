package sc.eidolon.nativeapp.data.api

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sc.eidolon.nativeapp.data.local.AccessCredentials
import sc.eidolon.nativeapp.data.local.BasicAuthCredentials
import java.net.URI
import java.time.ZoneId

object RequestHeaders {
    /**
     * 后端在开发模式（没有 EIDOLON_DOMAIN）只接受 http://localhost|127.0.0.1|::1 的 Origin；
     * 公开部署则要求刚好等于 https://<domain>。原生客户端没有浏览器 Origin，需要自己补。
     */
    fun originFor(baseUrl: String): String {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull()
            ?: return "http://localhost:3000"
        return if (uri.scheme.equals("http", ignoreCase = true)) {
            "http://localhost:3000"
        } else {
            val authority = uri.rawAuthority ?: uri.host ?: return "http://localhost:3000"
            "${uri.scheme}://$authority"
        }
    }

    fun timezone(zoneId: String = ZoneId.systemDefault().id): String {
        val value = zoneId.trim()
        return if (
            value.isEmpty() ||
            value.startsWith("GMT") ||
            value.startsWith("UTC+") ||
            value.startsWith("UTC-")
        ) {
            "Asia/Shanghai"
        } else {
            value
        }
    }

    fun isMutating(method: String): Boolean =
        method.uppercase() !in setOf("GET", "HEAD", "OPTIONS")

    /**
     * Cloudflare Access 服务令牌只发给当前后端所在的 HTTPS host:port。头像等图片可能
     * 指向别的域名；明文 HTTP 也不带，免得令牌在局域网里被截获。
     */
    fun accessHeaders(
        baseUrl: String?,
        requestUrl: HttpUrl,
        credentials: AccessCredentials?,
    ): Pair<String, String>? {
        if (credentials == null || !canSendAuth(baseUrl, requestUrl)) return null
        return credentials.clientId to credentials.clientSecret
    }

    /** Basic Auth 的 `Authorization` 值，规则同 [accessHeaders]：只发给同一 HTTPS host:port。 */
    fun basicAuthHeader(
        baseUrl: String?,
        requestUrl: HttpUrl,
        credentials: BasicAuthCredentials?,
    ): String? {
        if (credentials == null || !canSendAuth(baseUrl, requestUrl)) return null
        return Credentials.basic(credentials.username, credentials.password, Charsets.UTF_8)
    }

    private fun canSendAuth(baseUrl: String?, requestUrl: HttpUrl): Boolean {
        val base = baseUrl?.toHttpUrlOrNull() ?: return false
        return base.isHttps &&
            requestUrl.isHttps &&
            requestUrl.host == base.host &&
            requestUrl.port == base.port
    }
}

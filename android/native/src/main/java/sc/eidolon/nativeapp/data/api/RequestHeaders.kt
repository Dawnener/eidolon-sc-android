package sc.eidolon.nativeapp.data.api

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
}

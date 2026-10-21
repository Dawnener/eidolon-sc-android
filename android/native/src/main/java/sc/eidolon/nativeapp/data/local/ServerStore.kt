package sc.eidolon.nativeapp.data.local

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 只接受站点根地址；去掉末尾斜杠，路径/查询/片段都拒绝。 */
object ServerUrl {
    fun normalize(value: String): String? {
        var text = value.trim()
        if (text.isEmpty()) return null
        if (!text.contains("://")) text = "http://$text"
        val url = text.toHttpUrlOrNull() ?: return null
        if (url.scheme !in setOf("http", "https")) return null
        if (url.encodedPath != "/" && url.encodedPath.isNotEmpty()) return null
        if (url.query != null || url.fragment != null) return null
        return url.toString().trimEnd('/')
    }
}

class ServerStore(private val prefs: PreferencesStore) {
    suspend fun current(): String? = prefs.serverUrl()?.let(ServerUrl::normalize)

    suspend fun save(value: String): String? {
        val normalized = ServerUrl.normalize(value) ?: return null
        prefs.setServerUrl(normalized)
        return normalized
    }

    suspend fun clear() = prefs.setServerUrl(null)
}

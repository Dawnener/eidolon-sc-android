package sc.eidolon.nativeapp.data.local

import kotlinx.serialization.Serializable
import sc.eidolon.nativeapp.core.EidolonJson
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val host: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
)

/** 只保存 `eidolon_session` 的 CookieJar；值用 Keystore 加密后放 SharedPreferences。 */
class CookieStore(
    private val prefs: android.content.SharedPreferences,
    private val secure: SecureStore,
) : CookieJar {
    private var cookie: StoredCookie? = load()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val session = cookies.firstOrNull { it.name == COOKIE_NAME } ?: return
        val stored = StoredCookie(
            name = session.name,
            value = session.value,
            host = url.host,
            path = session.path,
            expiresAt = session.expiresAt,
            secure = session.secure,
            httpOnly = session.httpOnly,
        )
        cookie = stored
        persist(stored)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val stored = cookie ?: return emptyList()
        if (stored.host != url.host) return emptyList()
        if (stored.expiresAt <= System.currentTimeMillis()) {
            clear()
            return emptyList()
        }
        if (stored.secure && url.scheme != "https") return emptyList()
        return listOf(
            Cookie.Builder()
                .name(stored.name)
                .value(stored.value)
                .hostOnlyDomain(stored.host)
                .path(stored.path)
                .apply {
                    if (stored.expiresAt != Long.MAX_VALUE) expiresAt(stored.expiresAt)
                    if (stored.secure) secure()
                    if (stored.httpOnly) httpOnly()
                }
                .build(),
        )
    }

    fun clear() {
        cookie = null
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): StoredCookie? {
        val ciphertext = prefs.getString(KEY, null) ?: return null
        val json = secure.decrypt(ciphertext) ?: return null
        return runCatching { EidolonJson.decodeFromString(StoredCookie.serializer(), json) }.getOrNull()
    }

    private fun persist(stored: StoredCookie) {
        val json = EidolonJson.encodeToString(StoredCookie.serializer(), stored)
        val ciphertext = secure.encrypt(json) ?: return
        prefs.edit().putString(KEY, ciphertext).apply()
    }

    private companion object {
        const val KEY = "eidolon_session"
        const val COOKIE_NAME = "eidolon_session"
    }
}

package sc.eidolon.nativeapp.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccessCredentials(val clientId: String, val clientSecret: String)

data class BasicAuthCredentials(val username: String, val password: String)

/**
 * 公网入口的访问凭证。它属于这个部署，不属于某个用户；存在本机并加密，
 * 请求拦截器只把它发给当前服务器地址所在的 host，避免误发给别的地址。
 */
class AccessStore(private val prefs: PreferencesStore) {
    private val _credentials = MutableStateFlow<AccessCredentials?>(null)
    val credentials: StateFlow<AccessCredentials?> = _credentials.asStateFlow()
    private val _basic = MutableStateFlow<BasicAuthCredentials?>(null)
    val basic: StateFlow<BasicAuthCredentials?> = _basic.asStateFlow()

    suspend fun load() {
        _credentials.value = prefs.accessCredentials()
        _basic.value = prefs.basicAuthCredentials()
    }

    suspend fun save(clientId: String, clientSecret: String) {
        val credentials = AccessCredentials(clientId.trim(), clientSecret.trim())
        prefs.setAccessCredentials(credentials)
        _credentials.value = credentials
    }

    suspend fun clear() {
        prefs.clearAccessCredentials()
        _credentials.value = null
    }

    suspend fun saveBasic(username: String, password: String) {
        val credentials = BasicAuthCredentials(username.trim(), password.trim())
        prefs.setBasicAuthCredentials(credentials)
        _basic.value = credentials
    }

    suspend fun clearBasic() {
        prefs.clearBasicAuthCredentials()
        _basic.value = null
    }

    fun current(): AccessCredentials? = _credentials.value

    fun currentBasic(): BasicAuthCredentials? = _basic.value
}

package sc.eidolon.nativeapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import sc.eidolon.nativeapp.model.OwnKeysRequest
import sc.eidolon.nativeapp.model.OwnKeysRules

private val Context.eidolonDataStore by preferencesDataStore("eidolon_preferences")

class PreferencesStore(
    private val context: Context,
    private val secure: SecureStore,
) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val userId = stringPreferencesKey("user_id")
        val accountName = stringPreferencesKey("account_name")
        val pushEnabled = booleanPreferencesKey("push_enabled")
        val accessClientId = stringPreferencesKey("access_client_id")
        val accessClientSecret = stringPreferencesKey("access_client_secret")
        val basicAuthUsername = stringPreferencesKey("basic_auth_username")
        val basicAuthPassword = stringPreferencesKey("basic_auth_password")

        fun ownKeys(authority: String, userId: String) =
            stringPreferencesKey("own_keys.$authority.$userId")
    }

    suspend fun serverUrl(): String? = context.eidolonDataStore.data.first()[Keys.serverUrl]

    suspend fun setServerUrl(value: String?) {
        context.eidolonDataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.serverUrl) else prefs[Keys.serverUrl] = value
        }
    }

    suspend fun userId(): String? = context.eidolonDataStore.data.first()[Keys.userId]

    suspend fun accountName(): String? = context.eidolonDataStore.data.first()[Keys.accountName]

    suspend fun pushEnabled(): Boolean =
        context.eidolonDataStore.data.first()[Keys.pushEnabled] ?: false

    suspend fun setPushEnabled(value: Boolean) {
        context.eidolonDataStore.edit { prefs -> prefs[Keys.pushEnabled] = value }
    }

    suspend fun setUser(userId: String, name: String) {
        context.eidolonDataStore.edit { prefs ->
            prefs[Keys.userId] = userId
            prefs[Keys.accountName] = name
        }
    }

    suspend fun clearUser() {
        context.eidolonDataStore.edit { prefs ->
            prefs.remove(Keys.userId)
            prefs.remove(Keys.accountName)
        }
    }

    suspend fun ownKeys(authority: String, userId: String): OwnKeysRequest? {
        val key = Keys.ownKeys(authority, userId)
        val ciphertext = context.eidolonDataStore.data.first()[key] ?: return null
        val json = secure.decrypt(ciphertext)
        if (json == null) {
            clearOwnKeys(authority, userId)
            return null
        }
        val keys = OwnKeysRules.decode(json)
        if (keys == null) clearOwnKeys(authority, userId)
        return keys
    }

    suspend fun setOwnKeys(authority: String, userId: String, keys: OwnKeysRequest) {
        val ciphertext = secure.encrypt(OwnKeysRules.encode(keys))
            ?: error("这个设备无法保存自带密钥")
        context.eidolonDataStore.edit { prefs ->
            prefs[Keys.ownKeys(authority, userId)] = ciphertext
        }
    }

    suspend fun clearOwnKeys(authority: String, userId: String) {
        context.eidolonDataStore.edit { prefs ->
            prefs.remove(Keys.ownKeys(authority, userId))
        }
    }

    suspend fun accessCredentials(): AccessCredentials? {
        val prefs = context.eidolonDataStore.data.first()
        val clientId = prefs[Keys.accessClientId] ?: return null
        val secret = prefs[Keys.accessClientSecret] ?: return null
        val plain = secure.decrypt(secret) ?: run {
            clearAccessCredentials()
            return null
        }
        return AccessCredentials(clientId, plain)
    }

    suspend fun setAccessCredentials(credentials: AccessCredentials) {
        val ciphertext = secure.encrypt(credentials.clientSecret)
            ?: error("这个设备无法保存访问凭证")
        context.eidolonDataStore.edit { prefs ->
            prefs[Keys.accessClientId] = credentials.clientId
            prefs[Keys.accessClientSecret] = ciphertext
        }
    }

    suspend fun clearAccessCredentials() {
        context.eidolonDataStore.edit { prefs ->
            prefs.remove(Keys.accessClientId)
            prefs.remove(Keys.accessClientSecret)
        }
    }

    suspend fun basicAuthCredentials(): BasicAuthCredentials? {
        val prefs = context.eidolonDataStore.data.first()
        val username = prefs[Keys.basicAuthUsername] ?: return null
        val password = prefs[Keys.basicAuthPassword] ?: return null
        val plain = secure.decrypt(password) ?: run {
            clearBasicAuthCredentials()
            return null
        }
        return BasicAuthCredentials(username, plain)
    }

    suspend fun setBasicAuthCredentials(credentials: BasicAuthCredentials) {
        val ciphertext = secure.encrypt(credentials.password)
            ?: error("这个设备无法保存访问凭证")
        context.eidolonDataStore.edit { prefs ->
            prefs[Keys.basicAuthUsername] = credentials.username
            prefs[Keys.basicAuthPassword] = ciphertext
        }
    }

    suspend fun clearBasicAuthCredentials() {
        context.eidolonDataStore.edit { prefs ->
            prefs.remove(Keys.basicAuthUsername)
            prefs.remove(Keys.basicAuthPassword)
        }
    }
}

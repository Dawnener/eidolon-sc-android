package sc.eidolon.nativeapp.data.local

import android.content.Context
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
}

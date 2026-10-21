package sc.eidolon.nativeapp.data.local

import sc.eidolon.nativeapp.model.OwnKeysRequest

/** 自带密钥按「服务器 + 用户」隔离；换服务器不会串，退出登录清掉当前用户的。 */
class OwnKeysStore(
    private val prefs: PreferencesStore,
    private val serverState: ServerState,
) {
    suspend fun load(userId: String): OwnKeysRequest? {
        val authority = serverState.authority ?: return null
        return prefs.ownKeys(authority, userId)
    }

    suspend fun save(userId: String, keys: OwnKeysRequest) {
        val authority = serverState.authority ?: return
        prefs.setOwnKeys(authority, userId, keys)
    }

    suspend fun clear(userId: String) {
        val authority = serverState.authority ?: return
        prefs.clearOwnKeys(authority, userId)
    }
}

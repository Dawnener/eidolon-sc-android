package sc.eidolon.nativeapp.data.local

import sc.eidolon.nativeapp.model.OwnKeysRequest

/**
 * 自带密钥按「部署 + 用户」隔离：同一个后端换了 IP 或域名仍然读得到，切换到另一个
 * 部署则不会串。旧版 App 按「服务器地址 + 用户」存过，读取时顺带迁移当前地址下的旧数据。
 * 退出登录清掉当前部署（以及当前地址）下的密钥。
 */
class OwnKeysStore(
    private val prefs: PreferencesStore,
    private val serverState: ServerState,
) {
    suspend fun load(userId: String): OwnKeysRequest? {
        val namespace = serverState.keyNamespace ?: return null
        prefs.ownKeys(namespace, userId)?.let { return it }
        return migrateFromAddress(namespace, userId)
    }

    suspend fun save(userId: String, keys: OwnKeysRequest) {
        val namespace = serverState.keyNamespace ?: return
        prefs.setOwnKeys(namespace, userId, keys)
    }

    suspend fun clear(userId: String) {
        val namespace = serverState.keyNamespace
        if (namespace != null) prefs.clearOwnKeys(namespace, userId)
        val authority = serverState.authority
        if (authority != null && authority != namespace) {
            prefs.clearOwnKeys(authority, userId)
        }
    }

    /**
     * 升级前密钥存在 `own_keys.<host:port>.<user>` 下。只有当前仍连在这个地址时，
     * 才能确认这些密钥属于当前部署，于是复制到部署命名空间并保留旧副本。
     */
    private suspend fun migrateFromAddress(namespace: String, userId: String): OwnKeysRequest? {
        val authority = serverState.authority ?: return null
        if (namespace == authority) return null
        val keys = prefs.ownKeys(authority, userId) ?: return null
        prefs.setOwnKeys(namespace, userId, keys)
        return keys
    }
}

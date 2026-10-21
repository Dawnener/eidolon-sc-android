package sc.eidolon.nativeapp.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sc.eidolon.nativeapp.data.api.ApiError
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.data.api.HealthDto
import sc.eidolon.nativeapp.data.local.CookieStore
import sc.eidolon.nativeapp.data.local.OwnKeysStore
import sc.eidolon.nativeapp.data.local.PreferencesStore
import sc.eidolon.nativeapp.data.local.ServerState
import sc.eidolon.nativeapp.data.local.ServerStore
import sc.eidolon.nativeapp.data.local.ServerUrl
import sc.eidolon.nativeapp.model.Account

sealed interface SessionState {
    data object Loading : SessionState

    data object NoServer : SessionState

    data object SignedOut : SessionState

    data object NeedsConsent : SessionState

    data class SignedIn(val account: Account) : SessionState

    data class Banned(val message: String, val contact: String? = null) : SessionState

    data class Unavailable(val message: String) : SessionState
}

class SessionController(
    private val api: EidolonApi,
    private val serverStore: ServerStore,
    private val prefs: PreferencesStore,
    private val cookieStore: CookieStore,
    private val ownKeysStore: OwnKeysStore,
    private val serverState: ServerState,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    var serverUrl: String? = null
        private set

    val account: Account?
        get() = (state.value as? SessionState.SignedIn)?.account

    suspend fun bootstrap(knownHealth: HealthDto? = null) {
        val saved = serverStore.current()
        if (saved == null) {
            serverState.url = null
            serverState.deploymentId = null
            serverUrl = null
            _state.value = SessionState.NoServer
            return
        }
        serverUrl = saved
        serverState.url = saved
        serverState.deploymentId = knownHealth?.deploymentId
        _state.value = SessionState.Loading
        try {
            // 自带密钥按部署 ID 存；旧后端不返回时退回按地址存。
            serverState.deploymentId = (knownHealth ?: api.health()).deploymentId
            val account = api.account()
            prefs.setUser(account.id, account.name)
            _state.value = SessionState.SignedIn(account)
        } catch (error: ApiError) {
            when {
                error.isUnauthorized -> {
                    cookieStore.clear()
                    _state.value = SessionState.SignedOut
                }
                error.isConsentRequired -> _state.value = SessionState.NeedsConsent
                error.isBanned -> {
                    cookieStore.clear()
                    _state.value = SessionState.Banned(error.message)
                }
                error.isOriginRejected -> _state.value = SessionState.Unavailable(
                    "服务器拒绝了请求来源（Origin）。原生客户端需要和服务器使用同一个地址。",
                )
                else -> _state.value = SessionState.Unavailable(error.message)
            }
        } catch (error: Exception) {
            _state.value = SessionState.Unavailable(error.message ?: "连不上服务器")
        }
    }

    /** 网络层任何 401/428/403 都回到对应的登录态；403 的 Origin 配置错误不强制退出。 */
    fun onError(error: ApiError) {
        when {
            error.isUnauthorized -> {
                cookieStore.clear()
                _state.value = SessionState.SignedOut
            }
            error.isConsentRequired -> _state.value = SessionState.NeedsConsent
            error.isBanned -> {
                cookieStore.clear()
                _state.value = SessionState.Banned(error.message)
            }
        }
    }

    /** 先探测新地址，成功才保存；失败恢复到原地址。 */
    suspend fun useServer(input: String): Result<String> {
        val normalized = ServerUrl.normalize(input)
            ?: return Result.failure(IllegalArgumentException("地址要以 http:// 或 https:// 开头"))
        val previous = serverState.url
        val previousDeployment = serverState.deploymentId
        serverState.url = normalized
        serverState.deploymentId = null
        return try {
            // 只探测 /api/health；登录方式等登录页自己会取，少一个可能失败的步骤
            val health = api.health()
            serverStore.save(normalized)
            serverUrl = normalized
            cookieStore.clear()
            bootstrap(health)
            Result.success(normalized)
        } catch (error: kotlinx.coroutines.CancellationException) {
            serverState.url = previous
            serverState.deploymentId = previousDeployment
            throw error
        } catch (error: Exception) {
            serverState.url = previous
            serverState.deploymentId = previousDeployment
            Result.failure(error)
        }
    }

    suspend fun signInDev(name: String): Result<Unit> = runCatching {
        api.devLogin(name.trim())
        bootstrap()
    }

    suspend fun consent(): Result<Unit> = runCatching {
        api.consent()
        bootstrap()
    }

    suspend fun logout(): Result<Unit> = runCatching {
        runCatching { api.logout() }
        clearCurrentUser()
        cookieStore.clear()
        _state.value = SessionState.SignedOut
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        api.deleteAccount()
        clearCurrentUser()
        cookieStore.clear()
        _state.value = SessionState.SignedOut
    }

    private suspend fun clearCurrentUser() {
        val userId = prefs.userId()
        if (userId != null) {
            ownKeysStore.clear(userId)
        }
        prefs.clearUser()
    }
}

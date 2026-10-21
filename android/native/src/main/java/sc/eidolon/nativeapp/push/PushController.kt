package sc.eidolon.nativeapp.push

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.data.local.PreferencesStore
import sc.eidolon.nativeapp.session.SessionController
import sc.eidolon.nativeapp.session.SessionState

/**
 * 推送开关：只有用户允许推送且已登录时才运行前台服务；任何一件不成立就停掉服务，
 * 不在后台空转。
 */
class PushController(
    context: Context,
    private val session: SessionController,
    private val preferences: PreferencesStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init {
        scope.launch {
            _enabled.value = preferences.pushEnabled()
            syncService()
        }
        scope.launch {
            session.state.collect { syncService() }
        }
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        scope.launch {
            preferences.setPushEnabled(on)
            syncService()
        }
    }

    private fun syncService() {
        if (_enabled.value && session.state.value is SessionState.SignedIn) {
            PushService.start(appContext)
        } else {
            PushService.stop(appContext)
        }
    }
}

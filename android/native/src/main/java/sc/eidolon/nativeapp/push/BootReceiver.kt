package sc.eidolon.nativeapp.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.EidolonApplication

/** 开机后如果用户开着推送，就把前台服务拉起来；MIUI 还需要允许自启动。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as EidolonApplication).container
                if (container.preferences.pushEnabled()) PushService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}

package sc.eidolon.nativeapp.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sc.eidolon.nativeapp.EidolonApplication
import sc.eidolon.nativeapp.MainActivity
import sc.eidolon.nativeapp.R
import sc.eidolon.nativeapp.session.SessionState

/**
 * 前台服务维持一条到后端的 SSE：系统会给它一条低优先级常驻通知，但不会像普通后台
 * 进程那样很快被回收。只由「推送」开关打开且已登录时运行；关闭开关或退出登录就停。
 *
 * 电池方面：一条 TCP 连接、服务器每 80 秒一次心跳、断线指数退避；不轮询、不持有
 * WakeLock，常驻通知是 IMPORTANCE_MIN 的静默通知。
 */
class PushService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: Job? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = requireNotNull(getSystemService(NotificationManager::class.java))
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    "推送服务",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "维持搭话推送的后台连接"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as EidolonApplication).container
        if (intent?.action == ACTION_STOP) {
            container.push.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (connection?.isActive == true) return START_STICKY
        connection = scope.launch {
            val state = container.session.state.first { it !is SessionState.Loading }
            if (state !is SessionState.SignedIn || !container.preferences.pushEnabled()) {
                stopSelf()
                return@launch
            }
            keepConnected(container)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        connection?.cancel()
        connection = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, PushService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Eidolon")
            .setContentText("正在接收搭话")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "停止推送", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun keepConnected(container: sc.eidolon.nativeapp.AppContainer) {
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            var heard = false
            try {
                container.api.pushEvents().collect { event ->
                    heard = true
                    container.pushNotifier.show(event)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 断网、服务器重启或代理断开：等一会儿再连。
            }
            if (heard) backoff = 1_000L
            if (!currentCoroutineContext().isActive) break
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    companion object {
        private const val CHANNEL_SERVICE = "push_service"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "sc.eidolon.nativeapp.STOP_PUSH"

        fun start(context: Context) {
            val intent = Intent(context, PushService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // 后台启动前台服务被系统拒绝时，等下次打开 App 再由控制器启动。
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PushService::class.java))
        }
    }
}

package sc.eidolon.nativeapp.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import sc.eidolon.nativeapp.MainActivity
import sc.eidolon.nativeapp.R
import sc.eidolon.nativeapp.data.api.PushEventDto

/** 把一条搭话显示成系统通知；点开后进入对应角色的对话。 */
class PushNotifier(private val context: Context) {
    private val manager =
        requireNotNull(context.getSystemService(NotificationManager::class.java))

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "搭话",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "角色主动发来的消息"
                },
            )
        }
    }

    fun show(event: PushEventDto) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CHARACTER_ID, event.characterId)
            putExtra(MainActivity.EXTRA_CONVERSATION_ID, event.conversationId)
        }
        val pending = PendingIntent.getActivity(
            context,
            event.conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title.ifBlank { "Eidolon" })
            .setContentText(event.body.ifBlank { "她给你发来一条消息" })
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(event.conversationId.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "reach_outs"
    }
}

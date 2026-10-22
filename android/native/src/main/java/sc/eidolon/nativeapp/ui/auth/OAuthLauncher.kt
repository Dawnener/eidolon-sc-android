package sc.eidolon.nativeapp.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 用系统浏览器打开后端 OAuth 入口；回调通过 `eidolon://auth` 回到 App。
 *
 * 这里刻意不用 Custom Tabs：Custom Tabs 有时会复用调用方的任务，
 * 把 EXCLUDE_FROM_RECENTS 错误地套在 App 自己的任务上。普通 ACTION_VIEW
 * 配合 NEW_TASK + MULTIPLE_TASK，可以把授权页放进一个独立且不进最近任务的任务。
 */
fun openOAuth(context: Context, serverUrl: String, provider: String) {
    val base = serverUrl.trimEnd('/')
    val uri = Uri.parse("$base/api/auth/$provider/start?mobile=1")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
        )
    }
    runCatching { context.startActivity(intent) }
}

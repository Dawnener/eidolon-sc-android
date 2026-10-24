package sc.eidolon.nativeapp.data.repo

import sc.eidolon.nativeapp.BuildConfig
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.data.local.ServerState
import sc.eidolon.nativeapp.model.AppUpdate

/** Checks the deployment for an Android release newer than this build. */
class UpdateChecker(
    private val api: EidolonApi,
    private val serverState: ServerState,
) {
    suspend fun check(): AppUpdate? {
        val latest = api.appUpdate()
        if (!isNewer(latest.version, BuildConfig.VERSION_NAME)) return null
        val base = serverState.url?.trimEnd('/') ?: return null
        val url = if (latest.url.startsWith("http://") || latest.url.startsWith("https://")) {
            latest.url
        } else {
            base + latest.url
        }
        return AppUpdate(latest.version, url)
    }
}

/** Whether `latest` is a strictly newer dotted version than `current`; missing or
 * non-numeric parts count as 0, so `0.1.10` is newer than `0.1.9`. */
internal fun isNewer(latest: String, current: String): Boolean {
    val left = latest.split(".").map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val right = current.split(".").map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(left.size, right.size)) {
        val newer = left.getOrElse(index) { 0 }
        val mine = right.getOrElse(index) { 0 }
        if (newer != mine) return newer > mine
    }
    return false
}

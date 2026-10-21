package sc.eidolon.nativeapp.data.local

import java.net.URI

/** 当前服务器地址，供同步的 OkHttp 拦截器读取。 */
class ServerState {
    @Volatile
    var url: String? = null

    val authority: String?
        get() = url?.let { runCatching { URI(it).rawAuthority }.getOrNull() }
}

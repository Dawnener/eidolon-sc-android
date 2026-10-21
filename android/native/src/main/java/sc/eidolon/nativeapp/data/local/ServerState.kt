package sc.eidolon.nativeapp.data.local

import java.net.URI

/** 当前服务器地址，供同步的 OkHttp 拦截器读取。 */
class ServerState {
    @Volatile
    var url: String? = null

    /** 后端在 /api/health 里给出的稳定部署 ID；旧版后端没有。 */
    @Volatile
    var deploymentId: String? = null

    val authority: String?
        get() = url?.let { runCatching { URI(it).rawAuthority }.getOrNull() }

    /** 自带密钥的存储命名空间：优先用部署 ID，同一部署换地址不会丢；旧后端退回地址。 */
    val keyNamespace: String?
        get() = deploymentId ?: authority
}

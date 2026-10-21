package sc.eidolon.nativeapp.data.api

/** 网络层发现 401/428/403 时通知登录态；由 SessionController 注册。 */
class ApiErrorBus {
    @Volatile
    var listener: ((ApiError) -> Unit)? = null

    fun report(error: ApiError) {
        listener?.invoke(error)
    }
}

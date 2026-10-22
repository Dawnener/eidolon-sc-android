package sc.eidolon.nativeapp.ui.auth

/** GitHub/Google 的显示名，和网页端保持一致。 */
fun oauthProviderLabel(provider: String): String = when (provider) {
    "github" -> "GitHub"
    "google" -> "Google"
    else -> provider
}

/** 后端通过 `eidolon://auth?error=…` 带回来的原因。 */
fun oauthErrorMessage(reason: String?): String = when (reason) {
    "too_new" -> "GitHub 账号注册满一年后才能用来登录。"
    "email_unverified" -> "这个 Google 账号的邮箱还没有验证，验证后才能用来登录。"
    "banned" -> "这位用户已被封禁，无法登录。"
    "state" -> "登录请求已失效，请重新登录。"
    "cancelled" -> "已取消登录。"
    "provider" -> "暂时无法向登录服务确认你的身份，请稍后再试。"
    "taken" -> "这个身份已经属于另一位用户。"
    "already_linked" -> "你已经绑定了这种登录方式的另一个身份。"
    else -> "登录失败，请重新登录。"
}

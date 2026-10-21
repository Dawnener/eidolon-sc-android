package sc.eidolon.nativeapp.model

import java.time.Instant

/** 界面层的失败提示；`until` 用于限额/冷却，`toOwnKeys` 表示可以引导去填自带密钥。 */
data class Failure(
    val text: String,
    val toOwnKeys: Boolean = false,
    val until: Instant? = null,
)

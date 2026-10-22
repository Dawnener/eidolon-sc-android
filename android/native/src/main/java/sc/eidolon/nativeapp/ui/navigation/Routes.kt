package sc.eidolon.nativeapp.ui.navigation

import android.net.Uri

object Routes {
    const val Conversations = "conversations"
    const val Chat = "chat/{characterId}"
    const val Gifts = "gifts"
    const val GiftsPattern = "gifts?to={to}"
    const val Settings = "settings"
    const val Profile = "settings/profile"
    const val OwnKeys = "settings/own-keys"
    const val ReachOutModel = "settings/reach-out-model"
    const val Memories = "settings/memories"
    const val Stats = "settings/stats"
    const val DataPackage = "settings/data-package"
    const val Server = "settings/server"

    fun chat(characterId: String) = "chat/${Uri.encode(characterId)}"

    fun gifts(characterId: String? = null) =
        if (characterId == null) Gifts else "gifts?to=${Uri.encode(characterId)}"

    fun baseRoute(route: String?): String? = route?.substringBefore('?')
}

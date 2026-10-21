package sc.eidolon.nativeapp.ui.navigation

import android.net.Uri

object Routes {
    const val Conversations = "conversations"
    const val Chat = "chat/{characterId}"
    const val Settings = "settings"
    const val OwnKeys = "settings/own-keys"
    const val ReachOutModel = "settings/reach-out-model"
    const val Memories = "settings/memories"
    const val Server = "settings/server"

    fun chat(characterId: String) = "chat/${Uri.encode(characterId)}"
}

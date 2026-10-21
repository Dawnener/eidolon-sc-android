package sc.eidolon.nativeapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import sc.eidolon.nativeapp.ui.EidolonApp
import sc.eidolon.nativeapp.ui.theme.EidolonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EidolonApplication).container
        handleIntent(intent)
        setContent {
            EidolonTheme {
                EidolonApp(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val characterId = intent?.getStringExtra(EXTRA_CHARACTER_ID) ?: return
        (application as EidolonApplication).container.pendingChat.value = characterId
    }

    companion object {
        const val EXTRA_CHARACTER_ID = "sc.eidolon.nativeapp.character_id"
        const val EXTRA_CONVERSATION_ID = "sc.eidolon.nativeapp.conversation_id"
    }
}

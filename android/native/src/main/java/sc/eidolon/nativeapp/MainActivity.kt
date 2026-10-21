package sc.eidolon.nativeapp

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
        setContent {
            EidolonTheme {
                EidolonApp(container)
            }
        }
    }
}

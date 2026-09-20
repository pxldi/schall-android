package io.github.pxldi.schall

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.pxldi.schall.ui.SchallRoot
import io.github.pxldi.schall.ui.theme.SchallTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    /** Fires once for every arrival through the schall://review link, so the
     * open app moves to Review rather than staying where it was. */
    private val reviewLinks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.data?.host == "review") reviewLinks.tryEmit(Unit)
        setContent {
            SchallTheme {
                SchallRoot(app = application as SchallApp, reviewLinks = reviewLinks)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.host == "review") reviewLinks.tryEmit(Unit)
    }
}

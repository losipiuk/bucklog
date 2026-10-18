package net.osipiuk.bucklog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.osipiuk.bucklog.domain.InviteLinks
import net.osipiuk.bucklog.ui.BucklogApp

class MainActivity : ComponentActivity() {
    private lateinit var platform: AndroidPlatform

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as BucklogApplication
        platform = AndroidPlatform(this, app.authorizer)
        handleIntent(intent)
        // Pick up family and hand-made changes whenever the app opens.
        SyncScheduler.syncNow(this)
        setContent {
            AndroidAppearance(app.graph.store) { dark, dynamic ->
                BucklogApp(app.graph, platform, dynamic, darkTheme = dark)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Picker results and invite links both arrive as net.osipiuk.bucklog:// intents. */
    private fun handleIntent(intent: Intent?) {
        platform.onIntent(intent)
        val uri = intent?.data ?: return
        if (uri.scheme != InviteLinks.APP_SCHEME || uri.host != InviteLinks.APP_HOST) return
        val invite = InviteLinks.parse(uri.toString()) ?: return
        lifecycleScope.launch { (application as BucklogApplication).graph.receiveInvite(invite) }
    }

    override fun onResume() {
        super.onResume()
        platform.onResume()
    }
}

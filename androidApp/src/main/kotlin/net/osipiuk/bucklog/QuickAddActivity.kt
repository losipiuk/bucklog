package net.osipiuk.bucklog

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import net.osipiuk.bucklog.ui.QuickAddApp

/**
 * The quick-add panel (SPEC §10): a translucent activity in its own task, so it floats over
 * whatever app is open and returns there when done. Started by [QuickAddTileService].
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as BucklogApplication
        val platform = AndroidPlatform(this, app.authorizer)
        setContent {
            val context = LocalContext.current
            val dynamic = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> null
                isSystemInDarkTheme() -> dynamicDarkColorScheme(context)
                else -> dynamicLightColorScheme(context)
            }
            QuickAddApp(
                app.graph,
                platform,
                dynamic,
                onDismiss = ::finish,
                onNeedsSetup = {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                },
            )
        }
    }
}

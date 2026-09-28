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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.osipiuk.bucklog.ui.BucklogApp

class MainActivity : ComponentActivity() {
    private lateinit var platform: AndroidPlatform

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as BucklogApplication
        platform = AndroidPlatform(this, app.authorizer)
        platform.onIntent(intent)
        lifecycleScope.launch {
            app.graph.store.config.first().accountEmail?.let { app.authorizer.accountEmail = it }
        }
        setContent {
            val context = LocalContext.current
            val dynamic = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> null
                isSystemInDarkTheme() -> dynamicDarkColorScheme(context)
                else -> dynamicLightColorScheme(context)
            }
            BucklogApp(app.graph, platform, dynamic)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        platform.onIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        platform.onResume()
    }
}

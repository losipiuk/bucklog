package net.osipiuk.bucklog

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.data.ThemeMode

/** Both entry points resolve one mode for Material You, refund colors and system bars. */
@Composable
internal fun ComponentActivity.AndroidAppearance(
    store: LocalStore,
    content: @Composable (dark: Boolean, dynamicScheme: ColorScheme?) -> Unit,
) {
    val modes = remember(store) { store.valueFlow(ThemeMode.KEY).map(ThemeMode::fromStored) }
    val mode by modes.collectAsState(initial = null)
    // Do not paint a system-themed frame before the persisted override has loaded.
    val current = mode ?: return
    val dark = current.isDark(isSystemInDarkTheme())
    val dynamic = remember(dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
        } else null
    }
    SideEffect {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { dark },
        )
    }
    content(dark, dynamic)
}

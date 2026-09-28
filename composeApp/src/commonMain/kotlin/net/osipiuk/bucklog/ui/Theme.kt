package net.osipiuk.bucklog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3A6A1F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAF294),
    onPrimaryContainer = Color(0xFF0B2000),
    secondaryContainer = Color(0xFFD9E7CB),
    surface = Color(0xFFF9FAEF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FD57B),
    onPrimary = Color(0xFF173800),
    primaryContainer = Color(0xFF235106),
    onPrimaryContainer = Color(0xFFBAF294),
    secondaryContainer = Color(0xFF3C4B33),
    surface = Color(0xFF11140E),
)

/** Colors the Material scheme doesn't have. */
data class ExtraColors(val refund: Color)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(refund = Color(0xFF2E7D32)) }

/** [dynamicScheme] comes from the platform (Material You on Android 12+); falls back to the Bucklog palette. */
@Composable
fun BucklogTheme(dynamicScheme: ColorScheme? = null, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val extra = ExtraColors(refund = if (dark) Color(0xFF81C784) else Color(0xFF2E7D32))
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = dynamicScheme ?: if (dark) DarkScheme else LightScheme, content = content)
    }
}

/** Tabular figures, so amounts don't jiggle while typing. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

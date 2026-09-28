package net.osipiuk.bucklog.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import net.osipiuk.bucklog.ui.PickedSheet

/**
 * Google Picker can't run in a WebView (it needs a Google web session, and Google blocks
 * sign-in in WebViews), so it runs on a hosted page (repo losipiuk/bucklog-picker) opened in
 * a Custom Tab, which shares Chrome's session. The page returns the pick via [RETURN_URL].
 */
object PickerLauncher {
    private const val PAGE_URL = "https://losipiuk.github.io/bucklog-picker/"
    private const val SCHEME = "net.osipiuk.bucklog"
    private const val RETURN_URL = "$SCHEME://picked"

    fun launch(context: Context, token: String, apiKey: String, appId: String, preselectFileId: String?) {
        val fragment = Uri.Builder()
            .appendQueryParameter("token", token)
            .appendQueryParameter("apiKey", apiKey)
            .appendQueryParameter("appId", appId)
            .appendQueryParameter("return", RETURN_URL)
            .apply { if (preselectFileId != null) appendQueryParameter("fileId", preselectFileId) }
            .build()
            .encodedQuery
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse("$PAGE_URL#$fragment"))
    }

    sealed interface Result {
        data class Picked(val sheet: PickedSheet) : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    /** Parses the page's return link; null when [uri] isn't one. */
    fun parseResult(uri: Uri?): Result? {
        if (uri == null || uri.scheme != SCHEME || uri.host != "picked") return null
        val id = uri.getQueryParameter("id")
        val error = uri.getQueryParameter("error")
        return when {
            id != null -> Result.Picked(PickedSheet(id, uri.getQueryParameter("name").orEmpty()))
            error != null -> Result.Failed(error)
            else -> Result.Cancelled
        }
    }
}

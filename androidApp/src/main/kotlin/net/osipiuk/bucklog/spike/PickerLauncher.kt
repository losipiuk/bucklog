package net.osipiuk.bucklog.spike

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Google Picker can't run in a WebView (it needs a Google web session, and Google blocks
 * sign-in in WebViews), so it runs on a hosted page opened in a Custom Tab, which shares
 * Chrome's session. The page returns the pick via [RETURN_URL].
 */
object PickerLauncher {
    private const val PAGE_URL = "https://losipiuk.github.io/bucklog-picker/"
    private const val RETURN_URL = "net.osipiuk.bucklog://picked"

    fun launch(context: Context, token: String, apiKey: String, appId: String, preselectFileId: String?) {
        val fragment = Uri.Builder()
            .appendQueryParameter("token", token)
            .appendQueryParameter("apiKey", apiKey)
            .appendQueryParameter("appId", appId)
            .appendQueryParameter("return", RETURN_URL)
            .apply { if (preselectFileId != null) appendQueryParameter("fileId", preselectFileId) }
            .build()
            .encodedQuery
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("$PAGE_URL#$fragment"))
    }

    fun parseResult(uri: Uri?): PickerResult? {
        if (uri == null || uri.scheme != "net.osipiuk.bucklog" || uri.host != "picked") return null
        val id = uri.getQueryParameter("id")
        return when {
            id != null -> PickerResult.Picked(id, uri.getQueryParameter("name").orEmpty())
            uri.getQueryParameter("error") != null -> PickerResult.Failed(uri.getQueryParameter("error")!!)
            else -> PickerResult.Cancelled
        }
    }
}

sealed interface PickerResult {
    data class Picked(val id: String, val name: String) : PickerResult
    data object Cancelled : PickerResult
    data class Failed(val message: String) : PickerResult
}

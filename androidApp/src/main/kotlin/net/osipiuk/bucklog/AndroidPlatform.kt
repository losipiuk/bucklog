package net.osipiuk.bucklog

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.icu.text.DecimalFormatSymbols
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CompletableDeferred
import net.osipiuk.bucklog.auth.GoogleAuthorizer
import net.osipiuk.bucklog.auth.PickerLauncher
import net.osipiuk.bucklog.ui.PickedSheet
import net.osipiuk.bucklog.ui.Platform
import java.util.Locale
import java.util.TimeZone

/**
 * Android side of [Platform], bound to [activity]. Must be created in onCreate (it registers
 * activity-result launchers). The activity handles config changes itself, so pending results
 * survive rotation.
 */
class AndroidPlatform(
    private val activity: ComponentActivity,
    private val authorizer: GoogleAuthorizer,
) : Platform {
    private var pendingResult: CompletableDeferred<ActivityResult>? = null
    private var pendingPick: CompletableDeferred<PickedSheet?>? = null
    private val main = Handler(Looper.getMainLooper())

    private val activityLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::deliver)
    private val intentSenderLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult(), ::deliver)

    private fun deliver(result: ActivityResult) {
        pendingResult?.complete(result)
        pendingResult = null
    }

    override suspend fun signIn(): String? {
        val chooser = AccountManager.newChooseAccountIntent(
            null, null, arrayOf("com.google"), null, null, null, null,
        )
        val chosen = awaitResult { activityLauncher.launch(chooser) }
        val email = chosen.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            ?.takeIf { chosen.resultCode == Activity.RESULT_OK } ?: return null
        when (val outcome = authorizer.authorize(email)) {
            is GoogleAuthorizer.Outcome.Authorized -> Unit
            is GoogleAuthorizer.Outcome.NeedsConsent -> {
                val consent = awaitResult {
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build())
                }
                if (consent.resultCode != Activity.RESULT_OK || authorizer.tokenFromConsent(consent.data) == null) return null
            }
        }
        authorizer.accountEmail = email
        return email
    }

    override suspend fun pickSheet(preselectFileId: String?): PickedSheet? {
        val deferred = CompletableDeferred<PickedSheet?>()
        pendingPick = deferred
        PickerLauncher.launch(
            activity,
            token = authorizer.accessToken(),
            apiKey = BuildConfig.PICKER_API_KEY,
            appId = BuildConfig.CLOUD_PROJECT_NUMBER,
            preselectFileId = preselectFileId,
        )
        return deferred.await()
    }

    /** Called from the activity for its launch and new intents. */
    fun onIntent(intent: Intent?) {
        when (val result = PickerLauncher.parseResult(intent?.data)) {
            null -> return
            is PickerLauncher.Result.Picked -> completePick(result.sheet)
            PickerLauncher.Result.Cancelled -> completePick(null)
            is PickerLauncher.Result.Failed -> pendingPick?.completeExceptionally(IllegalStateException(result.message))
        }
    }

    /** The user came back without picking (closed the Custom Tab): treat it as cancelled. */
    fun onResume() {
        val waiting = pendingPick ?: return
        main.postDelayed({ if (pendingPick === waiting) completePick(null) }, 800)
    }

    private fun completePick(sheet: PickedSheet?) {
        pendingPick?.complete(sheet)
        pendingPick = null
    }

    private suspend fun awaitResult(launch: () -> Unit): ActivityResult {
        val deferred = CompletableDeferred<ActivityResult>()
        pendingResult = deferred
        launch()
        return deferred.await()
    }

    override fun requestSync() = SyncScheduler.syncNow(activity)

    override fun openUrl(url: String) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    override fun finishWithMessage(message: String) {
        Toast.makeText(activity.applicationContext, message, Toast.LENGTH_SHORT).show()
        activity.finish()
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) =
        androidx.activity.compose.BackHandler(enabled, onBack)

    override val deviceCurrency: String
        get() = runCatching { java.util.Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "EUR"

    override val timeZoneId: String get() = TimeZone.getDefault().id

    override val isDebugBuild: Boolean get() = BuildConfig.DEBUG

    override val language: String get() = Locale.getDefault().language

    override val country: String get() = Locale.getDefault().country

    override val decimalSeparator: Char get() = DecimalFormatSymbols.getInstance().decimalSeparator
}

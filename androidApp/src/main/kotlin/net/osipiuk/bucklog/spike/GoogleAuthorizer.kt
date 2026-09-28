package net.osipiuk.bucklog.spike

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import net.osipiuk.bucklog.google.AccessTokenProvider
import net.osipiuk.bucklog.google.AuthRequiredException

const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/**
 * Access tokens via Google Identity Services. Tokens are cached by Play services, so calling
 * [accessToken] repeatedly is cheap. Consent needs an Activity: see [authorize].
 */
class GoogleAuthorizer(context: Context) : AccessTokenProvider {
    private val client = Identity.getAuthorizationClient(context)

    /** Google account email to authorize as; null lets Play services choose (asks when ambiguous). */
    var accountEmail: String? = null

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .apply { accountEmail?.let { setAccount(Account(it, "com.google")) } }
        .build()

    sealed interface Outcome {
        data class Authorized(val token: String) : Outcome
        data class NeedsConsent(val pendingIntent: PendingIntent) : Outcome
    }

    suspend fun authorize(): Outcome = client.authorize(request()).await().toOutcome()

    fun resultFromConsent(data: Intent?): String =
        client.getAuthorizationResultFromIntent(data).accessToken
            ?: throw AuthRequiredException("Consent finished without an access token")

    override suspend fun accessToken(): String =
        when (val outcome = authorize()) {
            is Outcome.Authorized -> outcome.token
            is Outcome.NeedsConsent -> throw AuthRequiredException("Google consent required")
        }

    override suspend fun invalidate(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }

    private fun AuthorizationResult.toOutcome(): Outcome {
        val pending = pendingIntent
        return if (hasResolution() && pending != null) {
            Outcome.NeedsConsent(pending)
        } else {
            Outcome.Authorized(accessToken ?: throw AuthRequiredException("No access token returned"))
        }
    }
}

package net.osipiuk.bucklog.auth

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import net.osipiuk.bucklog.google.AccessTokenProvider
import net.osipiuk.bucklog.google.AuthRequiredException

/**
 * Access tokens for the drive.file scope via Google Identity Services, for the account in
 * [accountEmail]. Tokens are cached by Play services, so [accessToken] is cheap to call.
 * Granting consent needs an Activity; see AndroidPlatform.signIn.
 */
class GoogleAuthorizer(context: Context) : AccessTokenProvider {
    private val client = Identity.getAuthorizationClient(context)

    @Volatile
    var accountEmail: String? = null

    sealed interface Outcome {
        data class Authorized(val token: String) : Outcome
        data class NeedsConsent(val pendingIntent: PendingIntent) : Outcome
    }

    suspend fun authorize(email: String): Outcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .setAccount(Account(email, "com.google"))
            .build()
        val result = client.authorize(request).await()
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) {
            Outcome.NeedsConsent(pending)
        } else {
            Outcome.Authorized(result.accessToken ?: throw AuthRequiredException("No access token returned"))
        }
    }

    fun tokenFromConsent(data: Intent?): String? = runCatching { client.getAuthorizationResultFromIntent(data).accessToken }.getOrNull()

    override suspend fun accessToken(): String {
        val email = accountEmail ?: throw AuthRequiredException("No Google account selected")
        return when (val outcome = authorize(email)) {
            is Outcome.Authorized -> outcome.token
            is Outcome.NeedsConsent -> throw AuthRequiredException("Google consent required")
        }
    }

    override suspend fun invalidate(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}

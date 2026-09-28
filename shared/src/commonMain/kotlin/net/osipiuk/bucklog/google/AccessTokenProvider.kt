package net.osipiuk.bucklog.google

/** Supplies OAuth access tokens for Google APIs. Implemented per platform. */
interface AccessTokenProvider {
    /** Returns a valid access token, or throws [AuthRequiredException] when user interaction is needed. */
    suspend fun accessToken(): String

    /** Drops a token Google rejected, so the next [accessToken] call fetches a fresh one. */
    suspend fun invalidate(token: String)
}

class AuthRequiredException(message: String) : Exception(message)

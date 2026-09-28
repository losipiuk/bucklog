package net.osipiuk.bucklog.google

import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val modifiedTime: String? = null,
    val version: String? = null,
)

@Serializable
data class DriveUser(val displayName: String? = null, val emailAddress: String? = null)

@Serializable
private data class About(val user: DriveUser)

/** The small part of Drive v3 we need: cheap change detection via file metadata. */
class DriveClient(private val api: GoogleApi) {
    suspend fun getFile(fileId: String): DriveFile =
        api.call {
            method = HttpMethod.Get
            url("$BASE/files/$fileId")
            parameter("fields", "id,name,modifiedTime,version")
        }.body()

    /** The signed-in user, as Google sees the access token. */
    suspend fun currentUser(): DriveUser =
        api.call {
            method = HttpMethod.Get
            url("$BASE/about")
            parameter("fields", "user(displayName,emailAddress)")
        }.body<About>().user

    private companion object {
        const val BASE = "https://www.googleapis.com/drive/v3"
    }
}

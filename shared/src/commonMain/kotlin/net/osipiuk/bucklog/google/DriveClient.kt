package net.osipiuk.bucklog.google

import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
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

@Serializable
private data class Permission(val role: String, val type: String, val emailAddress: String)

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

    /**
     * Shares [fileId] with [email] as an editor; Google emails them (with [message]).
     * Works under drive.file for files the app has access to.
     */
    suspend fun shareWith(fileId: String, email: String, message: String) {
        api.call {
            method = HttpMethod.Post
            url("$BASE/files/$fileId/permissions")
            parameter("sendNotificationEmail", true)
            parameter("emailMessage", message)
            parameter("fields", "id")
            contentType(ContentType.Application.Json)
            setBody(Permission(role = "writer", type = "user", emailAddress = email))
        }
    }

        private companion object {
        const val BASE = "https://www.googleapis.com/drive/v3"
    }
}

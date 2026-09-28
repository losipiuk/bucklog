package net.osipiuk.bucklog.google

class GoogleApiException(
    val httpStatus: Int,
    val status: String?,
    message: String,
) : Exception("HTTP $httpStatus${status?.let { " $it" } ?: ""}: $message")

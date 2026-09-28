package net.osipiuk.bucklog.ui

import net.osipiuk.bucklog.google.AuthRequiredException
import net.osipiuk.bucklog.google.GoogleApiException

/** Turns failures into something a family member can act on. */
fun Throwable.userMessage(): String = when (this) {
    is AuthRequiredException -> "Google sign-in is needed. Please sign in again."
    is GoogleApiException -> when (httpStatus) {
        403, 404 -> "Can't open this sheet. Make sure it's shared with you and pick it again."
        429 -> "Google is busy. Try again in a minute."
        else -> "Google error ($httpStatus): $message"
    }
    else -> message?.let { "Something went wrong: $it" } ?: "Something went wrong."
}

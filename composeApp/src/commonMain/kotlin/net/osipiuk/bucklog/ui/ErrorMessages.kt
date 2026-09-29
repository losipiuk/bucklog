package net.osipiuk.bucklog.ui

import kotlinx.io.IOException
import net.osipiuk.bucklog.google.AuthRequiredException
import net.osipiuk.bucklog.google.GoogleApiException

/** Turns failures into something a family member can act on. */
fun Throwable.userMessage(s: Strings): String = when (this) {
    is AuthRequiredException -> s.errSignIn
    is GoogleApiException -> when (httpStatus) {
        401 -> s.errSignIn
        403, 404 -> s.errSheetAccess
        429 -> s.errBusy
        else -> s.errGoogle(httpStatus, message.orEmpty())
    }
    is IOException -> s.errOffline
    else -> s.errGeneric(message)
}

package net.osipiuk.bucklog.ui

/** Device-local option; absent or unrecognized values preserve the original behavior. */
const val CLOSE_AFTER_ADD_KEY = "ui_close_after_add"
internal fun parseCloseAfterAdd(value: String?): Boolean = value != "false"

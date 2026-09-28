package net.osipiuk.bucklog.domain

import kotlin.random.Random
import kotlin.time.Instant

enum class EntryStatus { PENDING, SYNCED, INVALID }

/** One expense (or refund, when [amountMinor] is negative). */
data class Entry(
    val id: String,
    val timestamp: Instant,
    val who: String,
    val what: String,
    val category: String,
    val amountMinor: Long,
    val currency: String,
    /** Exchange rate to the main currency as a decimal string; null for the main currency or not fetched yet. */
    val rate: String?,
    val status: EntryStatus,
) {
    val isRefund: Boolean get() = amountMinor < 0
}

data class Category(val name: String, val emoji: String?, val archived: Boolean = false)

private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

/** Short random row ID (8 base32 chars, ~10^12 values) used to match rows between app and sheet. */
fun newEntryId(random: Random = Random.Default): String =
    String(CharArray(8) { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] })

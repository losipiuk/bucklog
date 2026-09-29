package net.osipiuk.bucklog.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

// English for now; Polish UI strings come in M4.
private val months = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun LocalDate.dayLabel(today: LocalDate): String = when (this) {
    today -> "Today"
    LocalDate.fromEpochDays(today.toEpochDays() - 1) -> "Yesterday"
    else -> "${weekdays[dayOfWeek.ordinal]}, $day ${months[month.ordinal].take(3)}" + if (year != today.year) " $year" else ""
}

fun LocalDate.monthLabel(): String = "${months[month.ordinal]} $year"

fun LocalTime.label(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

fun LocalDateTime.shortLabel(): String = "$day ${months[month.ordinal].take(3)} ${time.label()}"

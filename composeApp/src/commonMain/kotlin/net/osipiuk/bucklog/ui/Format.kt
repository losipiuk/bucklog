package net.osipiuk.bucklog.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Day header: weekday or Today/Yesterday, then the full date (DD.MM.YYYY). */
fun LocalDate.dayLabel(today: LocalDate, s: Strings): String {
    val name = when (this) {
        today -> s.today
        LocalDate.fromEpochDays(today.toEpochDays() - 1) -> s.yesterday
        else -> s.weekdays[dayOfWeek.ordinal]
    }
    return "$name ${label()}"
}

fun LocalDate.monthLabel(s: Strings): String = "${s.months[month.ordinal]} $year"

/** DD.MM.YYYY, zero-padded. */
fun LocalDate.label(): String = "${day.toString().padStart(2, '0')}.${(month.ordinal + 1).toString().padStart(2, '0')}.$year"

fun LocalTime.label(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** DD.MM.YYYY HH:mm */
fun LocalDateTime.shortLabel(): String = "${date.label()} ${time.label()}"

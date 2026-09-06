package com.edu.quickaside.ui.memory

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class NoteTimestampFormatter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
    private val clock: Clock = Clock.system(zoneId),
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", locale)

    fun format(instant: Instant): String {
        val localDateTime = instant.atZone(zoneId)
        val today = LocalDate.now(clock.withZone(zoneId))
        val dayLabel = when (localDateTime.toLocalDate()) {
            today -> "Hoy"
            today.minusDays(1) -> "Ayer"
            else -> dateFormatter.format(localDateTime)
        }

        return "$dayLabel, ${timeFormatter.format(localDateTime)}"
    }
}

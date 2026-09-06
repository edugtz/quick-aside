package com.edu.quickaside.ui.lists

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats a session timestamp for the user's local timezone without affecting ordering. */
class MandadoHistoryTimestampFormatter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu · HH:mm", locale)

    fun format(instant: Instant): String = dateTimeFormatter.format(instant.atZone(zoneId))
}

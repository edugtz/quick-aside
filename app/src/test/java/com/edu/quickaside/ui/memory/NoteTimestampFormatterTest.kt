package com.edu.quickaside.ui.memory

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTimestampFormatterTest {
    private val zoneId = ZoneId.of("America/Mexico_City")
    private val now = Instant.parse("2026-09-03T18:00:00Z")
    private val formatter = NoteTimestampFormatter(
        zoneId = zoneId,
        locale = Locale.ENGLISH,
        clock = Clock.fixed(now, zoneId),
    )

    @Test
    fun formatsNoteOnCurrentLocalDateAsToday() {
        assertEquals("Hoy, 12:00", formatter.format(now))
    }

    @Test
    fun formatsNoteOnPreviousLocalDateAsYesterday() {
        val yesterday = Instant.parse("2026-09-03T02:10:00Z")

        assertEquals("Ayer, 20:10", formatter.format(yesterday))
    }

    @Test
    fun convertsInstantToInjectedLocalTimezoneForOlderDates() {
        val olderNote = Instant.parse("2026-09-01T22:42:00Z")

        assertEquals("1 Sep, 16:42", formatter.format(olderNote))
    }
}

package com.edu.quickaside.ui.memory

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureTimestampFormatterTest {
    private val zoneId = ZoneId.of("America/Mexico_City")
    private val now = Instant.parse("2026-09-03T18:00:00Z")
    private val formatter = CaptureTimestampFormatter(
        zoneId = zoneId,
        locale = Locale.ENGLISH,
        clock = Clock.fixed(now, zoneId),
    )

    @Test
    fun formatsCaptureOnCurrentLocalDateAsToday() {
        assertEquals("Hoy, 12:00", formatter.format(now))
    }

    @Test
    fun formatsCaptureOnPreviousLocalDateAsYesterday() {
        val yesterday = Instant.parse("2026-09-03T02:10:00Z")

        assertEquals("Ayer, 20:10", formatter.format(yesterday))
    }

    @Test
    fun convertsInstantToInjectedLocalTimezoneForOlderDates() {
        val olderCapture = Instant.parse("2026-09-01T22:42:00Z")

        assertEquals("1 Sep, 16:42", formatter.format(olderCapture))
    }
}

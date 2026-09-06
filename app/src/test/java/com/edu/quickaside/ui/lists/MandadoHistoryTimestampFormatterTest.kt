package com.edu.quickaside.ui.lists

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MandadoHistoryTimestampFormatterTest {
    @Test
    fun formatsInTheInjectedLocalTimezoneAndLocale() {
        val formatter = MandadoHistoryTimestampFormatter(
            zoneId = ZoneId.of("America/Mexico_City"),
            locale = Locale.ENGLISH,
        )

        assertEquals(
            "5 Sep 2026 · 18:20",
            formatter.format(Instant.parse("2026-09-06T00:20:00Z")),
        )
    }

    @Test
    fun formatsSpanishMonthNamesForTheSpanishLocale() {
        val formatter = MandadoHistoryTimestampFormatter(
            zoneId = ZoneId.of("UTC"),
            locale = Locale.forLanguageTag("es-MX"),
        )

        assertEquals(
            "5 sep 2026 · 18:20",
            formatter.format(Instant.parse("2026-09-05T18:20:00Z")),
        )
    }
}

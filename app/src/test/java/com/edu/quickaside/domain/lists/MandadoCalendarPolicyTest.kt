package com.edu.quickaside.domain.lists

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MandadoCalendarPolicyTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val policy = MandadoCalendarPolicy(
        clock = Clock.fixed(local("2026-10-03T13:59:59"), zone),
        zoneId = zone,
    )

    @Test
    fun fridayBelongsToTheFormalSundayPeriod() {
        val period = policy.periodFor(local("2026-10-02T18:00:00"))

        assertEquals(LocalDate.of(2026, 9, 27), period.startDate)
        assertEquals(local("2026-10-03T14:00:00"), period.cutoff)
    }

    @Test
    fun saturdayBeforeCutoffRemainsInTheEndingPeriod() {
        assertEquals(
            LocalDate.of(2026, 9, 27),
            policy.periodFor(local("2026-10-03T13:59:59")).startDate,
        )
    }

    @Test
    fun exactCutoffImmediatelyAdvancesToTheFollowingSundayPeriod() {
        val period = policy.periodFor(local("2026-10-03T14:00:00"))

        assertEquals(LocalDate.of(2026, 10, 4), period.startDate)
        assertEquals(local("2026-10-10T14:00:00"), period.cutoff)
    }

    @Test
    fun saturdayAfterCutoffAndFollowingSundayShareOnePeriod() {
        val saturday = policy.periodFor(local("2026-10-03T18:00:00"))
        val sunday = policy.periodFor(local("2026-10-04T00:00:00"))

        assertEquals(LocalDate.of(2026, 10, 4), saturday.startDate)
        assertEquals(saturday, sunday)
    }

    @Test
    fun injectedZoneMakesTheBoundaryDeterministic() {
        val instant = Instant.parse("2026-10-03T19:59:59Z")

        assertEquals(
            LocalDate.of(2026, 9, 27),
            policy.periodFor(instant).startDate,
        )
        assertEquals(
            LocalDate.of(2026, 10, 4),
            policy.periodFor(instant.plusSeconds(1)).startDate,
        )
    }

    @Test
    fun sessionHelpersDistinguishTheCutoffBoundary() {
        val session = ListSession(
            id = com.edu.quickaside.domain.common.ListSessionId("session"),
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            startedAt = local("2026-09-28T09:00:00"),
        )

        assertTrue(policy.sessionBelongsToCurrentPeriod(session, local("2026-10-03T13:59:59")))
        assertFalse(policy.sessionBelongsToCurrentPeriod(session, local("2026-10-03T14:00:00")))
        assertFalse(policy.boundaryHasBeenCrossed(session, local("2026-10-03T13:59:59")))
        assertTrue(policy.boundaryHasBeenCrossed(session, local("2026-10-03T14:00:00")))
    }

    private fun local(value: String): Instant =
        java.time.LocalDateTime.parse(value).atZone(zone).toInstant()
}

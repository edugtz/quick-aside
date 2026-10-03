package com.edu.quickaside.domain.lists

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Deterministic calendar rules for the weekly Mandado lifecycle.
 *
 * A period is identified by its formal Sunday start date. The Saturday 14:00
 * cutoff advances the identity to the following Sunday immediately, so the
 * Saturday afternoon staging window belongs to the upcoming period.
 */
class MandadoCalendarPolicy(
    private val clock: Clock = Clock.systemDefaultZone(),
    val zoneId: ZoneId = clock.zone,
) {
    fun now(): Instant = clock.instant()

    fun currentPeriod(at: Instant = now()): MandadoPeriod = periodFor(at)

    fun periodFor(at: Instant): MandadoPeriod {
        val localDateTime = at.atZone(zoneId).toLocalDateTime()
        val periodStartDate = if (
            localDateTime.dayOfWeek == DayOfWeek.SATURDAY &&
            !localDateTime.toLocalTime().isBefore(SATURDAY_CUTOFF)
        ) {
            localDateTime.toLocalDate().plusDays(1)
        } else {
            localDateTime.toLocalDate().minusDays(
                localDateTime.dayOfWeek.value.toLong() % DAYS_IN_WEEK,
            )
        }
        return MandadoPeriod(
            startDate = periodStartDate,
            cutoff = cutoffForStartDate(periodStartDate),
        )
    }

    fun cutoffFor(period: MandadoPeriod): Instant = period.cutoff

    fun sessionPeriod(session: ListSession): MandadoPeriod = periodFor(session.startedAt)

    fun sessionBelongsToCurrentPeriod(
        session: ListSession,
        at: Instant = now(),
    ): Boolean = sessionPeriod(session).startDate == currentPeriod(at).startDate

    fun boundaryHasBeenCrossed(
        session: ListSession,
        at: Instant = now(),
    ): Boolean = !at.isBefore(cutoffFor(sessionPeriod(session)))

    private fun cutoffForStartDate(periodStartDate: LocalDate): Instant = periodStartDate
        .plusDays(DAYS_IN_WEEK - 1)
        .atTime(SATURDAY_CUTOFF)
        .atZone(zoneId)
        .toInstant()

    companion object {
        val SATURDAY_CUTOFF: LocalTime = LocalTime.of(14, 0)
        const val DAYS_IN_WEEK: Long = 7
    }
}

data class MandadoPeriod(
    val startDate: LocalDate,
    val cutoff: Instant,
)

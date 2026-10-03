package com.edu.quickaside.data.local

import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.MandadoCalendarPolicy
import com.edu.quickaside.domain.lists.MandadoPeriod
import java.time.Instant

/** Result of reconciling the persisted Mandado session against a calendar instant. */
internal data class MandadoSessionReconciliation(
    val currentPeriod: MandadoPeriod,
    val activeSession: ListSessionEntity?,
)

/**
 * Closes an active Mandado from an earlier period at that period's exact
 * calendar cutoff. It never creates a replacement session.
 *
 * The caller must already be inside a Room write transaction.
 */
internal suspend fun QuickAsideDatabase.reconcileMandadoSession(
    policy: MandadoCalendarPolicy,
    at: Instant,
): MandadoSessionReconciliation {
    val currentPeriod = policy.currentPeriod(at)
    val active = listSessionDao()
        .getActiveByDefinitionId(BuiltInListDefinitions.MANDADO.id.value)
    if (active == null) {
        return MandadoSessionReconciliation(
            currentPeriod = currentPeriod,
            activeSession = null,
        )
    }

    val activeSession = active.toDomain()
    val activePeriod = policy.sessionPeriod(activeSession)
    if (activePeriod.startDate.isBefore(currentPeriod.startDate)) {
        check(
            listSessionDao().finishActive(
                id = active.id,
                endedAtEpochMillis = policy.cutoffFor(activePeriod).toEpochMilli(),
            ) == 1,
        ) {
            "Expected exactly one Mandado calendar rollover finish update"
        }
        return MandadoSessionReconciliation(
            currentPeriod = currentPeriod,
            activeSession = null,
        )
    }

    return MandadoSessionReconciliation(
        currentPeriod = currentPeriod,
        activeSession = active,
    )
}

/**
 * Reconciles the Mandado calendar at [at] and reports whether [listSessionId]
 * still identifies the active Mandado session afterward. A null or historical
 * session can never accept an item mutation.
 *
 * The caller must already be inside a Room write transaction.
 */
internal suspend fun QuickAsideDatabase.mandadoSessionRemainsActive(
    listSessionId: String?,
    policy: MandadoCalendarPolicy,
    at: Instant,
): Boolean {
    val reconciliation = reconcileMandadoSession(policy, at)
    return listSessionId != null && reconciliation.activeSession?.id == listSessionId
}

/**
 * Manual Finish is represented by the latest ended session in the current
 * logical period. Calendar rollover makes that historical session stop
 * blocking bootstrap without requiring a schema flag.
 */
internal suspend fun QuickAsideDatabase.isMandadoPeriodClosed(
    policy: MandadoCalendarPolicy,
    currentPeriod: MandadoPeriod,
): Boolean {
    val latest = listSessionDao()
        .getByDefinitionId(BuiltInListDefinitions.MANDADO.id.value)
        .firstOrNull()
        ?: return false
    if (latest.endedAtEpochMillis == null) return false
    return policy.sessionPeriod(latest.toDomain()).startDate == currentPeriod.startDate
}

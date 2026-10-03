package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CapturePlanListExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanListExecutionResult
import com.edu.quickaside.application.lists.CreateListItemActionResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListIdProvider
import com.edu.quickaside.application.lists.ListItemIdProvider
import com.edu.quickaside.application.lists.SessionFinishResult
import com.edu.quickaside.application.lists.SessionStartResult
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.MandadoCalendarPolicy
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MandadoCalendarLifecycleDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "mandado-calendar-lifecycle-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun passiveReadClosesOldActiveAtExactCutoffWithoutMaterializingNextSession() = runBlocking {
        val oldStartedAt = local("2026-09-27T09:00:00")
        val cutoff = local("2026-10-03T14:00:00")
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "old-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = oldStartedAt.toEpochMilli(),
                lastActivityAtEpochMillis = oldStartedAt.toEpochMilli(),
            ),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "old-item",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                listSessionId = "old-session",
                text = "Leche",
                isCompleted = false,
                createdAtEpochMillis = oldStartedAt.toEpochMilli(),
            ),
        )
        val store = RoomListStore(
            database = database,
            mandadoCalendarPolicy = policy(cutoff),
        )

        assertNull(store.getActiveSession(BuiltInListDefinitions.MANDADO.id))
        val firstRead = store.readRecentSessions(BuiltInListDefinitions.MANDADO.id)
        val secondRead = store.readRecentSessions(BuiltInListDefinitions.MANDADO.id)

        assertEquals(firstRead, secondRead)
        assertEquals(listOf(ListSessionId("old-session")), firstRead.map { it.session.id })
        assertEquals(
            cutoff,
            database.listSessionDao().getById("old-session")?.endedAtEpochMillis
                ?.let(Instant::ofEpochMilli),
        )
        assertEquals(1, database.listSessionDao().getByDefinitionId("mandado").size)
        assertEquals(listOf("old-item"), firstRead.single().items.map { it.id.value })
    }

    @Test
    fun manualFinishClosesPeriodAndBlocksCaptureAndStartUntilNextCutoff() = runBlocking {
        val startedAt = local("2026-10-02T10:00:00")
        val finishedAt = local("2026-10-02T11:00:00")
        val afterFinish = local("2026-10-02T12:00:00")
        val beforeNextCutoff = local("2026-10-03T13:59:59")
        val nextCutoff = local("2026-10-03T14:00:00")
        val clock = MutableCalendarClock(startedAt, zone)
        val calendarPolicy = MandadoCalendarPolicy(clock = clock, zoneId = zone)
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider("manual-session", "next-period-session"),
            clock = clock,
            mandadoCalendarPolicy = calendarPolicy,
        )

        val created = store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created
        clock.current = finishedAt
        val finished = store.finishActiveSession(BuiltInListDefinitions.MANDADO.id)
            as SessionFinishResult.Finished

        assertEquals(created.session.id, finished.session.id)
        assertEquals(finishedAt, finished.session.endedAt)

        clock.current = afterFinish
        assertEquals(
            SessionStartResult.PeriodClosed,
            store.startSession(BuiltInListDefinitions.MANDADO.id),
        )

        database.captureDao().insert(
            CaptureEntity(
                id = "closed-period-capture",
                kind = "TEXT",
                originalText = "Agrega aguacate",
                capturedAtEpochMillis = afterFinish.toEpochMilli(),
            ),
        )
        val rejected = RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = ListItemIdProvider { ListItemId("must-not-be-created") },
            clock = ListClock { afterFinish },
            sessionIdProvider = { ListSessionId("must-not-be-created") },
            mandadoCalendarPolicy = policy(afterFinish),
        ).execute(
            CapturePlan(
                sourceCaptureId = CaptureId("closed-period-capture"),
                actions = listOf(
                    CapturePlanAction.AddListItem(
                        BuiltInListDefinitions.MANDADO.id,
                        "aguacate",
                    ),
                ),
            ),
        )

        assertEquals(
            CapturePlanListExecutionResult.Rejected(
                actionIndex = 0,
                reason = CapturePlanListExecutionRejectionReason.MANDADO_PERIOD_CLOSED,
            ),
            rejected,
        )
        assertTrue(database.listItemDao().getBySessionId("manual-session").isEmpty())
        assertEquals(1, database.listSessionDao().getByDefinitionId("mandado").size)

        clock.current = beforeNextCutoff
        assertEquals(
            SessionStartResult.PeriodClosed,
            store.startSession(BuiltInListDefinitions.MANDADO.id),
        )
        assertEquals(1, database.listSessionDao().getByDefinitionId("mandado").size)

        clock.current = nextCutoff
        val next = store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created
        assertEquals(ListSessionId("next-period-session"), next.session.id)
        assertEquals(nextCutoff, next.session.startedAt)
        assertEquals(2, database.listSessionDao().getByDefinitionId("mandado").size)

        assertEquals(
            SessionStartResult.Existing(next.session),
            store.startSession(BuiltInListDefinitions.MANDADO.id),
        )
        assertEquals(2, database.listSessionDao().getByDefinitionId("mandado").size)
    }

    @Test
    fun cutoffRolloverClosesOldSessionOnceAndReusesNewPeriodThroughSunday() = runBlocking {
        val oldStartedAt = local("2026-09-27T09:00:00")
        val cutoff = local("2026-10-03T14:00:00")
        val sunday = local("2026-10-04T00:00:00")
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "old-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = oldStartedAt.toEpochMilli(),
                lastActivityAtEpochMillis = oldStartedAt.toEpochMilli(),
            ),
        )
        database.captureDao().insert(
            CaptureEntity(
                id = "rollover-capture",
                kind = "TEXT",
                originalText = "Mandado",
                capturedAtEpochMillis = cutoff.toEpochMilli(),
            ),
        )

        val first = executeMandado(
            occurredAt = cutoff,
            itemId = "next-item-1",
            ledgerId = "next-ledger-1",
            sessionId = "next-session",
        )
        assertEquals(ListSessionId("next-session"), first.autoCreatedMandadoSessionId)
        assertEquals(
            cutoff,
            database.listSessionDao().getById("old-session")?.endedAtEpochMillis
                ?.let(Instant::ofEpochMilli),
        )

        val second = executeMandado(
            occurredAt = sunday,
            itemId = "next-item-2",
            ledgerId = "next-ledger-2",
            sessionId = "must-not-be-created",
        )
        assertNull(second.autoCreatedMandadoSessionId)
        assertEquals(
            listOf("next-session"),
            listOfNotNull(database.listItemDao().getById("next-item-2")?.listSessionId),
        )
        assertEquals(2, database.listSessionDao().getByDefinitionId("mandado").size)
        assertNull(database.listSessionDao().getById("next-session")?.endedAtEpochMillis)
        assertEquals(
            cutoff,
            database.listSessionDao().getById("old-session")?.endedAtEpochMillis
                ?.let(Instant::ofEpochMilli),
        )
    }

    @Test
    fun rolloverFailureRollsBackOldFinishNewSessionAndItemsTogether() = runBlocking {
        val oldStartedAt = local("2026-09-27T09:00:00")
        val cutoff = local("2026-10-03T14:00:00")
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "old-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = oldStartedAt.toEpochMilli(),
                lastActivityAtEpochMillis = oldStartedAt.toEpochMilli(),
            ),
        )
        database.captureDao().insert(
            CaptureEntity(
                id = "failed-rollover-capture",
                kind = "TEXT",
                originalText = "Mandado",
                capturedAtEpochMillis = cutoff.toEpochMilli(),
            ),
        )
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = "rollover-failure-ledger",
                occurredAtEpochMillis = cutoff.toEpochMilli(),
            ),
        )

        val result = RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = ListItemIdProvider { ListItemId("rolled-back-item") },
            actionLedgerIdProvider = object : com.edu.quickaside.application.actions.ActionLedgerIdProvider {
                override fun nextEntryId() =
                    com.edu.quickaside.domain.common.ActionLedgerEntryId("rollover-failure-ledger")
            },
            clock = ListClock { cutoff },
            sessionIdProvider = { ListSessionId("rolled-back-session") },
            mandadoCalendarPolicy = policy(cutoff),
        ).execute(
            CapturePlan(
                sourceCaptureId = CaptureId("failed-rollover-capture"),
                actions = listOf(
                    CapturePlanAction.AddListItem(
                        BuiltInListDefinitions.MANDADO.id,
                        "producto",
                    ),
                ),
            ),
        )

        assertTrue(result is CapturePlanListExecutionResult.Failed)
        assertNull(database.listSessionDao().getById("rolled-back-session"))
        assertNull(database.listItemDao().getById("rolled-back-item"))
        assertNull(database.listSessionDao().getById("old-session")?.endedAtEpochMillis)
        assertEquals(1, database.listSessionDao().getByDefinitionId("mandado").size)
        assertEquals(1, database.actionLedgerEntryDao().getRecent(50).size)
    }

    @Test
    fun manualCreateAtCutoffCannotWriteIntoTheClosedOldSession() = runBlocking {
        val oldStartedAt = local("2026-09-27T09:00:00")
        val cutoff = local("2026-10-03T14:00:00")
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "old-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = oldStartedAt.toEpochMilli(),
                lastActivityAtEpochMillis = oldStartedAt.toEpochMilli(),
            ),
        )

        val result = RoomReversibleListItemActions(
            database = database,
            clock = ListClock { cutoff },
            mandadoCalendarPolicy = policy(cutoff),
        ).create(
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "No debe entrar al periodo anterior",
            listSessionId = ListSessionId("old-session"),
        )

        assertEquals(CreateListItemActionResult.SessionNotActive, result)
        assertEquals(
            cutoff,
            database.listSessionDao().getById("old-session")?.endedAtEpochMillis
                ?.let(Instant::ofEpochMilli),
        )
        assertTrue(database.listItemDao().getBySessionId("old-session").isEmpty())
    }

    @Test
    fun completionAndToggleRejectAfterCutoffWithoutTouchingHistoricalSession() = runBlocking {
        val oldStartedAt = local("2026-09-27T09:00:00")
        val cutoff = local("2026-10-03T14:00:00")
        val afterCutoff = local("2026-10-04T00:00:00")
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "old-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = oldStartedAt.toEpochMilli(),
                lastActivityAtEpochMillis = oldStartedAt.toEpochMilli(),
            ),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "old-item",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                listSessionId = "old-session",
                text = "Leche",
                isCompleted = false,
                createdAtEpochMillis = oldStartedAt.toEpochMilli(),
            ),
        )
        val clock = MutableCalendarClock(cutoff, zone)
        val store = RoomListStore(
            database = database,
            clock = clock,
            mandadoCalendarPolicy = MandadoCalendarPolicy(clock = clock, zoneId = zone),
        )

        assertEquals(
            ItemCompletionResult.SessionNotActive,
            store.setItemCompleted(ListItemId("old-item"), true),
        )
        assertEquals(
            cutoff,
            database.listSessionDao().getById("old-session")?.endedAtEpochMillis
                ?.let(Instant::ofEpochMilli),
        )

        clock.current = afterCutoff
        assertEquals(
            ItemCompletionResult.SessionNotActive,
            store.toggleItemCompleted(ListItemId("old-item")),
        )

        assertEquals(false, database.listItemDao().getById("old-item")?.isCompleted)
        assertEquals(
            oldStartedAt.toEpochMilli(),
            database.listSessionDao().getById("old-session")?.lastActivityAtEpochMillis,
        )
        assertEquals(1, database.listSessionDao().getByDefinitionId("mandado").size)
        val history = store.readRecentSessions(BuiltInListDefinitions.MANDADO.id)
        assertEquals(listOf(ListSessionId("old-session")), history.map { it.session.id })
        assertEquals(cutoff, history.single().session.endedAt)
        assertEquals(listOf(ListItemId("old-item")), history.single().items.map { it.id })
        assertFalse(history.single().items.single().isCompleted)
    }

    private suspend fun executeMandado(
        occurredAt: Instant,
        itemId: String,
        ledgerId: String,
        sessionId: String,
    ): CapturePlanListExecutionResult.Executed =
        RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = ListItemIdProvider { ListItemId(itemId) },
            actionLedgerIdProvider = object : com.edu.quickaside.application.actions.ActionLedgerIdProvider {
                override fun nextEntryId() = com.edu.quickaside.domain.common.ActionLedgerEntryId(ledgerId)
            },
            clock = ListClock { occurredAt },
            sessionIdProvider = { ListSessionId(sessionId) },
            mandadoCalendarPolicy = policy(occurredAt),
        ).execute(
            CapturePlan(
                sourceCaptureId = CaptureId("rollover-capture"),
                actions = listOf(
                    CapturePlanAction.AddListItem(
                        BuiltInListDefinitions.MANDADO.id,
                        "producto",
                    ),
                ),
            ),
        ) as CapturePlanListExecutionResult.Executed

    private fun policy(at: Instant): MandadoCalendarPolicy = MandadoCalendarPolicy(
        clock = Clock.fixed(at, zone),
        zoneId = zone,
    )

    private fun local(value: String): Instant =
        LocalDateTime.parse(value).atZone(zone).toInstant()

    private class MutableCalendarClock(
        var current: Instant,
        private val currentZone: ZoneId,
    ) : Clock(), ListClock {
        override fun getZone(): ZoneId = currentZone

        override fun withZone(zone: ZoneId): Clock = MutableCalendarClock(current, zone)

        override fun instant(): Instant = current

        override fun now(): Instant = current
    }

    private class QueueListIdProvider(vararg sessionIds: String) : ListIdProvider {
        private val ids = ArrayDeque(sessionIds.toList())

        override fun nextSessionId(): ListSessionId = ListSessionId(ids.removeFirst())

        override fun nextItemId(): ListItemId = ListItemId("unused-item")
    }

    private val zone: ZoneId = ZoneId.of("America/Mexico_City")
}

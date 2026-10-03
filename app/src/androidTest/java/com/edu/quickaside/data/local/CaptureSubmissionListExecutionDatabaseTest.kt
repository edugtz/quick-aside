package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.capture.CaptureExecutionOutcome
import com.edu.quickaside.application.capture.CaptureInterpretationResult
import com.edu.quickaside.application.capture.CaptureInterpreter
import com.edu.quickaside.application.capture.CapturePlanListExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanListExecutionResult
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureSubmissionResult
import com.edu.quickaside.application.capture.UndoCapturePlanListExecutionResult
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListItemIdProvider
import com.edu.quickaside.application.lists.ListSessionIdProvider
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureSubmissionListExecutionDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-028-capture-pipeline-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun eligibleCapturePersistsCaptureItemsOneSourceLinkedLedgerAndExactReceipt() = runBlocking {
        val captureId = CaptureId("pipeline-success")
        val executor = executor(
            itemIds = listOf("item-leche", "item-pan"),
            entryIds = listOf("ledger-success"),
            times = listOf(Instant.parse("2026-09-19T20:01:00Z")),
        )
        val submission = submission(
            captureId = captureId,
            actions = listOf(compras("leche"), compras("pan")),
            executor = executor,
        )

        val saved = submission.submit("Compra leche y pan") as CaptureSubmissionResult.Saved
        val receipt = (saved.execution as CaptureExecutionOutcome.Executed.ListItems).receipt

        assertNotNull(database.captureDao().getById(captureId.value))
        assertEquals(listOf("item-leche", "item-pan"), receipt.items.map { it.id.value })
        assertEquals(
            receipt.items,
            database.listItemDao()
                .getContinuousByDefinitionId(BuiltInListDefinitions.COMPRAS.id.value)
                .map(ListItemEntity::toDomain),
        )
        val ledger = database.actionLedgerEntryDao().getById("ledger-success")
        assertEquals(captureId.value, ledger?.sourceCaptureId)
        assertEquals(ActionLedgerEntryId("ledger-success"), receipt.actionLedgerEntryId)
        assertEquals(
            receipt.items.map { it.id.value },
            database.actionLedgerMutationDao().getByEntryId("ledger-success").map { it.targetId },
        )
    }

    @Test
    fun mixedUnsupportedPlanPersistsCaptureAndAddsNoExecutionRows() = runBlocking {
        val captureId = CaptureId("pipeline-mixed")
        val executor = executor()
        val submission = submission(
            captureId = captureId,
            actions = listOf(
                compras("leche"),
                CapturePlanAction.CreateTask(TaskSpace.PERSONAL, "Pagar luz"),
            ),
            executor = executor,
        )

        val saved = submission.submit("Compra leche y paga la luz") as CaptureSubmissionResult.Saved

        assertEquals(CaptureExecutionOutcome.NotEligible, saved.execution)
        assertNotNull(database.captureDao().getById(captureId.value))
        assertTrue(
            database.listItemDao()
                .getContinuousByDefinitionId(BuiltInListDefinitions.COMPRAS.id.value)
                .isEmpty(),
        )
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
    }

    @Test
    fun mandadoWithoutActiveSessionBootstrapsOneSessionItemsAndLedger() = runBlocking {
        val captureId = CaptureId("pipeline-mandado-bootstrap")
        val occurredAt = Instant.parse("2026-09-19T20:05:00Z")
        val executor = executor(
            itemIds = listOf("mandado-bootstrap-item"),
            entryIds = listOf("mandado-bootstrap-ledger"),
            times = listOf(occurredAt),
            sessionIds = listOf("mandado-bootstrap-session"),
        )
        val submission = submission(
            captureId = captureId,
            actions = listOf(mandado("aguacate")),
            executor = executor,
        )

        val saved = submission.submitVoice("Agrega aguacate al mandado") as
            CaptureSubmissionResult.Saved
        val receipt = (saved.execution as CaptureExecutionOutcome.Executed.ListItems).receipt

        assertNotNull(database.captureDao().getById(captureId.value))
        assertEquals(ListSessionId("mandado-bootstrap-session"), receipt.autoCreatedMandadoSessionId)
        val session = database.listSessionDao().getById("mandado-bootstrap-session")
        assertNotNull(session)
        assertNull(session?.endedAtEpochMillis)
        assertEquals(occurredAt.toEpochMilli(), session?.lastActivityAtEpochMillis)
        val item = database.listItemDao().getById("mandado-bootstrap-item")
        assertEquals("mandado-bootstrap-session", item?.listSessionId)
        val ledger = database.actionLedgerEntryDao().getById("mandado-bootstrap-ledger")
        assertEquals(captureId.value, ledger?.sourceCaptureId)
        assertEquals(
            listOf("mandado-bootstrap-item"),
            database.actionLedgerMutationDao().getByEntryId("mandado-bootstrap-ledger")
                .map { it.targetId },
        )
    }

    @Test
    fun mandadoCaptureAfterSkippedCutoffReconcilesAndExecutesNextPeriod() = runBlocking {
        val captureId = CaptureId("pipeline-mandado-rollover")
        val zone = java.time.ZoneId.of("America/Mexico_City")
        val occurredAt = java.time.LocalDateTime
            .parse("2026-10-03T14:00:00")
            .atZone(zone)
            .toInstant()
        val previousPeriodStartedAt = java.time.LocalDateTime
            .parse("2026-09-27T10:00:00")
            .atZone(zone)
            .toInstant()
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "pipeline-old-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = previousPeriodStartedAt.toEpochMilli(),
                lastActivityAtEpochMillis = previousPeriodStartedAt.toEpochMilli(),
            ),
        )
        val executor = executor(
            itemIds = listOf("pipeline-next-item"),
            entryIds = listOf("pipeline-next-ledger"),
            times = listOf(occurredAt),
            sessionIds = listOf("pipeline-next-session"),
            mandadoCalendarPolicy = com.edu.quickaside.domain.lists.MandadoCalendarPolicy(
                clock = java.time.Clock.fixed(occurredAt, zone),
                zoneId = zone,
            ),
        )
        val submission = submission(
            captureId = captureId,
            actions = listOf(mandado("aguacate")),
            executor = executor,
        )

        val saved = submission.submit("Agrega aguacate al mandado") as
            CaptureSubmissionResult.Saved
        val receipt = (saved.execution as CaptureExecutionOutcome.Executed.ListItems).receipt

        assertNotNull(database.captureDao().getById(captureId.value))
        assertEquals(ListSessionId("pipeline-next-session"), receipt.autoCreatedMandadoSessionId)
        assertEquals(
            occurredAt.toEpochMilli(),
            database.listSessionDao().getById("pipeline-old-session")?.endedAtEpochMillis,
        )
        assertEquals(
            "pipeline-next-session",
            database.listItemDao().getById("pipeline-next-item")?.listSessionId,
        )
        assertEquals(
            listOf("pipeline-old-session", "pipeline-next-session"),
            database.listSessionDao()
                .getByDefinitionId(BuiltInListDefinitions.MANDADO.id.value)
                .sortedBy { it.startedAtEpochMillis }
                .map { it.id },
        )
    }

    @Test
    fun targetedUndoUsesReturnedIdsDeletesExactBatchMarksLedgerAndPreservesCapture() = runBlocking {
        val captureId = CaptureId("pipeline-undo")
        val executor = executor(
            itemIds = listOf("undo-item-1", "undo-item-2"),
            entryIds = listOf("undo-ledger"),
            times = listOf(
                Instant.parse("2026-09-19T20:02:00Z"),
                Instant.parse("2026-09-19T20:03:00Z"),
            ),
        )
        val saved = submission(
            captureId = captureId,
            actions = listOf(compras("cuerdas"), compras("plumillas")),
            executor = executor,
        ).submit("Compra cuerdas y plumillas") as CaptureSubmissionResult.Saved
        val receipt = (saved.execution as CaptureExecutionOutcome.Executed.ListItems).receipt

        val undo = executor.undoExecution(
            actionLedgerEntryId = receipt.actionLedgerEntryId,
            expectedItemIds = receipt.items.map(ListItem::id),
        )

        assertEquals(
            UndoCapturePlanListExecutionResult.Undone(
                actionLedgerEntryId = receipt.actionLedgerEntryId,
                itemIds = receipt.items.map(ListItem::id),
            ),
            undo,
        )
        receipt.items.forEach { assertNull(database.listItemDao().getById(it.id.value)) }
        assertNotNull(database.actionLedgerEntryDao().getById("undo-ledger")?.undoneAtEpochMillis)
        assertNotNull(database.captureDao().getById(captureId.value))
    }

    private fun submission(
        captureId: CaptureId,
        actions: List<CapturePlanAction>,
        executor: RoomCapturePlanListExecutor,
    ) = CaptureSubmission(
        writer = RoomCaptureWriter(database),
        interpreter = CaptureInterpreter { capture ->
            CaptureInterpretationResult.Success(
                CapturePlan(
                    sourceCaptureId = capture.id,
                    actions = actions,
                ),
            )
        },
        listExecutor = executor,
        idProvider = { captureId },
        capturedAtProvider = { Instant.parse("2026-09-19T20:00:00Z") },
    )

    private fun executor(
        itemIds: List<String> = emptyList(),
        entryIds: List<String> = emptyList(),
        times: List<Instant> = emptyList(),
        sessionIds: List<String> = emptyList(),
        mandadoCalendarPolicy: com.edu.quickaside.domain.lists.MandadoCalendarPolicy =
            com.edu.quickaside.domain.lists.MandadoCalendarPolicy(),
    ) = RoomCapturePlanListExecutor(
        database = database,
        itemIdProvider = QueueItemIdProvider(itemIds),
        actionLedgerIdProvider = QueueEntryIdProvider(entryIds),
        clock = QueueClock(times),
        sessionIdProvider = QueueSessionIdProvider(sessionIds),
        mandadoCalendarPolicy = mandadoCalendarPolicy,
    )

    private fun compras(text: String) = CapturePlanAction.AddListItem(
        BuiltInListDefinitions.COMPRAS.id,
        text,
    )

    private fun mandado(text: String) = CapturePlanAction.AddListItem(
        BuiltInListDefinitions.MANDADO.id,
        text,
    )

    private class QueueItemIdProvider(ids: List<String>) : ListItemIdProvider {
        private val values = ArrayDeque(ids.map(::ListItemId))
        override fun nextItemId(): ListItemId = values.removeFirst()
    }

    private class QueueEntryIdProvider(ids: List<String>) : ActionLedgerIdProvider {
        private val values = ArrayDeque(ids.map(::ActionLedgerEntryId))
        override fun nextEntryId(): ActionLedgerEntryId = values.removeFirst()
    }

    private class QueueSessionIdProvider(ids: List<String>) : ListSessionIdProvider {
        private val values = ArrayDeque(ids.map(::ListSessionId))
        override fun nextSessionId(): ListSessionId = values.removeFirst()
    }

    private class QueueClock(times: List<Instant>) : ListClock {
        private val values = ArrayDeque(times)
        override fun now(): Instant = values.removeFirst()
    }
}

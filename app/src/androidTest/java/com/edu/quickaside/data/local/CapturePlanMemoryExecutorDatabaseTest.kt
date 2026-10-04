package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.capture.CapturePlanMemoryExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanMemoryExecutionResult
import com.edu.quickaside.application.capture.CapturePlanMemoryPlanRejectionReason
import com.edu.quickaside.application.capture.CreatedMemoryRecord
import com.edu.quickaside.application.capture.MemoryExecutionTarget
import com.edu.quickaside.application.capture.UndoCapturePlanMemoryExecutionResult
import com.edu.quickaside.application.memory.MemoryIdProvider
import com.edu.quickaside.application.memory.NOTE_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.memory.NOTE_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.memory.STRUCTURED_LOG_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.memory.STRUCTURED_LOG_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.capture.CapturePlanContract
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CHG-032 owns Memory-plan execution/Undo verification; no CaptureSubmission routing. */
@RunWith(AndroidJUnit4::class)
class CapturePlanMemoryExecutorDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase
    private val sourceId = CaptureId("memory-plan-source")
    private val executionTime = Instant.parse("2026-10-03T12:00:00Z")
    private val undoTime = executionTime.plusSeconds(60)
    private val fields = linkedMapOf("exercise" to "incline press", "weight" to "210 lbs")

    @Before
    fun setUp() {
        databaseName = "change-032-memory-${UUID.randomUUID()}.db"
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName) // This test exclusively owns this disposable database.
    }

    @Test
    fun interleavedMemoryPlanPersistsOrderedRecordsAndOneLedgerWithOneTimestamp() = runBlocking {
        insertCapture()
        var clockCalls = 0
        val result = executor(clock = ActionLedgerClock {
            check(++clockCalls == 1) { "Execution must read the clock only once" }
            executionTime
        }).execute(mixedPlan()).requireExecuted()
        assertEquals(1, clockCalls)
        assertEquals(expectedRecords(), result.records)
        database.close()
        database = QuickAsideDatabase.create(context, databaseName)
        assertMixedRecordsPersist()
        val entries = database.actionLedgerEntryDao().getRecent(10)
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(result.actionLedgerEntryId.value, entry.id)
        assertEquals(sourceId.value, entry.sourceCaptureId)
        assertEquals(executionTime.toEpochMilli(), entry.occurredAtEpochMillis)
        assertNull(entry.undoneAtEpochMillis)
        val mutations = database.actionLedgerMutationDao().getByEntryId(entry.id)
        assertEquals(listOf(0, 1, 2), mutations.map { it.position })
        assertEquals(listOf("note-1", "log-1", "note-2"), mutations.map { it.targetId })
        assertEquals(
            listOf(NOTE_ACTION_LEDGER_TARGET_TYPE, STRUCTURED_LOG_ACTION_LEDGER_TARGET_TYPE, NOTE_ACTION_LEDGER_TARGET_TYPE),
            mutations.map { it.targetType },
        )
        assertEquals(
            listOf(NOTE_CREATE_ACTION_PAYLOAD_VERSION, STRUCTURED_LOG_CREATE_ACTION_PAYLOAD_VERSION, NOTE_CREATE_ACTION_PAYLOAD_VERSION),
            mutations.map { it.payloadVersion },
        )
        assertTrue(mutations.all { it.operation == "CREATE" && it.beforeState == null && it.afterState == null })
    }

    @Test
    fun unsupportedAndInvalidPlansRejectBeforeAnyMemoryOrLedgerWrite() = runBlocking {
        insertCapture()
        val action = executor(clock = ActionLedgerClock { error("Rejected plans must not reach execution") })
        assertEquals(
            CapturePlanMemoryExecutionResult.UnsupportedAction(1),
            action.execute(plan(CapturePlanAction.CreateNote("first"), CapturePlanAction.UndoLast)),
        )
        assertEquals(
            CapturePlanMemoryExecutionResult.Rejected(1, CapturePlanMemoryExecutionRejectionReason.NOTE_TEXT_TOO_LONG),
            action.execute(plan(CapturePlanAction.CreateNote("first"), CapturePlanAction.CreateNote("x".repeat(CapturePlanContract.MAX_NOTE_CHARS + 1)))),
        )
        val cases = listOf(
            emptyMap<String, String>() to CapturePlanMemoryExecutionRejectionReason.EMPTY_FIELDS,
            (0..CapturePlanContract.MAX_STRUCTURED_LOG_FIELDS).associate { "key-$it" to "value" } to
                CapturePlanMemoryExecutionRejectionReason.TOO_MANY_FIELDS,
            mapOf(" " to "value") to CapturePlanMemoryExecutionRejectionReason.BLANK_FIELD_KEY,
            mapOf("key" to " ") to CapturePlanMemoryExecutionRejectionReason.BLANK_FIELD_VALUE,
            mapOf("k".repeat(CapturePlanContract.MAX_STRUCTURED_LOG_KEY_CHARS + 1) to "value") to
                CapturePlanMemoryExecutionRejectionReason.FIELD_KEY_TOO_LONG,
            mapOf("key" to "v".repeat(CapturePlanContract.MAX_STRUCTURED_LOG_VALUE_CHARS + 1)) to
                CapturePlanMemoryExecutionRejectionReason.FIELD_VALUE_TOO_LONG,
        )
        for ((invalidFields, reason) in cases) {
            // Typed plans can be invalidated by mutable collections after construction.
            val mutableFields = mutableMapOf("valid" to "value")
            val logAction = CapturePlanAction.CreateStructuredLog(mutableFields)
            mutableFields.clear()
            mutableFields.putAll(invalidFields)
            assertEquals(
                CapturePlanMemoryExecutionResult.Rejected(1, reason),
                action.execute(plan(CapturePlanAction.CreateNote("first"), logAction)),
            )
        }
        val mutableActions = mutableListOf<CapturePlanAction>(CapturePlanAction.CreateNote("valid"))
        val emptiedPlan = CapturePlan(sourceId, mutableActions)
        mutableActions.clear()
        assertEquals(
            CapturePlanMemoryExecutionResult.RejectedPlan(CapturePlanMemoryPlanRejectionReason.EMPTY_ACTIONS),
            action.execute(emptiedPlan),
        )
        assertEquals(
            CapturePlanMemoryExecutionResult.RejectedPlan(CapturePlanMemoryPlanRejectionReason.TOO_MANY_ACTIONS),
            action.execute(CapturePlan(sourceId, List(CapturePlanContract.MAX_ACTIONS + 1) { CapturePlanAction.CreateNote("valid") })),
        )
        assertNoExecutionWrites()
    }

    @Test
    fun missingSourceCaptureWritesNothing() = runBlocking {
        assertEquals(CapturePlanMemoryExecutionResult.MissingSourceCapture, executor().execute(mixedPlan()))
        assertNoExecutionWrites()
    }

    @Test
    fun laterDuplicateNoteIdRollsBackEarlierNoteLogAndFields() = runBlocking {
        insertCapture()
        val result = executor(noteIds = listOf("note-1", "note-1")).execute(mixedPlan())
        assertTrue(result is CapturePlanMemoryExecutionResult.Failed)
        assertNoExecutionWrites()
    }

    @Test
    fun ledgerMutationFailureRollsBackRecordsFieldsAndLedgerEntry() = runBlocking {
        insertCapture()
        installTrigger(
            "CREATE TRIGGER fail_chg032_mutation BEFORE INSERT ON action_ledger_mutations " +
                "WHEN NEW.position = 1 BEGIN SELECT RAISE(ABORT, 'CHG-032 mutation failure'); END",
        )
        assertTrue(executor().execute(mixedPlan()) is CapturePlanMemoryExecutionResult.Failed)
        assertNoExecutionWrites()
    }

    @Test
    fun executionCancellationFromIdProviderAfterMemoryWritesPropagatesAndRollsBack() = runBlocking {
        insertCapture()
        val action = executor(entryIds = object : ActionLedgerIdProvider {
            override fun nextEntryId(): ActionLedgerEntryId = throw CancellationException("CHG-032 after Memory inserts")
        })
        var cancelled = false
        try {
            action.execute(mixedPlan())
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertNoExecutionWrites()
    }

    @Test
    fun mixedUndoDeletesFieldsAndParentsPreservesUnrelatedMemoryAndSecondUndoDoesNothing() = runBlocking {
        insertCapture()
        val unrelatedNote = Note(NoteId("unrelated-note"), "keep", createdAt = executionTime)
        val unrelatedLog = StructuredLog(StructuredLogId("unrelated-log"), mapOf("keep" to "value"), createdAt = executionTime)
        database.noteDao().insert(unrelatedNote.toEntity())
        database.structuredLogDao().insert(unrelatedLog.toEntity())
        database.structuredLogFieldDao().insertAll(unrelatedLog.toFieldEntities())
        val saved = executor().execute(mixedPlan()).requireExecuted()
        val targets = saved.records.map { it.target }
        val undo = executor(clock = ActionLedgerClock { undoTime })
        assertEquals(
            UndoCapturePlanMemoryExecutionResult.Undone(saved.actionLedgerEntryId, targets),
            undo.undoExecution(saved.actionLedgerEntryId, targets),
        )
        assertEquals(listOf(unrelatedNote.toEntity()), database.noteDao().getRecent(10))
        assertEquals(listOf(unrelatedLog.toEntity()), database.structuredLogDao().getRecent(10))
        assertEquals(unrelatedLog.toFieldEntities(), database.structuredLogFieldDao().getByStructuredLogId("unrelated-log"))
        assertTrue(database.structuredLogFieldDao().getByStructuredLogId("log-1").isEmpty())
        val ledgerBefore = database.actionLedgerEntryDao().getRecent(10)
        val mutationsBefore = database.actionLedgerMutationDao().getByEntryId(saved.actionLedgerEntryId.value)
        assertEquals(undoTime.toEpochMilli(), ledgerBefore.single().undoneAtEpochMillis)
        assertEquals(
            UndoCapturePlanMemoryExecutionResult.AlreadyUndone,
            executor(clock = ActionLedgerClock { error("Second Undo must not mutate") })
                .undoExecution(saved.actionLedgerEntryId, targets),
        )
        assertEquals(ledgerBefore, database.actionLedgerEntryDao().getRecent(10))
        assertEquals(mutationsBefore, database.actionLedgerMutationDao().getByEntryId(saved.actionLedgerEntryId.value))
        assertEquals(listOf(unrelatedNote.toEntity()), database.noteDao().getRecent(10))
        assertEquals(listOf(unrelatedLog.toEntity()), database.structuredLogDao().getRecent(10))
        assertNotNull(database.captureDao().getById(sourceId.value))
    }

    @Test
    fun wrongOrderedOrTypedExpectedTargetsDeleteNothing() = runBlocking {
        insertCapture()
        val saved = executor().execute(mixedPlan()).requireExecuted()
        val targets = saved.records.map { it.target }
        val wrongType = targets.toMutableList().apply { this[0] = MemoryExecutionTarget.StructuredLog(StructuredLogId("note-1")) }
        for (expected in listOf(targets.reversed(), wrongType, targets.dropLast(1))) {
            assertEquals(
                UndoCapturePlanMemoryExecutionResult.TargetMismatch,
                executor().undoExecution(saved.actionLedgerEntryId, expected),
            )
            assertMixedRecordsPersist()
            assertLedgerNotUndone(saved.actionLedgerEntryId)
        }
    }

    @Test
    fun missingTargetLeavesRemainingRecordsFieldsAndLedgerUntouched() = runBlocking {
        insertCapture()
        val saved = executor().execute(mixedPlan()).requireExecuted()
        database.noteDao().deleteById("note-2")
        assertEquals(
            UndoCapturePlanMemoryExecutionResult.TargetMissing(2),
            executor().undoExecution(saved.actionLedgerEntryId, saved.records.map { it.target }),
        )
        assertNotNull(database.noteDao().getById("note-1"))
        assertNotNull(database.structuredLogDao().getById("log-1"))
        assertEquals(fields, database.structuredLogFieldDao().getByStructuredLogId("log-1").associate { it.fieldKey to it.fieldValue })
        assertLedgerNotUndone(saved.actionLedgerEntryId)
    }

    @Test
    fun malformedLedgerShapesNeverDeleteMemory() = runBlocking {
        insertCapture()
        val saved = executor().execute(mixedPlan()).requireExecuted()
        val original = database.actionLedgerMutationDao().getByEntryId(saved.actionLedgerEntryId.value)
        val shape = UndoCapturePlanMemoryExecutionResult.UnsupportedLedgerShape
        val variants = listOf(
            emptyList<ActionLedgerMutationEntity>() to shape,
            original.mapIndexed { i, m -> if (i == 1) m.copy(position = 5) else m } to shape,
            original.mapIndexed { i, m -> if (i == 1) m.copy(operation = "UPDATE") else m } to
                UndoCapturePlanMemoryExecutionResult.UnsupportedAction(1),
            original.mapIndexed { i, m -> if (i == 1) m.copy(targetType = "task") else m } to
                UndoCapturePlanMemoryExecutionResult.UnsupportedAction(1),
            original.mapIndexed { i, m -> if (i == 1) m.copy(payloadVersion = 999) else m } to shape,
            original.mapIndexed { i, m -> if (i == 0) m.copy(payloadVersion = 999) else m } to shape,
            original.mapIndexed { i, m -> if (i == 1) m.copy(beforeState = "unexpected") else m } to shape,
            original.mapIndexed { i, m -> if (i == 1) m.copy(afterState = "unexpected") else m } to shape,
            original.mapIndexed { i, m -> if (i == 1) m.copy(targetId = " ") else m } to shape,
            original.mapIndexed { i, m -> if (i == 2) m.copy(targetId = "note-1") else m } to shape,
        )
        for ((index, variant) in variants.withIndex()) {
            val entryId = ActionLedgerEntryId("malformed-$index")
            database.actionLedgerEntryDao().insert(ActionLedgerEntryEntity(entryId.value, 0, sourceId.value))
            database.actionLedgerMutationDao().insertAll(variant.first.map { it.copy(actionLedgerEntryId = entryId.value) })
            assertEquals(variant.second, executor().undoExecution(entryId, saved.records.map { it.target }))
            assertMixedRecordsPersist()
            assertLedgerNotUndone(entryId)
        }
        val noSourceId = ActionLedgerEntryId("no-source")
        database.actionLedgerEntryDao().insert(ActionLedgerEntryEntity(noSourceId.value, 0))
        database.actionLedgerMutationDao().insertAll(original.map { it.copy(actionLedgerEntryId = noSourceId.value) })
        assertEquals(shape, executor().undoExecution(noSourceId, saved.records.map { it.target }))
        assertEquals(
            UndoCapturePlanMemoryExecutionResult.MissingLedgerEntry,
            executor().undoExecution(ActionLedgerEntryId("absent"), saved.records.map { it.target }),
        )
        assertMixedRecordsPersist()
        assertLedgerNotUndone(noSourceId)
    }

    @Test
    fun undoParentDeleteFailureRollsBackEarlierNoteAndFieldDeletes() = runBlocking {
        insertCapture()
        val saved = executor().execute(mixedPlan()).requireExecuted()
        installTrigger(
            "CREATE TRIGGER fail_chg032_log_delete BEFORE DELETE ON structured_logs " +
                "WHEN OLD.id = 'log-1' BEGIN SELECT RAISE(ABORT, 'CHG-032 parent delete failure'); END",
        )
        assertTrue(
            executor().undoExecution(saved.actionLedgerEntryId, saved.records.map { it.target }) is
                UndoCapturePlanMemoryExecutionResult.Failed,
        )
        assertMixedRecordsPersist()
        assertLedgerNotUndone(saved.actionLedgerEntryId)
    }

    @Test
    fun undoCancellationAfterAllDeletesPropagatesAndRestoresAllRecordsAndFields() = runBlocking {
        insertCapture()
        val saved = executor().execute(mixedPlan()).requireExecuted()
        var cancelled = false
        try {
            executor(clock = ActionLedgerClock { throw CancellationException("CHG-032 after deletes") })
                .undoExecution(saved.actionLedgerEntryId, saved.records.map { it.target })
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertMixedRecordsPersist()
        assertLedgerNotUndone(saved.actionLedgerEntryId)
    }

    private suspend fun insertCapture() {
        database.captureDao().insert(Capture(sourceId, CaptureInput.Text("Memory source"), executionTime).toEntity())
    }

    private fun plan(vararg actions: CapturePlanAction) = CapturePlan(sourceId, actions.toList())

    private fun mixedPlan() = plan(
        CapturePlanAction.CreateNote("first note"),
        CapturePlanAction.CreateStructuredLog(fields),
        CapturePlanAction.CreateNote("last note"),
    )

    private fun expectedRecords(): List<CreatedMemoryRecord> = listOf(
        CreatedMemoryRecord.Note(Note(NoteId("note-1"), "first note", sourceId, executionTime)),
        CreatedMemoryRecord.StructuredLog(StructuredLog(StructuredLogId("log-1"), fields, sourceId, executionTime)),
        CreatedMemoryRecord.Note(Note(NoteId("note-2"), "last note", sourceId, executionTime)),
    )

    private fun executor(
        noteIds: List<String> = listOf("note-1", "note-2"),
        entryIds: ActionLedgerIdProvider = object : ActionLedgerIdProvider {
            override fun nextEntryId() = ActionLedgerEntryId("memory-entry")
        },
        clock: ActionLedgerClock = ActionLedgerClock { executionTime },
    ): RoomCapturePlanMemoryExecutor {
        val notes = ArrayDeque(noteIds)
        return RoomCapturePlanMemoryExecutor(
            database,
            object : MemoryIdProvider {
                override fun nextNoteId() = NoteId(notes.removeFirst())
                override fun nextStructuredLogId() = StructuredLogId("log-1")
            },
            entryIds,
            clock,
        )
    }

    private suspend fun assertMixedRecordsPersist() {
        assertEquals(2, database.noteDao().getRecent(10).size)
        assertEquals(1, database.structuredLogDao().getRecent(10).size)
        for (record in expectedRecords()) {
            when (record) {
                is CreatedMemoryRecord.Note -> assertEquals(record.note, database.noteDao().getById(record.note.id.value)?.toDomain())
                is CreatedMemoryRecord.StructuredLog -> assertEquals(
                    record.log,
                    database.structuredLogDao().getById(record.log.id.value)?.toDomain(
                        database.structuredLogFieldDao().getByStructuredLogId(record.log.id.value),
                    ),
                )
            }
        }
    }

    private suspend fun assertNoExecutionWrites() {
        assertTrue(database.noteDao().getRecent(10).isEmpty())
        assertTrue(database.structuredLogDao().getRecent(10).isEmpty())
        assertTrue(database.structuredLogFieldDao().getByStructuredLogId("log-1").isEmpty())
        assertTrue(database.actionLedgerEntryDao().getRecent(10).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("memory-entry").isEmpty())
    }

    private suspend fun assertLedgerNotUndone(id: ActionLedgerEntryId) {
        val entry = database.actionLedgerEntryDao().getById(id.value)
        assertNotNull(entry)
        assertNull(entry?.undoneAtEpochMillis)
    }

    private fun installTrigger(sql: String) {
        database.close()
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.prepare(sql).use { it.step() }
        }
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun CapturePlanMemoryExecutionResult.requireExecuted() =
        this as? CapturePlanMemoryExecutionResult.Executed
            ?: throw AssertionError("Expected Memory execution success, got $this")
}

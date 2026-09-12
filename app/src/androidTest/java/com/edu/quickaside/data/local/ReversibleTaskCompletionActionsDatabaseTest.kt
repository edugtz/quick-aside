package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.tasks.ReversibleTaskActions
import com.edu.quickaside.application.tasks.TaskCompletionActionResult
import com.edu.quickaside.application.tasks.UndoTaskCompletionChangeResult
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
import java.util.ArrayDeque
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

@RunWith(AndroidJUnit4::class)
class ReversibleTaskCompletionActionsDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-025-task-completion-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
        context.deleteDatabase(databaseName)
    }

    @Test
    fun completePersistsCanonicalTimestampAndExactUpdateLedgerForBothSpaces() = runBlocking {
        openFreshDatabase()
        insertTask(
            Task(
                id = TaskId("personal-task"),
                title = "  Pagar Totalplay  ",
                space = TaskSpace.PERSONAL,
                dueDate = null,
            ),
        )
        insertTask(
            Task(
                id = TaskId("trabajo-task"),
                title = "Revisar integración",
                space = TaskSpace.TRABAJO,
                dueDate = LocalDate.of(2026, 9, 17),
            ),
        )

        val personalResult = actions(
            entryIds = listOf("personal-entry"),
            times = listOf(Instant.parse("2026-09-12T10:00:00.123456789Z")),
        ).complete(TaskId("personal-task")) as TaskCompletionActionResult.Changed
        val trabajoResult = actions(
            entryIds = listOf("trabajo-entry"),
            times = listOf(Instant.parse("2026-09-12T10:01:00.987654321Z")),
        ).complete(TaskId("trabajo-task")) as TaskCompletionActionResult.Changed

        val personalExpected = Instant.parse("2026-09-12T10:00:00.123Z")
        val trabajoExpected = Instant.parse("2026-09-12T10:01:00.987Z")
        assertEquals(
            Task(
                id = TaskId("personal-task"),
                title = "  Pagar Totalplay  ",
                space = TaskSpace.PERSONAL,
                dueDate = null,
                completedAt = personalExpected,
            ),
            personalResult.task,
        )
        assertEquals(
            Task(
                id = TaskId("trabajo-task"),
                title = "Revisar integración",
                space = TaskSpace.TRABAJO,
                dueDate = LocalDate.of(2026, 9, 17),
                completedAt = trabajoExpected,
            ),
            trabajoResult.task,
        )
        assertEquals(personalResult.task, RoomTaskStore(database).getById(personalResult.task.id))
        assertEquals(trabajoResult.task, RoomTaskStore(database).getById(trabajoResult.task.id))
        assertEquals(
            personalExpected.toEpochMilli(),
            database.taskDao().getById("personal-task")?.completedAtEpochMillis,
        )
        assertEquals(
            trabajoExpected.toEpochMilli(),
            database.taskDao().getById("trabajo-task")?.completedAtEpochMillis,
        )
        assertCompletionLedger(
            entryId = "personal-entry",
            occurredAt = personalExpected,
            taskId = "personal-task",
            beforeState = "pending",
            afterState = "completed:${personalExpected.toEpochMilli()}",
        )
        assertCompletionLedger(
            entryId = "trabajo-entry",
            occurredAt = trabajoExpected,
            taskId = "trabajo-task",
            beforeState = "pending",
            afterState = "completed:${trabajoExpected.toEpochMilli()}",
        )
    }

    @Test
    fun reopenUsesExactPersistedCompletionMillisAndPreservesTaskFields() = runBlocking {
        openFreshDatabase()
        val original = Task(
            id = TaskId("reopen-task"),
            title = "Reabrir tarea",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 9, 18),
            completedAt = Instant.ofEpochMilli(1_789_223_400_123L),
        )
        insertTask(original)

        val result = actions(
            entryIds = listOf("reopen-entry"),
            times = listOf(Instant.parse("2026-09-12T10:02:00.123999999Z")),
        ).reopen(original.id) as TaskCompletionActionResult.Changed

        val expected = original.copy(completedAt = null)
        assertEquals(expected, result.task)
        assertEquals(expected, RoomTaskStore(database).getById(original.id))
        assertNull(database.taskDao().getById(original.id.value)?.completedAtEpochMillis)
        assertCompletionLedger(
            entryId = "reopen-entry",
            occurredAt = Instant.parse("2026-09-12T10:02:00.123Z"),
            taskId = original.id.value,
            beforeState = "completed:${original.completedAt!!.toEpochMilli()}",
            afterState = "pending",
        )
    }

    @Test
    fun missingAndAlreadyRequestedStateAreNoOpsWithoutClockOrLedgerWrites() = runBlocking {
        openFreshDatabase()
        val completed = Task(
            id = TaskId("completed-task"),
            title = "Ya completada",
            space = TaskSpace.PERSONAL,
            completedAt = Instant.ofEpochMilli(10_000L),
        )
        insertTask(completed)
        insertTask(
            Task(
                id = TaskId("pending-task"),
                title = "Pendiente",
                space = TaskSpace.PERSONAL,
            ),
        )
        val action = actions(entryIds = emptyList(), times = emptyList())

        assertEquals(
            TaskCompletionActionResult.AlreadyInRequestedState,
            action.complete(completed.id),
        )
        assertEquals(
            TaskCompletionActionResult.AlreadyInRequestedState,
            action.reopen(TaskId("pending-task")),
        )
        assertEquals(
            TaskCompletionActionResult.MissingTask,
            action.complete(TaskId("missing-task")),
        )
        assertEquals(completed, RoomTaskStore(database).getById(completed.id))
        assertEquals(2, database.taskDao().getAll().size)
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
    }

    @Test
    fun completionParentCollisionRollsBackTaskWithoutChangingExistingLedger() = runBlocking {
        openFreshDatabase()
        insertTask(Task(TaskId("parent-collision-task"), "collision", TaskSpace.PERSONAL))
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = "parent-collision-entry",
                occurredAtEpochMillis = 1_000L,
            ),
        )

        val result = actions(
            entryIds = listOf("parent-collision-entry"),
            times = listOf(Instant.parse("2026-09-12T10:03:00Z")),
        ).complete(TaskId("parent-collision-task"))

        assertTrue(result is TaskCompletionActionResult.Failed)
        assertNull(database.taskDao().getById("parent-collision-task")?.completedAtEpochMillis)
        assertEquals(
            1_000L,
            database.actionLedgerEntryDao().getById("parent-collision-entry")?.occurredAtEpochMillis,
        )
        assertTrue(database.actionLedgerMutationDao().getByEntryId("parent-collision-entry").isEmpty())
    }

    @Test
    fun completionChildFailureRollsBackTaskAndParent() = runBlocking {
        openFreshDatabase()
        insertTask(Task(TaskId("child-failure-task"), "child failure", TaskSpace.PERSONAL))
        database.close()
        addCompletionMutationFailureTrigger()
        openFreshDatabase()

        val result = actions(
            entryIds = listOf("child-failure-entry"),
            times = listOf(Instant.parse("2026-09-12T10:04:00Z")),
        ).complete(TaskId("child-failure-task"))

        assertTrue(result is TaskCompletionActionResult.Failed)
        assertNull(database.taskDao().getById("child-failure-task")?.completedAtEpochMillis)
        assertNull(database.actionLedgerEntryDao().getById("child-failure-entry"))
    }

    @Test
    fun taskCompletionUpdateFailureRecordsNoLedger() = runBlocking {
        openFreshDatabase()
        insertTask(Task(TaskId("update-failure-task"), "update failure", TaskSpace.TRABAJO))
        database.close()
        addCompletionUpdateFailureTrigger()
        openFreshDatabase()

        val result = actions(
            entryIds = listOf("update-failure-entry"),
            times = listOf(Instant.parse("2026-09-12T10:05:00Z")),
        ).complete(TaskId("update-failure-task"))

        assertTrue(result is TaskCompletionActionResult.Failed)
        assertNull(database.taskDao().getById("update-failure-task")?.completedAtEpochMillis)
        assertNull(database.actionLedgerEntryDao().getById("update-failure-entry"))
    }

    @Test
    fun completionCancellationPropagatesWithoutPartialState() = runBlocking {
        openFreshDatabase()
        val task = Task(TaskId("cancel-complete-task"), "cancel complete", TaskSpace.PERSONAL)
        insertTask(task)
        var caught: CancellationException? = null

        try {
            RoomReversibleTaskActions(
                database = database,
                actionLedgerIdProvider = QueueEntryIdProvider(listOf("cancel-complete-entry")),
                clock = ActionLedgerClock { throw CancellationException("completion cancelled") },
            ).complete(task.id)
        } catch (cancellation: CancellationException) {
            caught = cancellation
        }

        assertNotNull(caught)
        assertNull(database.taskDao().getById(task.id.value)?.completedAtEpochMillis)
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
    }

    @Test
    fun undoCompleteRestoresPendingMarksExactEntryAndSurvivesReopen() = runBlocking {
        openFreshDatabase()
        val task = Task(
            id = TaskId("undo-complete-task"),
            title = "Undo complete",
            space = TaskSpace.PERSONAL,
            dueDate = LocalDate.of(2026, 9, 19),
        )
        insertTask(task)
        val action = actions(
            entryIds = listOf("undo-complete-entry"),
            times = listOf(
                Instant.parse("2026-09-12T10:06:00.123456789Z"),
                Instant.parse("2026-09-12T10:07:00.999999999Z"),
            ),
        )
        val completed = action.complete(task.id) as TaskCompletionActionResult.Changed

        assertEquals(
            UndoTaskCompletionChangeResult.Undone(
                actionLedgerEntryId = completed.actionLedgerEntryId,
                taskId = task.id,
            ),
            action.undoCompletionChange(completed.actionLedgerEntryId, task.id),
        )
        assertEquals(task, RoomTaskStore(database).getById(task.id))
        assertNotNull(
            database.actionLedgerEntryDao().getById("undo-complete-entry")?.undoneAtEpochMillis,
        )

        database.close()
        openFreshDatabase()
        assertEquals(task, RoomTaskStore(database).getById(task.id))
        assertEquals(
            UndoTaskCompletionChangeResult.AlreadyUndone,
            RoomReversibleTaskActions(database).undoCompletionChange(
                completed.actionLedgerEntryId,
                task.id,
            ),
        )
    }

    @Test
    fun undoReopenRestoresExactOriginalCompletionTimestampAndSurvivesReopen() = runBlocking {
        openFreshDatabase()
        val original = Task(
            id = TaskId("undo-reopen-task"),
            title = "Undo reopen",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 9, 20),
            completedAt = Instant.ofEpochMilli(1_789_223_400_123L),
        )
        insertTask(original)
        val action = actions(
            entryIds = listOf("undo-reopen-entry"),
            times = listOf(
                Instant.parse("2026-09-12T10:08:00.999999999Z"),
                Instant.parse("2026-09-12T10:09:00.123999999Z"),
            ),
        )
        val reopened = action.reopen(original.id) as TaskCompletionActionResult.Changed

        assertEquals(
            UndoTaskCompletionChangeResult.Undone(
                actionLedgerEntryId = reopened.actionLedgerEntryId,
                taskId = original.id,
            ),
            action.undoCompletionChange(reopened.actionLedgerEntryId, original.id),
        )
        assertEquals(original, RoomTaskStore(database).getById(original.id))
        database.close()
        openFreshDatabase()
        assertEquals(original, RoomTaskStore(database).getById(original.id))
        assertNotNull(
            database.actionLedgerEntryDao().getById("undo-reopen-entry")?.undoneAtEpochMillis,
        )
    }

    @Test
    fun undoRejectsMissingTargetMismatchAndStaleCurrentStateWithoutMutation() = runBlocking {
        openFreshDatabase()
        val task = Task(TaskId("validation-task"), "validation", TaskSpace.PERSONAL)
        insertTask(task)
        val action = actions(
            entryIds = listOf("validation-entry"),
            times = listOf(Instant.parse("2026-09-12T10:10:00Z")),
        )
        val completed = action.complete(task.id) as TaskCompletionActionResult.Changed

        assertEquals(
            UndoTaskCompletionChangeResult.MissingLedgerEntry,
            action.undoCompletionChange(ActionLedgerEntryId("missing-entry"), task.id),
        )
        assertEquals(
            UndoTaskCompletionChangeResult.TargetMismatch,
            action.undoCompletionChange(completed.actionLedgerEntryId, TaskId("other-task")),
        )
        assertEquals(
            completed.task,
            RoomTaskStore(database).getById(task.id),
        )
        assertNull(
            database.actionLedgerEntryDao().getById(completed.actionLedgerEntryId.value)?.undoneAtEpochMillis,
        )

        val reopened = actions(
            entryIds = listOf("validation-reopen-entry"),
            times = listOf(Instant.parse("2026-09-12T10:11:00Z")),
        ).reopen(task.id) as TaskCompletionActionResult.Changed
        assertEquals(task, reopened.task)
        assertEquals(
            UndoTaskCompletionChangeResult.TargetStateMismatch,
            action.undoCompletionChange(completed.actionLedgerEntryId, task.id),
        )
        assertEquals(task, RoomTaskStore(database).getById(task.id))
        assertNull(
            database.actionLedgerEntryDao().getById(completed.actionLedgerEntryId.value)?.undoneAtEpochMillis,
        )
        assertNull(
            database.actionLedgerEntryDao().getById(reopened.actionLedgerEntryId.value)?.undoneAtEpochMillis,
        )

        database.taskDao().deleteById(task.id.value)
        assertEquals(
            UndoTaskCompletionChangeResult.TargetMissing,
            action.undoCompletionChange(reopened.actionLedgerEntryId, task.id),
        )
        assertNull(
            database.actionLedgerEntryDao().getById(reopened.actionLedgerEntryId.value)?.undoneAtEpochMillis,
        )
    }

    @Test
    fun undoRejectsAllUnsupportedLedgerShapesWithoutMutation() = runBlocking {
        openFreshDatabase()
        insertMalformedAction(
            entryId = "wrong-operation",
            taskId = "wrong-operation-task",
            operation = "CREATE",
            beforeState = "pending",
            afterState = "completed:1000",
        )
        insertMalformedAction(
            entryId = "wrong-target-type",
            taskId = "wrong-target-type-task",
            targetType = "note",
            beforeState = "pending",
            afterState = "completed:1000",
        )
        insertMalformedAction(
            entryId = "wrong-version",
            taskId = "wrong-version-task",
            payloadVersion = 2,
            beforeState = "pending",
            afterState = "completed:1000",
        )
        insertMalformedAction(
            entryId = "zero-mutations",
            taskId = "zero-mutations-task",
            mutationCount = 0,
        )
        insertMalformedAction(
            entryId = "multiple-mutations",
            taskId = "multiple-mutations-task",
            mutationCount = 2,
        )
        insertMalformedAction(
            entryId = "null-before",
            taskId = "null-before-task",
            beforeState = null,
            afterState = "completed:1000",
        )
        insertMalformedAction(
            entryId = "null-after",
            taskId = "null-after-task",
            beforeState = "pending",
            afterState = null,
        )
        insertMalformedAction(
            entryId = "malformed-payload",
            taskId = "malformed-payload-task",
            beforeState = "pending ",
            afterState = "completed:1000",
        )
        insertMalformedAction(
            entryId = "same-state",
            taskId = "same-state-task",
            beforeState = "completed:1000",
            afterState = "completed:2000",
            taskCompletedAtEpochMillis = 2_000L,
        )

        val action = RoomReversibleTaskActions(database)
        assertEquals(
            UndoTaskCompletionChangeResult.UnsupportedAction,
            action.undoCompletionChange(ActionLedgerEntryId("wrong-operation"), TaskId("wrong-operation-task")),
        )
        assertEquals(
            UndoTaskCompletionChangeResult.UnsupportedAction,
            action.undoCompletionChange(ActionLedgerEntryId("wrong-target-type"), TaskId("wrong-target-type-task")),
        )
        assertEquals(
            UndoTaskCompletionChangeResult.UnsupportedLedgerShape,
            action.undoCompletionChange(ActionLedgerEntryId("wrong-version"), TaskId("wrong-version-task")),
        )
        assertEquals(
            UndoTaskCompletionChangeResult.UnsupportedLedgerShape,
            action.undoCompletionChange(ActionLedgerEntryId("zero-mutations"), TaskId("zero-mutations-task")),
        )
        assertEquals(
            UndoTaskCompletionChangeResult.UnsupportedLedgerShape,
            action.undoCompletionChange(ActionLedgerEntryId("multiple-mutations"), TaskId("multiple-mutations-task")),
        )
        assertEquals(
            UndoTaskCompletionChangeResult.UnsupportedLedgerShape,
            action.undoCompletionChange(ActionLedgerEntryId("null-before"), TaskId("null-before-task")),
        )
        assertEquals(
            UndoTaskCompletionChangeResult.UnsupportedLedgerShape,
            action.undoCompletionChange(ActionLedgerEntryId("null-after"), TaskId("null-after-task")),
        )
        assertEquals(
            UndoTaskCompletionChangeResult.UnsupportedLedgerShape,
            action.undoCompletionChange(ActionLedgerEntryId("malformed-payload"), TaskId("malformed-payload-task")),
        )
        assertEquals(
            UndoTaskCompletionChangeResult.UnsupportedLedgerShape,
            action.undoCompletionChange(ActionLedgerEntryId("same-state"), TaskId("same-state-task")),
        )

        listOf(
            "wrong-operation-task",
            "wrong-target-type-task",
            "wrong-version-task",
            "zero-mutations-task",
            "multiple-mutations-task",
            "null-before-task",
            "null-after-task",
            "malformed-payload-task",
            "same-state-task",
        ).forEach { taskId -> assertNotNull(database.taskDao().getById(taskId)) }
        assertTrue(
            database.actionLedgerEntryDao().getRecent(50)
                .all { it.undoneAtEpochMillis == null },
        )
    }

    @Test
    fun undoMarkFailureAndCancellationRollBackTaskRestoration() = runBlocking {
        openFreshDatabase()
        val task = Task(TaskId("mark-failure-task"), "mark failure", TaskSpace.PERSONAL)
        insertTask(task)
        val completed = actions(
            entryIds = listOf("mark-failure-entry"),
            times = listOf(Instant.parse("2026-09-12T10:12:00Z")),
        ).complete(task.id) as TaskCompletionActionResult.Changed
        database.close()
        addUndoMarkFailureTrigger()
        openFreshDatabase()

        val failed = RoomReversibleTaskActions(
            database = database,
            clock = ActionLedgerClock { Instant.parse("2026-09-12T10:13:00Z") },
        ).undoCompletionChange(completed.actionLedgerEntryId, task.id)
        assertTrue(failed is UndoTaskCompletionChangeResult.Failed)
        assertEquals(completed.task, RoomTaskStore(database).getById(task.id))
        assertNull(database.actionLedgerEntryDao().getById(completed.actionLedgerEntryId.value)?.undoneAtEpochMillis)

        database.close()
        context.deleteDatabase(databaseName)
        openFreshDatabase()
        val secondTask = Task(TaskId("cancel-mark-task"), "cancel mark", TaskSpace.PERSONAL)
        insertTask(secondTask)
        val second = actions(
            entryIds = listOf("cancel-mark-entry"),
            times = listOf(Instant.parse("2026-09-12T10:14:00Z")),
        ).complete(secondTask.id) as TaskCompletionActionResult.Changed
        var caught: CancellationException? = null
        try {
            RoomReversibleTaskActions(
                database = database,
                clock = ActionLedgerClock { throw CancellationException("mark cancelled") },
            ).undoCompletionChange(second.actionLedgerEntryId, secondTask.id)
        } catch (cancellation: CancellationException) {
            caught = cancellation
        }
        assertNotNull(caught)
        assertEquals(second.task, RoomTaskStore(database).getById(secondTask.id))
        assertNull(database.actionLedgerEntryDao().getById(second.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    @Test
    fun completionUndoPreservesNewerUnrelatedTaskFields() = runBlocking {
        openFreshDatabase()
        val original = Task(
            id = TaskId("narrow-undo-task"),
            title = "Original title",
            space = TaskSpace.PERSONAL,
            dueDate = LocalDate.of(2026, 9, 21),
        )
        insertTask(original)
        val completed = actions(
            entryIds = listOf("narrow-undo-entry"),
            times = listOf(Instant.parse("2026-09-12T10:15:00.123456789Z")),
        ).complete(original.id) as TaskCompletionActionResult.Changed
        val newerFields = completed.task.copy(
            title = "Newer title",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 9, 22),
        )
        RoomTaskStore(database).save(newerFields)

        assertEquals(
            UndoTaskCompletionChangeResult.Undone(completed.actionLedgerEntryId, original.id),
            RoomReversibleTaskActions(
                database = database,
                clock = ActionLedgerClock { Instant.parse("2026-09-12T10:16:00Z") },
            ).undoCompletionChange(completed.actionLedgerEntryId, original.id),
        )
        assertEquals(newerFields.copy(completedAt = null), RoomTaskStore(database).getById(original.id))
    }

    private suspend fun insertTask(task: Task) {
        database.taskDao().insertStrict(task.toEntity())
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun actions(
        entryIds: List<String> = emptyList(),
        times: List<Instant> = emptyList(),
    ): ReversibleTaskActions = RoomReversibleTaskActions(
        database = database,
        actionLedgerIdProvider = QueueEntryIdProvider(entryIds),
        clock = QueueActionClock(times),
    )

    private suspend fun assertCompletionLedger(
        entryId: String,
        occurredAt: Instant,
        taskId: String,
        beforeState: String,
        afterState: String,
    ) {
        val entry = database.actionLedgerEntryDao().getById(entryId)
        assertNotNull(entry)
        assertEquals(occurredAt.toEpochMilli(), entry?.occurredAtEpochMillis)
        assertNull(entry?.sourceCaptureId)
        assertNull(entry?.undoneAtEpochMillis)
        val mutations = database.actionLedgerMutationDao().getByEntryId(entryId)
        assertEquals(1, mutations.size)
        val mutation = mutations.single()
        assertEquals(0, mutation.position)
        assertEquals(ActionLedgerOperation.UPDATE.name, mutation.operation)
        assertEquals("task", mutation.targetType)
        assertEquals(taskId, mutation.targetId)
        assertEquals(1, mutation.payloadVersion)
        assertEquals(beforeState, mutation.beforeState)
        assertEquals(afterState, mutation.afterState)
    }

    private suspend fun insertMalformedAction(
        entryId: String,
        taskId: String,
        operation: String = ActionLedgerOperation.UPDATE.name,
        targetType: String = "task",
        payloadVersion: Int = 1,
        mutationCount: Int = 1,
        beforeState: String? = "pending",
        afterState: String? = "completed:1000",
        taskCompletedAtEpochMillis: Long? = null,
    ) {
        database.taskDao().insertStrict(
            TaskEntity(
                id = taskId,
                title = taskId,
                space = TaskSpace.PERSONAL.name,
                completedAtEpochMillis = taskCompletedAtEpochMillis,
            ),
        )
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = entryId,
                occurredAtEpochMillis = 10_000L,
            ),
        )
        if (mutationCount > 0) {
            database.actionLedgerMutationDao().insertAll(
                (0 until mutationCount).map { position ->
                    ActionLedgerMutationEntity(
                        actionLedgerEntryId = entryId,
                        position = position,
                        operation = operation,
                        targetType = targetType,
                        targetId = taskId,
                        payloadVersion = payloadVersion,
                        beforeState = beforeState,
                        afterState = afterState,
                    )
                },
            )
        }
    }

    private fun addCompletionMutationFailureTrigger() {
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_change025_completion_mutation BEFORE INSERT ON action_ledger_mutations " +
                    "WHEN NEW.target_id = 'child-failure-task' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced Change 025 mutation failure'); END",
            )
        }
    }

    private fun addCompletionUpdateFailureTrigger() {
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_change025_completion_update BEFORE UPDATE OF completed_at_epoch_millis ON tasks " +
                    "WHEN OLD.id = 'update-failure-task' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced Change 025 update failure'); END",
            )
        }
    }

    private fun addUndoMarkFailureTrigger() {
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_change025_undo_mark BEFORE UPDATE OF undone_at_epoch_millis ON action_ledger_entries " +
                    "WHEN OLD.id = 'mark-failure-entry' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced Change 025 mark failure'); END",
            )
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private class QueueActionClock(times: List<Instant>) : ActionLedgerClock {
        private val times = ArrayDeque(times)

        override fun now(): Instant = times.removeFirst()
    }

    private class QueueEntryIdProvider(ids: List<String>) : ActionLedgerIdProvider {
        private val ids = ArrayDeque(ids)

        override fun nextEntryId(): ActionLedgerEntryId = ActionLedgerEntryId(ids.removeFirst())
    }

}

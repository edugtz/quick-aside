package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.tasks.CreateTaskActionResult
import com.edu.quickaside.application.tasks.ReversibleTaskActions
import com.edu.quickaside.application.tasks.TaskIdProvider
import com.edu.quickaside.application.tasks.UndoTaskCreateResult
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
class ReversibleTaskActionsDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-024-reversible-task-${UUID.randomUUID()}.db"
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
    fun createPersistsPersonalAndTrabajoTasksAsPendingWithExactLedgerShape() = runBlocking {
        openFreshDatabase()
        val personalAt = Instant.parse("2026-09-11T10:00:00Z")
        val trabajoAt = Instant.parse("2026-09-11T10:01:00Z")
        val action = actions(
            taskIds = listOf("personal-task", "trabajo-task"),
            entryIds = listOf("personal-entry", "trabajo-entry"),
            times = listOf(personalAt, trabajoAt),
        )

        val personalResult = action.create(
            title = "  Pagar Totalplay  ",
            space = TaskSpace.PERSONAL,
        ) as CreateTaskActionResult.Saved
        val trabajoResult = action.create(
            title = "Revisar integración",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 9, 17),
        ) as CreateTaskActionResult.Saved

        assertEquals(
            Task(
                id = TaskId("personal-task"),
                title = "  Pagar Totalplay  ",
                space = TaskSpace.PERSONAL,
                dueDate = null,
                completedAt = null,
            ),
            personalResult.task,
        )
        assertEquals(
            Task(
                id = TaskId("trabajo-task"),
                title = "Revisar integración",
                space = TaskSpace.TRABAJO,
                dueDate = LocalDate.of(2026, 9, 17),
                completedAt = null,
            ),
            trabajoResult.task,
        )
        assertEquals(TaskId("personal-task"), personalResult.task.id)
        assertEquals(personalResult.task, RoomTaskStore(database).getById(personalResult.task.id))
        assertEquals(trabajoResult.task, RoomTaskStore(database).getById(trabajoResult.task.id))
        assertNull(database.taskDao().getById("personal-task")?.completedAtEpochMillis)
        assertNull(database.taskDao().getById("trabajo-task")?.completedAtEpochMillis)
        assertEquals(2, database.taskDao().getAll().size)

        assertCreateLedger("personal-entry", personalAt, "personal-task")
        assertCreateLedger("trabajo-entry", trabajoAt, "trabajo-task")
        assertEquals(2, database.actionLedgerEntryDao().getRecent(50).size)
    }

    @Test
    fun blankTitleIsRejectedWithoutAnyTaskOrLedgerRows() = runBlocking {
        openFreshDatabase()

        assertEquals(
            CreateTaskActionResult.BlankTitle,
            RoomReversibleTaskActions(database).create(
                title = " \t\n ",
                space = TaskSpace.PERSONAL,
            ),
        )

        assertTrue(database.taskDao().getAll().isEmpty())
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("unused").isEmpty())
    }

    @Test
    fun generatedIdCollisionFailsWithoutOverwritingExistingTaskOrRecordingLedger() = runBlocking {
        openFreshDatabase()
        val existing = Task(
            id = TaskId("collision-task"),
            title = "  Existing exact task  ",
            space = TaskSpace.PERSONAL,
            dueDate = LocalDate.of(2026, 9, 20),
            completedAt = Instant.parse("2026-09-20T08:00:00Z"),
        )
        RoomTaskStore(database).save(existing)
        val existingEntity = database.taskDao().getById(existing.id.value)

        val result = actions(
            taskIds = listOf("collision-task"),
            entryIds = listOf("collision-entry"),
            times = listOf(Instant.parse("2026-09-11T10:02:00Z")),
        ).create(
            title = "replacement must not win",
            space = TaskSpace.TRABAJO,
        )

        assertTrue(result is CreateTaskActionResult.Failed)
        assertEquals(existing, RoomTaskStore(database).getById(existing.id))
        assertEquals(existingEntity, database.taskDao().getById(existing.id.value))
        assertEquals(1, database.taskDao().getAll().size)
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("collision-entry").isEmpty())
    }

    @Test
    fun ledgerEntryIdCollisionRollsBackTaskWithoutChangingExistingLedger() = runBlocking {
        openFreshDatabase()
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = "entry-collision",
                occurredAtEpochMillis = 1_000,
            ),
        )

        val result = actions(
            taskIds = listOf("parent-collision-task"),
            entryIds = listOf("entry-collision"),
            times = listOf(Instant.parse("2026-09-11T10:02:30Z")),
        ).create("parent collision", TaskSpace.TRABAJO)

        assertTrue(result is CreateTaskActionResult.Failed)
        assertNull(database.taskDao().getById("parent-collision-task"))
        assertEquals(
            1_000L,
            database.actionLedgerEntryDao().getById("entry-collision")?.occurredAtEpochMillis,
        )
        assertTrue(database.actionLedgerMutationDao().getByEntryId("entry-collision").isEmpty())
    }

    @Test
    fun createChildFailureRollsBackTaskParentAndMutation() = runBlocking {
        openFreshDatabase()
        assertTrue(database.actionLedgerEntryDao().getRecent(1).isEmpty())
        database.close()
        addCreateMutationFailureTrigger()
        openFreshDatabase()

        val result = actions(
            taskIds = listOf("rollback-task"),
            entryIds = listOf("rollback-entry"),
            times = listOf(Instant.parse("2026-09-11T10:03:00Z")),
        ).create("rollback", TaskSpace.PERSONAL)

        assertTrue(result is CreateTaskActionResult.Failed)
        assertNull(database.taskDao().getById("rollback-task"))
        assertNull(database.actionLedgerEntryDao().getById("rollback-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("rollback-entry").isEmpty())
    }

    @Test
    fun createCancellationPropagatesWithoutPartialState() = runBlocking {
        openFreshDatabase()
        var caught: CancellationException? = null

        try {
            RoomReversibleTaskActions(
                database = database,
                taskIdProvider = QueueTaskIdProvider(listOf("cancel-task")),
                actionLedgerIdProvider = QueueEntryIdProvider(listOf("cancel-entry")),
                clock = ActionLedgerClock { throw CancellationException("create cancelled") },
            ).create("cancelled", TaskSpace.PERSONAL)
        } catch (cancellation: CancellationException) {
            caught = cancellation
        }

        assertNotNull(caught)
        assertNull(database.taskDao().getById("cancel-task"))
        assertNull(database.actionLedgerEntryDao().getById("cancel-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("cancel-entry").isEmpty())
    }

    @Test
    fun undoDeletesOnlyExactTaskMarksEntryAndSurvivesReopen() = runBlocking {
        openFreshDatabase()
        val first = actions(
            taskIds = listOf("task-one", "task-two"),
            entryIds = listOf("entry-one", "entry-two"),
            times = listOf(
                Instant.parse("2026-09-11T10:04:00Z"),
                Instant.parse("2026-09-11T10:05:00Z"),
                Instant.parse("2026-09-11T10:06:00Z"),
            ),
        ).create("uno", TaskSpace.PERSONAL) as CreateTaskActionResult.Saved
        val second = RoomReversibleTaskActions(
            database = database,
            taskIdProvider = QueueTaskIdProvider(listOf("task-two")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("entry-two")),
            clock = QueueActionClock(listOf(Instant.parse("2026-09-11T10:05:00Z"))),
        ).create("dos", TaskSpace.TRABAJO) as CreateTaskActionResult.Saved

        assertEquals(
            UndoTaskCreateResult.Undone(first.actionLedgerEntryId, first.task.id),
            actions(times = listOf(Instant.parse("2026-09-11T10:06:00Z")))
                .undoCreate(first.actionLedgerEntryId, first.task.id),
        )
        assertNull(database.taskDao().getById(first.task.id.value))
        assertNotNull(database.taskDao().getById(second.task.id.value))
        assertNotNull(database.actionLedgerEntryDao().getById(first.actionLedgerEntryId.value)?.undoneAtEpochMillis)
        assertNull(database.actionLedgerEntryDao().getById(second.actionLedgerEntryId.value)?.undoneAtEpochMillis)

        database.close()
        openFreshDatabase()
        assertNull(database.taskDao().getById(first.task.id.value))
        assertNotNull(database.taskDao().getById(second.task.id.value))
        assertNotNull(database.actionLedgerEntryDao().getById(first.actionLedgerEntryId.value)?.undoneAtEpochMillis)
        assertEquals(
            UndoTaskCreateResult.AlreadyUndone,
            RoomReversibleTaskActions(database).undoCreate(first.actionLedgerEntryId, first.task.id),
        )
        assertEquals(1, database.taskDao().getAll().size)
    }

    @Test
    fun undoRejectsMissingEntryMismatchAndMissingTargetWithoutChangingLedger() = runBlocking {
        openFreshDatabase()
        val saved = actions(
            taskIds = listOf("target-task"),
            entryIds = listOf("target-entry"),
            times = listOf(Instant.parse("2026-09-11T10:07:00Z")),
        ).create("target", TaskSpace.PERSONAL) as CreateTaskActionResult.Saved
        val action = RoomReversibleTaskActions(database)

        assertEquals(
            UndoTaskCreateResult.MissingLedgerEntry,
            action.undoCreate(ActionLedgerEntryId("missing-entry"), saved.task.id),
        )
        assertEquals(
            UndoTaskCreateResult.TargetMismatch,
            action.undoCreate(saved.actionLedgerEntryId, TaskId("different-task")),
        )
        assertNotNull(database.taskDao().getById(saved.task.id.value))
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)

        assertEquals(1, database.taskDao().deleteById(saved.task.id.value))
        assertEquals(
            UndoTaskCreateResult.TargetMissing,
            action.undoCreate(saved.actionLedgerEntryId, saved.task.id),
        )
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    @Test
    fun undoRejectsUnsupportedAndMalformedLedgerShapesWithoutDeletingTasks() = runBlocking {
        openFreshDatabase()
        insertMalformedAction(
            entryId = "wrong-operation",
            taskId = "wrong-operation-task",
            operation = "UPDATE",
        )
        insertMalformedAction(
            entryId = "wrong-target-type",
            taskId = "wrong-target-type-task",
            targetType = "note",
        )
        insertMalformedAction(
            entryId = "wrong-version",
            taskId = "wrong-version-task",
            payloadVersion = 2,
        )
        insertMalformedAction(
            entryId = "multiple-mutations",
            taskId = "multiple-mutations-task",
            mutationCount = 2,
        )
        insertMalformedAction(
            entryId = "missing-mutation",
            taskId = "missing-mutation-task",
            mutationCount = 0,
        )
        insertMalformedAction(
            entryId = "unknown-operation",
            taskId = "unknown-operation-task",
            operation = "NOT_AN_OPERATION",
        )

        val action = RoomReversibleTaskActions(database)
        assertEquals(
            UndoTaskCreateResult.UnsupportedAction,
            action.undoCreate(ActionLedgerEntryId("wrong-operation"), TaskId("wrong-operation-task")),
        )
        assertEquals(
            UndoTaskCreateResult.UnsupportedAction,
            action.undoCreate(ActionLedgerEntryId("wrong-target-type"), TaskId("wrong-target-type-task")),
        )
        assertEquals(
            UndoTaskCreateResult.UnsupportedLedgerShape,
            action.undoCreate(ActionLedgerEntryId("wrong-version"), TaskId("wrong-version-task")),
        )
        assertEquals(
            UndoTaskCreateResult.UnsupportedLedgerShape,
            action.undoCreate(ActionLedgerEntryId("multiple-mutations"), TaskId("multiple-mutations-task")),
        )
        assertEquals(
            UndoTaskCreateResult.UnsupportedLedgerShape,
            action.undoCreate(ActionLedgerEntryId("missing-mutation"), TaskId("missing-mutation-task")),
        )
        assertEquals(
            UndoTaskCreateResult.UnsupportedAction,
            action.undoCreate(ActionLedgerEntryId("unknown-operation"), TaskId("unknown-operation-task")),
        )

        listOf(
            "wrong-operation-task",
            "wrong-target-type-task",
            "wrong-version-task",
            "multiple-mutations-task",
            "missing-mutation-task",
            "unknown-operation-task",
        ).forEach { taskId ->
            assertNotNull(database.taskDao().getById(taskId))
        }
        assertTrue(
            database.actionLedgerEntryDao().getRecent(50)
                .all { it.undoneAtEpochMillis == null },
        )
    }

    @Test
    fun undoDeleteFailureRollsBackTaskAndLedgerUpdate() = runBlocking {
        openFreshDatabase()
        val saved = actions(
            taskIds = listOf("delete-failure-task"),
            entryIds = listOf("delete-failure-entry"),
            times = listOf(Instant.parse("2026-09-11T10:08:00Z")),
        ).create("delete failure", TaskSpace.PERSONAL) as CreateTaskActionResult.Saved
        database.close()
        addUndoDeleteFailureTrigger()
        openFreshDatabase()

        val result = RoomReversibleTaskActions(database).undoCreate(
            saved.actionLedgerEntryId,
            saved.task.id,
        )

        assertTrue(result is UndoTaskCreateResult.Failed)
        assertNotNull(database.taskDao().getById(saved.task.id.value))
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    @Test
    fun undoMarkFailureRollsBackTaskDeletionAndLedgerUpdate() = runBlocking {
        openFreshDatabase()
        val saved = actions(
            taskIds = listOf("mark-failure-task"),
            entryIds = listOf("mark-failure-entry"),
            times = listOf(Instant.parse("2026-09-11T10:09:00Z")),
        ).create("mark failure", TaskSpace.TRABAJO) as CreateTaskActionResult.Saved
        database.close()
        addUndoMarkFailureTrigger()
        openFreshDatabase()

        val result = actions(
            times = listOf(Instant.parse("2026-09-11T10:10:00Z")),
        ).undoCreate(saved.actionLedgerEntryId, saved.task.id)

        assertTrue(result is UndoTaskCreateResult.Failed)
        assertNotNull(database.taskDao().getById(saved.task.id.value))
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    @Test
    fun undoCancellationPropagatesAndRollsBackTaskDeletion() = runBlocking {
        openFreshDatabase()
        val saved = actions(
            taskIds = listOf("cancel-undo-task"),
            entryIds = listOf("cancel-undo-entry"),
            times = listOf(Instant.parse("2026-09-11T10:11:00Z")),
        ).create("cancel undo", TaskSpace.PERSONAL) as CreateTaskActionResult.Saved
        var caught: CancellationException? = null

        try {
            RoomReversibleTaskActions(
                database = database,
                clock = ActionLedgerClock { throw CancellationException("undo cancelled") },
            ).undoCreate(saved.actionLedgerEntryId, saved.task.id)
        } catch (cancellation: CancellationException) {
            caught = cancellation
        }

        assertNotNull(caught)
        assertNotNull(database.taskDao().getById(saved.task.id.value))
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun actions(
        taskIds: List<String> = emptyList(),
        entryIds: List<String> = emptyList(),
        times: List<Instant> = emptyList(),
    ): ReversibleTaskActions = RoomReversibleTaskActions(
        database = database,
        taskIdProvider = QueueTaskIdProvider(taskIds),
        actionLedgerIdProvider = QueueEntryIdProvider(entryIds),
        clock = QueueActionClock(times),
    )

    private suspend fun assertCreateLedger(
        entryId: String,
        occurredAt: Instant,
        taskId: String,
    ) {
        val entry = database.actionLedgerEntryDao().getById(entryId)
        assertNotNull(entry)
        assertEquals(occurredAt.toEpochMilli(), entry?.occurredAtEpochMillis)
        assertNull(entry?.sourceCaptureId)
        assertNull(entry?.undoneAtEpochMillis)

        val mutations = database.actionLedgerMutationDao().getByEntryId(entryId)
        assertEquals(1, mutations.size)
        val mutation = mutations.single()
        assertEquals(ActionLedgerOperation.CREATE.name, mutation.operation)
        assertEquals("task", mutation.targetType)
        assertEquals(taskId, mutation.targetId)
        assertEquals(1, mutation.payloadVersion)
        assertNull(mutation.beforeState)
        assertNull(mutation.afterState)
    }

    private suspend fun insertMalformedAction(
        entryId: String,
        taskId: String,
        operation: String = "CREATE",
        targetType: String = "task",
        payloadVersion: Int = 1,
        mutationCount: Int = 1,
    ) {
        database.taskDao().insertStrict(
            TaskEntity(
                id = taskId,
                title = taskId,
                space = TaskSpace.PERSONAL.name,
            ),
        )
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = entryId,
                occurredAtEpochMillis = 10_000,
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
                    )
                },
            )
        }
    }

    private fun addCreateMutationFailureTrigger() {
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_change024_create_mutation BEFORE INSERT ON action_ledger_mutations " +
                    "WHEN NEW.target_id = 'rollback-task' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced Change 024 create failure'); END",
            )
        }
    }

    private fun addUndoDeleteFailureTrigger() {
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_change024_undo_delete BEFORE DELETE ON tasks " +
                    "WHEN OLD.id = 'delete-failure-task' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced Change 024 delete failure'); END",
            )
        }
    }

    private fun addUndoMarkFailureTrigger() {
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_change024_undo_mark BEFORE UPDATE OF undone_at_epoch_millis ON action_ledger_entries " +
                    "WHEN OLD.id = 'mark-failure-entry' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced Change 024 mark failure'); END",
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

    private class QueueTaskIdProvider(ids: List<String>) : TaskIdProvider {
        private val ids = ArrayDeque(ids)

        override fun nextTaskId(): TaskId = TaskId(ids.removeFirst())
    }

    private class QueueEntryIdProvider(ids: List<String>) : ActionLedgerIdProvider {
        private val ids = ArrayDeque(ids)

        override fun nextEntryId(): ActionLedgerEntryId = ActionLedgerEntryId(ids.removeFirst())
    }
}

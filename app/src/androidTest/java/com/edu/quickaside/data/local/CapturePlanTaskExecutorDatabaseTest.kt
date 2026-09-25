package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.capture.CapturePlanTaskExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanTaskExecutionResult
import com.edu.quickaside.application.capture.CapturePlanTaskExecutor
import com.edu.quickaside.application.capture.CapturePlanTaskPlanRejectionReason
import com.edu.quickaside.application.capture.UndoCapturePlanTaskExecutionResult
import com.edu.quickaside.application.tasks.TASK_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.tasks.TASK_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.tasks.TaskIdProvider
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.capture.CapturePlanContract
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapturePlanTaskExecutorDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-029-capture-plan-task-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun singlePersonalAndTrabajoTasksPreserveExactFieldsAndStartPending() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val personalAt = Instant.parse("2026-09-23T15:00:00.123Z")
        val personal = executor(
            taskIds = listOf("personal-task"),
            entryIds = listOf("personal-entry"),
            times = listOf(personalAt),
        ).execute(
            plan(task("  Pay Totalplay  ", TaskSpace.PERSONAL)),
        ).requireExecuted()

        assertEquals(
            listOf(
                Task(
                    id = TaskId("personal-task"),
                    title = "  Pay Totalplay  ",
                    space = TaskSpace.PERSONAL,
                    dueDate = null,
                    completedAt = null,
                ),
            ),
            personal.tasks,
        )
        assertEquals(personal.tasks.single(), database.taskDao().getById("personal-task")?.toDomain())
        assertNull(database.taskDao().getById("personal-task")?.completedAtEpochMillis)
        assertExecutionLedger(
            entryId = "personal-entry",
            sourceCaptureId = "plan-source",
            taskIds = listOf("personal-task"),
            occurredAt = personalAt,
        )

        val dueDate = LocalDate.of(2026, 10, 2)
        val trabajoAt = Instant.parse("2026-09-23T15:01:00Z")
        val trabajo = executor(
            taskIds = listOf("trabajo-task"),
            entryIds = listOf("trabajo-entry"),
            times = listOf(trabajoAt),
        ).execute(
            plan(task("Review the release", TaskSpace.TRABAJO, dueDate)),
        ).requireExecuted()

        assertEquals(
            listOf(
                Task(
                    id = TaskId("trabajo-task"),
                    title = "Review the release",
                    space = TaskSpace.TRABAJO,
                    dueDate = dueDate,
                    completedAt = null,
                ),
            ),
            trabajo.tasks,
        )
        assertEquals(trabajo.tasks.single(), database.taskDao().getById("trabajo-task")?.toDomain())
        assertNull(database.taskDao().getById("trabajo-task")?.completedAtEpochMillis)
        assertExecutionLedger(
            entryId = "trabajo-entry",
            sourceCaptureId = "plan-source",
            taskIds = listOf("trabajo-task"),
            occurredAt = trabajoAt,
        )
        assertEquals(2, database.actionLedgerEntryDao().getRecent(50).size)
    }

    @Test
    fun multiTaskBatchPreservesOrderAllowsDuplicateTitlesAndWritesOneLedger() = runBlocking {
        openFreshDatabase()
        insertSourceCapture("  exact-source-id  ")
        val dueDate = LocalDate.of(2026, 10, 5)
        val occurredAt = Instant.parse("2026-09-23T15:02:03.456Z")
        val expectedIds = listOf("task-second", "task-first", "task-third")
        val duplicateTitle = "  same title  "
        val saved = executor(
            taskIds = expectedIds,
            entryIds = listOf("one-plan-entry"),
            times = listOf(occurredAt),
        ).execute(
            plan(
                task(duplicateTitle, TaskSpace.TRABAJO, dueDate),
                task(duplicateTitle, TaskSpace.PERSONAL, dueDate),
                task("without date", TaskSpace.PERSONAL),
                sourceCaptureId = "  exact-source-id  ",
            ),
        ).requireExecuted()

        assertEquals(expectedIds, saved.tasks.map { it.id.value })
        assertEquals(listOf(duplicateTitle, duplicateTitle, "without date"), saved.tasks.map(Task::title))
        assertEquals(
            listOf(TaskSpace.TRABAJO, TaskSpace.PERSONAL, TaskSpace.PERSONAL),
            saved.tasks.map(Task::space),
        )
        assertEquals(listOf(dueDate, dueDate, null), saved.tasks.map(Task::dueDate))
        assertTrue(saved.tasks.all { it.completedAt == null })
        expectedIds.forEachIndexed { index, id ->
            assertEquals(saved.tasks[index], database.taskDao().getById(id)?.toDomain())
        }
        assertEquals(1, database.actionLedgerEntryDao().getRecent(50).size)
        assertExecutionLedger(
            entryId = "one-plan-entry",
            sourceCaptureId = "  exact-source-id  ",
            taskIds = expectedIds,
            occurredAt = occurredAt,
        )
    }

    @Test
    fun everyUnsupportedActionFamilyRejectsWholePlanBeforeAnyMutation() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val taskIds = CountingTaskIdProvider()
        val entryIds = CountingEntryIdProvider()
        val clock = CountingClock()
        val action = RoomCapturePlanTaskExecutor(database, taskIds, entryIds, clock)
        val unsupportedActions = listOf(
            CapturePlanAction.AddListItem(BuiltInListDefinitions.COMPRAS.id, "list item"),
            CapturePlanAction.CreateNote("note"),
            CapturePlanAction.CreateStructuredLog(mapOf("field" to "value")),
            CapturePlanAction.UndoLast,
        )

        unsupportedActions.forEach { unsupported ->
            assertEquals(
                CapturePlanTaskExecutionResult.UnsupportedAction(1),
                action.execute(plan(task("supported first"), unsupported)),
            )
        }
        assertEquals(
            CapturePlanTaskExecutionResult.UnsupportedAction(0),
            action.execute(plan(unsupportedActions.first(), task("supported second"))),
        )

        assertEquals(0, taskIds.calls)
        assertEquals(0, entryIds.calls)
        assertEquals(0, clock.calls)
        assertTrue(database.taskDao().getAll().isEmpty())
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("unused-entry").isEmpty())
    }

    @Test
    fun overLimitTitleAndActionCountAreRejectedWithoutWrites() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val taskIds = CountingTaskIdProvider()
        val entryIds = CountingEntryIdProvider()
        val clock = CountingClock()
        val action = RoomCapturePlanTaskExecutor(database, taskIds, entryIds, clock)

        assertEquals(
            CapturePlanTaskExecutionResult.Rejected(
                actionIndex = 0,
                reason = CapturePlanTaskExecutionRejectionReason.TASK_TITLE_TOO_LONG,
            ),
            action.execute(
                plan(task("x".repeat(CapturePlanContract.MAX_TASK_TITLE_CHARS + 1))),
            ),
        )
        val tooManyActions = CapturePlan(
            sourceCaptureId = CaptureId("plan-source"),
            actions = List(CapturePlanContract.MAX_ACTIONS + 1) { task("task-$it") },
        )
        assertEquals(
            CapturePlanTaskExecutionResult.RejectedPlan(
                CapturePlanTaskPlanRejectionReason.TOO_MANY_ACTIONS,
            ),
            action.execute(tooManyActions),
        )

        assertEquals(0, taskIds.calls)
        assertEquals(0, entryIds.calls)
        assertEquals(0, clock.calls)
        assertTrue(database.taskDao().getAll().isEmpty())
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
    }

    @Test
    fun missingSourceCaptureWritesNothingAndDoesNotConsumeProviders() = runBlocking {
        openFreshDatabase()
        val taskIds = CountingTaskIdProvider()
        val entryIds = CountingEntryIdProvider()
        val clock = CountingClock()
        val action = RoomCapturePlanTaskExecutor(database, taskIds, entryIds, clock)

        assertEquals(
            CapturePlanTaskExecutionResult.MissingSourceCapture,
            action.execute(plan(task("capture must exist"))),
        )

        assertEquals(0, taskIds.calls)
        assertEquals(0, entryIds.calls)
        assertEquals(0, clock.calls)
        assertTrue(database.taskDao().getAll().isEmpty())
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
    }

    @Test
    fun firstTaskIdCollisionFailsWithoutChangingExistingOrUnrelatedState() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val existing = Task(
            id = TaskId("existing-task"),
            title = "  existing exact value  ",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 10, 9),
            completedAt = Instant.parse("2026-09-20T11:00:00Z"),
        )
        database.taskDao().insertStrict(existing.toEntity())
        insertUnrelatedDurableState()
        val tasksBefore = database.taskDao().getAll()
        val entriesBefore = database.actionLedgerEntryDao().getRecent(50)
        val unrelatedMutationsBefore = database.actionLedgerMutationDao()
            .getByEntryId("unrelated-entry")
        val sourceCaptureBefore = database.captureDao().getById("plan-source")

        val action = RoomCapturePlanTaskExecutor(
            database = database,
            taskIdProvider = QueueTaskIdProvider(listOf("existing-task", "new-task")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("first-collision-entry")),
            clock = QueueActionClock(listOf(Instant.parse("2026-09-23T15:05:00Z"))),
        )
        val result = action.execute(plan(task("must not persist"), task("also must not persist")))

        assertTrue(result is CapturePlanTaskExecutionResult.Failed)
        assertEquals(existing, database.taskDao().getById("existing-task")?.toDomain())
        assertNull(database.taskDao().getById("new-task"))
        assertEquals(tasksBefore, database.taskDao().getAll())
        assertEquals(entriesBefore, database.actionLedgerEntryDao().getRecent(50))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("first-collision-entry").isEmpty())
        assertEquals(unrelatedMutationsBefore, database.actionLedgerMutationDao().getByEntryId("unrelated-entry"))
        assertEquals(sourceCaptureBefore, database.captureDao().getById("plan-source"))
    }

    @Test
    fun duplicateGeneratedTaskIdsFailBeforeInsertionAndPreserveAllExistingState() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        insertUnrelatedDurableState()
        val taskIds = RepeatingTaskIdProvider("duplicate-task")
        val entryIds = CountingEntryIdProvider()
        val clock = CountingClock()
        val tasksBefore = database.taskDao().getAll()
        val entriesBefore = database.actionLedgerEntryDao().getRecent(50)
        val unrelatedMutationsBefore = database.actionLedgerMutationDao()
            .getByEntryId("unrelated-entry")
        val sourceCaptureBefore = database.captureDao().getById("plan-source")
        val action = RoomCapturePlanTaskExecutor(database, taskIds, entryIds, clock)

        val result = action.execute(plan(task("first plan task"), task("second plan task")))

        assertTrue(result is CapturePlanTaskExecutionResult.Failed)
        assertEquals(
            "Task ID provider returned duplicate IDs",
            (result as CapturePlanTaskExecutionResult.Failed).cause.message,
        )
        assertEquals(2, taskIds.calls)
        assertEquals(0, entryIds.calls)
        assertEquals(0, clock.calls)
        assertNull(database.taskDao().getById("duplicate-task"))
        assertEquals(tasksBefore, database.taskDao().getAll())
        assertEquals(entriesBefore, database.actionLedgerEntryDao().getRecent(50))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("duplicate-entry").isEmpty())
        assertEquals(unrelatedMutationsBefore, database.actionLedgerMutationDao().getByEntryId("unrelated-entry"))
        assertEquals(sourceCaptureBefore, database.captureDao().getById("plan-source"))
    }

    @Test
    fun laterTaskIdCollisionRollsBackEarlierInsertAndPreservesExistingTask() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val existing = Task(
            id = TaskId("existing-task"),
            title = "  existing exact value  ",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 10, 9),
            completedAt = Instant.parse("2026-09-20T11:00:00Z"),
        )
        database.taskDao().insertStrict(existing.toEntity())

        val result = executor(
            taskIds = listOf("first-new-task", "existing-task"),
            entryIds = listOf("collision-entry"),
            times = listOf(Instant.parse("2026-09-23T15:05:00Z")),
        ).execute(plan(task("new task"), task("must collide")))

        assertTrue(result is CapturePlanTaskExecutionResult.Failed)
        assertNull(database.taskDao().getById("first-new-task"))
        assertEquals(existing, database.taskDao().getById("existing-task")?.toDomain())
        assertEquals(1, database.taskDao().getAll().size)
        assertNull(database.actionLedgerEntryDao().getById("collision-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("collision-entry").isEmpty())
    }

    @Test
    fun parentLedgerIdCollisionRollsBackEveryInsertedTask() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = "existing-entry",
                occurredAtEpochMillis = 1234,
            ),
        )

        val result = executor(
            taskIds = listOf("new-task-one", "new-task-two"),
            entryIds = listOf("existing-entry"),
            times = listOf(Instant.parse("2026-09-23T15:06:00Z")),
        ).execute(plan(task("one"), task("two")))

        assertTrue(result is CapturePlanTaskExecutionResult.Failed)
        assertNull(database.taskDao().getById("new-task-one"))
        assertNull(database.taskDao().getById("new-task-two"))
        assertEquals(1234L, database.actionLedgerEntryDao().getById("existing-entry")?.occurredAtEpochMillis)
        assertTrue(database.actionLedgerMutationDao().getByEntryId("existing-entry").isEmpty())
    }

    @Test
    fun childMutationFailureRollsBackTasksParentAndEarlierChild() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        installTrigger(
            "CREATE TRIGGER fail_change029_child BEFORE INSERT ON action_ledger_mutations " +
                "WHEN NEW.action_ledger_entry_id = 'child-failure-entry' AND NEW.position = 1 " +
                "BEGIN SELECT RAISE(ABORT, 'forced CHG-029 child failure'); END",
        )

        val result = executor(
            taskIds = listOf("child-task-one", "child-task-two"),
            entryIds = listOf("child-failure-entry"),
            times = listOf(Instant.parse("2026-09-23T15:07:00Z")),
        ).execute(plan(task("one"), task("two")))

        assertTrue(result is CapturePlanTaskExecutionResult.Failed)
        assertNull(database.taskDao().getById("child-task-one"))
        assertNull(database.taskDao().getById("child-task-two"))
        assertNull(database.actionLedgerEntryDao().getById("child-failure-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("child-failure-entry").isEmpty())
    }

    @Test
    fun cancellationAfterTaskInsertsPropagatesAndRollsBackCompletePlan() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val action = RoomCapturePlanTaskExecutor(
            database = database,
            taskIdProvider = QueueTaskIdProvider(listOf("cancel-task-one", "cancel-task-two")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("cancel-entry")),
            clock = ActionLedgerClock { throw CancellationException("cancel after Task inserts") },
        )
        var cancellationCaught = false
        try {
            action.execute(plan(task("one"), task("two")))
        } catch (_: CancellationException) {
            cancellationCaught = true
        }

        assertTrue(cancellationCaught)
        assertNull(database.taskDao().getById("cancel-task-one"))
        assertNull(database.taskDao().getById("cancel-task-two"))
        assertNull(database.actionLedgerEntryDao().getById("cancel-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("cancel-entry").isEmpty())
    }

    @Test
    fun targetedUndoPreservesUnrelatedTasksCaptureAndSurvivesReopen() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val unrelated = Task(
            id = TaskId("unrelated-task"),
            title = "keep this Task",
            space = TaskSpace.PERSONAL,
        )
        database.taskDao().insertStrict(unrelated.toEntity())
        val saved = executor(
            taskIds = listOf("undo-task-one", "undo-task-two"),
            entryIds = listOf("undo-entry"),
            times = listOf(Instant.parse("2026-09-23T15:09:00Z")),
        ).execute(plan(task("one"), task("two"))).requireExecuted()

        val undone = executor(
            times = listOf(Instant.parse("2026-09-23T15:10:00Z")),
        ).undoExecution(saved.actionLedgerEntryId, saved.tasks.map(Task::id))
        assertEquals(
            UndoCapturePlanTaskExecutionResult.Undone(
                actionLedgerEntryId = saved.actionLedgerEntryId,
                taskIds = listOf(TaskId("undo-task-one"), TaskId("undo-task-two")),
            ),
            undone,
        )
        assertNull(database.taskDao().getById("undo-task-one"))
        assertNull(database.taskDao().getById("undo-task-two"))
        assertEquals(unrelated, database.taskDao().getById("unrelated-task")?.toDomain())
        assertNotNull(database.captureDao().getById("plan-source"))
        assertNotNull(database.actionLedgerEntryDao().getById("undo-entry")?.undoneAtEpochMillis)

        reopenDatabase()
        assertNull(database.taskDao().getById("undo-task-one"))
        assertNull(database.taskDao().getById("undo-task-two"))
        assertEquals(unrelated, database.taskDao().getById("unrelated-task")?.toDomain())
        assertNotNull(database.captureDao().getById("plan-source"))
        assertNotNull(database.actionLedgerEntryDao().getById("undo-entry")?.undoneAtEpochMillis)
        assertEquals(
            UndoCapturePlanTaskExecutionResult.AlreadyUndone,
            executor().undoExecution(saved.actionLedgerEntryId, saved.tasks.map(Task::id)),
        )
    }

    @Test
    fun undoRejectsMissingLedgerMismatchedIdsOrderAndMissingTargetWithoutPartialDelete() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val saved = executor(
            taskIds = listOf("target-one", "target-two"),
            entryIds = listOf("target-entry"),
            times = listOf(Instant.parse("2026-09-23T15:11:00Z")),
        ).execute(plan(task("one"), task("two"))).requireExecuted()
        val ids = saved.tasks.map(Task::id)

        assertEquals(
            UndoCapturePlanTaskExecutionResult.MissingLedgerEntry,
            executor().undoExecution(ActionLedgerEntryId("missing-entry"), ids),
        )
        assertEquals(
            UndoCapturePlanTaskExecutionResult.TargetMismatch,
            executor().undoExecution(saved.actionLedgerEntryId, listOf(TaskId("wrong-one"), ids[1])),
        )
        assertEquals(
            UndoCapturePlanTaskExecutionResult.TargetMismatch,
            executor().undoExecution(saved.actionLedgerEntryId, ids.reversed()),
        )
        assertEquals(
            UndoCapturePlanTaskExecutionResult.TargetMismatch,
            executor().undoExecution(saved.actionLedgerEntryId, ids.take(1)),
        )
        assertTrue(ids.all { database.taskDao().getById(it.value) != null })
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)

        assertEquals(1, database.taskDao().deleteById(ids.first().value))
        assertEquals(
            UndoCapturePlanTaskExecutionResult.TargetMissing(taskIndex = 0),
            executor().undoExecution(saved.actionLedgerEntryId, ids),
        )
        assertNull(database.taskDao().getById(ids.first().value))
        assertNotNull(database.taskDao().getById(ids[1].value))
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    @Test
    fun malformedLedgerProvenancePositionsOperationsTypesVersionsPayloadsAndTargetsRejectUndo() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()

        assertMalformedLedger(
            entryId = "null-provenance",
            sourceCaptureId = null,
            persistedTaskIds = listOf("null-provenance-task"),
            mutations = listOf(mutation("null-provenance", 0, "null-provenance-task")),
            expectedTaskIds = listOf("null-provenance-task"),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape,
        )
        assertMalformedLedger(
            entryId = "empty-mutations",
            persistedTaskIds = listOf("empty-task"),
            mutations = emptyList(),
            expectedTaskIds = listOf("empty-task"),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape,
        )
        assertMalformedLedger(
            entryId = "gapped-positions",
            persistedTaskIds = listOf("gap-one", "gap-two"),
            mutations = listOf(
                mutation("gapped-positions", 0, "gap-one"),
                mutation("gapped-positions", 2, "gap-two"),
            ),
            expectedTaskIds = listOf("gap-one", "gap-two"),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape,
        )
        assertMalformedLedger(
            entryId = "wrong-operation",
            persistedTaskIds = listOf("wrong-operation-task"),
            mutations = listOf(
                mutation("wrong-operation", 0, "wrong-operation-task", operation = "DELETE"),
            ),
            expectedTaskIds = listOf("wrong-operation-task"),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedAction(0),
        )
        assertMalformedLedger(
            entryId = "wrong-target-type",
            persistedTaskIds = listOf("wrong-type-task"),
            mutations = listOf(
                mutation("wrong-target-type", 0, "wrong-type-task", targetType = "list_item"),
            ),
            expectedTaskIds = listOf("wrong-type-task"),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedAction(0),
        )
        assertMalformedLedger(
            entryId = "wrong-version",
            persistedTaskIds = listOf("wrong-version-task"),
            mutations = listOf(
                mutation("wrong-version", 0, "wrong-version-task", payloadVersion = 2),
            ),
            expectedTaskIds = listOf("wrong-version-task"),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape,
        )
        assertMalformedLedger(
            entryId = "before-payload",
            persistedTaskIds = listOf("before-payload-task"),
            mutations = listOf(
                mutation("before-payload", 0, "before-payload-task", beforeState = "pending"),
            ),
            expectedTaskIds = listOf("before-payload-task"),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape,
        )
        assertMalformedLedger(
            entryId = "after-payload",
            persistedTaskIds = listOf("after-payload-task"),
            mutations = listOf(
                mutation("after-payload", 0, "after-payload-task", afterState = "created"),
            ),
            expectedTaskIds = listOf("after-payload-task"),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape,
        )
        assertMalformedLedger(
            entryId = "blank-target",
            mutations = listOf(mutation("blank-target", 0, "")),
            expectedTaskIds = listOf(""),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape,
        )
        assertMalformedLedger(
            entryId = "duplicate-targets",
            persistedTaskIds = listOf("duplicate-target"),
            mutations = listOf(
                mutation("duplicate-targets", 0, "duplicate-target"),
                mutation("duplicate-targets", 1, "duplicate-target"),
            ),
            expectedTaskIds = listOf("duplicate-target", "duplicate-target"),
            expectedResult = UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape,
        )
    }

    @Test
    fun deleteFailureAfterEarlierDeleteRollsBackWholeUndo() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val saved = executor(
            taskIds = listOf("delete-one", "delete-two"),
            entryIds = listOf("delete-entry"),
            times = listOf(Instant.parse("2026-09-23T15:12:00Z")),
        ).execute(plan(task("one"), task("two"))).requireExecuted()
        installTrigger(
            "CREATE TRIGGER fail_change029_delete BEFORE DELETE ON tasks " +
                "WHEN OLD.id = 'delete-two' " +
                "BEGIN SELECT RAISE(ABORT, 'forced CHG-029 later delete failure'); END",
        )

        val result = executor(times = listOf(Instant.parse("2026-09-23T15:13:00Z")))
            .undoExecution(saved.actionLedgerEntryId, saved.tasks.map(Task::id))

        assertTrue(result is UndoCapturePlanTaskExecutionResult.Failed)
        assertNotNull(database.taskDao().getById("delete-one"))
        assertNotNull(database.taskDao().getById("delete-two"))
        assertNull(database.actionLedgerEntryDao().getById("delete-entry")?.undoneAtEpochMillis)
    }

    @Test
    fun markUndoneFailureRollsBackEveryTaskDelete() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val saved = executor(
            taskIds = listOf("mark-one", "mark-two"),
            entryIds = listOf("mark-entry"),
            times = listOf(Instant.parse("2026-09-23T15:14:00Z")),
        ).execute(plan(task("one"), task("two"))).requireExecuted()
        installTrigger(
            "CREATE TRIGGER fail_change029_mark BEFORE UPDATE OF undone_at_epoch_millis " +
                "ON action_ledger_entries WHEN OLD.id = 'mark-entry' " +
                "BEGIN SELECT RAISE(ABORT, 'forced CHG-029 mark failure'); END",
        )

        val result = executor(times = listOf(Instant.parse("2026-09-23T15:15:00Z")))
            .undoExecution(saved.actionLedgerEntryId, saved.tasks.map(Task::id))

        assertTrue(result is UndoCapturePlanTaskExecutionResult.Failed)
        assertNotNull(database.taskDao().getById("mark-one"))
        assertNotNull(database.taskDao().getById("mark-two"))
        assertNull(database.actionLedgerEntryDao().getById("mark-entry")?.undoneAtEpochMillis)
    }

    @Test
    fun undoCancellationAfterDeletesPropagatesAndRestoresEveryTask() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val saved = executor(
            taskIds = listOf("cancel-undo-one", "cancel-undo-two"),
            entryIds = listOf("cancel-undo-entry"),
            times = listOf(Instant.parse("2026-09-23T15:16:00Z")),
        ).execute(plan(task("one"), task("two"))).requireExecuted()
        val cancellingUndo = RoomCapturePlanTaskExecutor(
            database = database,
            clock = ActionLedgerClock { throw CancellationException("cancel after Task deletes") },
        )

        var cancellationCaught = false
        try {
            cancellingUndo.undoExecution(saved.actionLedgerEntryId, saved.tasks.map(Task::id))
        } catch (_: CancellationException) {
            cancellationCaught = true
        }

        assertTrue(cancellationCaught)
        assertNotNull(database.taskDao().getById("cancel-undo-one"))
        assertNotNull(database.taskDao().getById("cancel-undo-two"))
        assertNull(database.actionLedgerEntryDao().getById("cancel-undo-entry")?.undoneAtEpochMillis)
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun reopenDatabase() {
        database.close()
        openFreshDatabase()
    }

    private suspend fun insertSourceCapture(id: String = "plan-source") {
        database.captureDao().insert(
            Capture(
                id = CaptureId(id),
                originalInput = CaptureInput.Text("original capture"),
                capturedAt = Instant.parse("2026-09-23T14:00:00Z"),
            ).toEntity(),
        )
    }

    private suspend fun insertTask(
        id: String,
        title: String = id,
        space: TaskSpace = TaskSpace.PERSONAL,
        dueDate: LocalDate? = null,
    ) {
        database.taskDao().insertStrict(
            Task(
                id = TaskId(id),
                title = title,
                space = space,
                dueDate = dueDate,
                completedAt = null,
            ).toEntity(),
        )
    }

    private suspend fun insertUnrelatedDurableState() {
        insertTask(
            id = "unrelated-task",
            title = "unrelated exact task",
            space = TaskSpace.PERSONAL,
            dueDate = LocalDate.of(2026, 11, 1),
        )
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = "unrelated-entry",
                occurredAtEpochMillis = 1_234,
                sourceCaptureId = "plan-source",
            ),
        )
        database.actionLedgerMutationDao().insertAll(
            listOf(mutation("unrelated-entry", 0, "unrelated-task")),
        )
    }

    private fun executor(
        taskIds: List<String> = emptyList(),
        entryIds: List<String> = emptyList(),
        times: List<Instant> = emptyList(),
    ): CapturePlanTaskExecutor = RoomCapturePlanTaskExecutor(
        database = database,
        taskIdProvider = QueueTaskIdProvider(taskIds),
        actionLedgerIdProvider = QueueEntryIdProvider(entryIds),
        clock = QueueActionClock(times),
    )

    private fun plan(
        vararg actions: CapturePlanAction,
        sourceCaptureId: String = "plan-source",
    ): CapturePlan = CapturePlan(
        sourceCaptureId = CaptureId(sourceCaptureId),
        actions = actions.toList(),
    )

    private fun task(
        title: String,
        space: TaskSpace = TaskSpace.PERSONAL,
        dueDate: LocalDate? = null,
    ): CapturePlanAction.CreateTask = CapturePlanAction.CreateTask(
        space = space,
        title = title,
        dueDate = dueDate,
    )

    private suspend fun assertExecutionLedger(
        entryId: String,
        sourceCaptureId: String,
        taskIds: List<String>,
        occurredAt: Instant,
    ) {
        val entry = database.actionLedgerEntryDao().getById(entryId)
        assertNotNull(entry)
        assertEquals(occurredAt.toEpochMilli(), entry?.occurredAtEpochMillis)
        assertEquals(sourceCaptureId, entry?.sourceCaptureId)
        assertNull(entry?.undoneAtEpochMillis)

        val mutations = database.actionLedgerMutationDao().getByEntryId(entryId)
        assertEquals(taskIds.size, mutations.size)
        mutations.forEachIndexed { index, mutation ->
            assertEquals(index, mutation.position)
            assertEquals(ActionLedgerOperation.CREATE.name, mutation.operation)
            assertEquals(TASK_ACTION_LEDGER_TARGET_TYPE, mutation.targetType)
            assertEquals(taskIds[index], mutation.targetId)
            assertEquals(TASK_CREATE_ACTION_PAYLOAD_VERSION, mutation.payloadVersion)
            assertNull(mutation.beforeState)
            assertNull(mutation.afterState)
        }
    }

    private suspend fun assertMalformedLedger(
        entryId: String,
        sourceCaptureId: String? = "plan-source",
        persistedTaskIds: List<String> = emptyList(),
        mutations: List<ActionLedgerMutationEntity>,
        expectedTaskIds: List<String>,
        expectedResult: UndoCapturePlanTaskExecutionResult,
    ) {
        persistedTaskIds.forEach { id -> insertTask(id) }
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = entryId,
                occurredAtEpochMillis = 10_000,
                sourceCaptureId = sourceCaptureId,
            ),
        )
        if (mutations.isNotEmpty()) {
            database.actionLedgerMutationDao().insertAll(mutations)
        }

        assertEquals(
            expectedResult,
            executor().undoExecution(
                ActionLedgerEntryId(entryId),
                expectedTaskIds.map(::TaskId),
            ),
        )
        persistedTaskIds.forEach { id -> assertNotNull(database.taskDao().getById(id)) }
        assertNull(database.actionLedgerEntryDao().getById(entryId)?.undoneAtEpochMillis)
    }

    private fun mutation(
        entryId: String,
        position: Int,
        targetId: String,
        operation: String = ActionLedgerOperation.CREATE.name,
        targetType: String = TASK_ACTION_LEDGER_TARGET_TYPE,
        payloadVersion: Int = TASK_CREATE_ACTION_PAYLOAD_VERSION,
        beforeState: String? = null,
        afterState: String? = null,
    ): ActionLedgerMutationEntity = ActionLedgerMutationEntity(
        actionLedgerEntryId = entryId,
        position = position,
        operation = operation,
        targetType = targetType,
        targetId = targetId,
        payloadVersion = payloadVersion,
        beforeState = beforeState,
        afterState = afterState,
    )

    private fun installTrigger(sql: String) {
        database.close()
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(sql)
        }
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private fun CapturePlanTaskExecutionResult.requireExecuted():
        CapturePlanTaskExecutionResult.Executed =
        this as? CapturePlanTaskExecutionResult.Executed
            ?: throw AssertionError("Expected CapturePlan Task execution success, got $this")

    private class QueueTaskIdProvider(ids: List<String>) : TaskIdProvider {
        private val ids = ArrayDeque(ids)
        override fun nextTaskId(): TaskId = TaskId(ids.removeFirst())
    }

    private class RepeatingTaskIdProvider(
        private val id: String,
    ) : TaskIdProvider {
        var calls: Int = 0
            private set

        override fun nextTaskId(): TaskId = TaskId(id).also { calls++ }
    }

    private class QueueEntryIdProvider(ids: List<String>) : ActionLedgerIdProvider {
        private val ids = ArrayDeque(ids)
        override fun nextEntryId(): ActionLedgerEntryId = ActionLedgerEntryId(ids.removeFirst())
    }

    private class QueueActionClock(times: List<Instant>) : ActionLedgerClock {
        private val times = ArrayDeque(times)
        override fun now(): Instant = times.removeFirst()
    }

    private class CountingTaskIdProvider : TaskIdProvider {
        var calls: Int = 0
            private set
        override fun nextTaskId(): TaskId = TaskId("unused-task-${calls++}")
    }

    private class CountingEntryIdProvider : ActionLedgerIdProvider {
        var calls: Int = 0
            private set
        override fun nextEntryId(): ActionLedgerEntryId = ActionLedgerEntryId("unused-entry-${calls++}")
    }

    private class CountingClock : ActionLedgerClock {
        var calls: Int = 0
            private set
        override fun now(): Instant = Instant.parse("2026-09-23T12:00:00Z").also { calls++ }
    }
}

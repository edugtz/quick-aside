package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.capture.CaptureExecutionOutcome
import com.edu.quickaside.application.capture.CaptureInterpretationResult
import com.edu.quickaside.application.capture.CaptureInterpreter
import com.edu.quickaside.application.capture.CapturePlanTaskExecutor
import com.edu.quickaside.application.capture.CapturePlanTaskExecutionResult
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureSubmissionResult
import com.edu.quickaside.application.capture.UndoCapturePlanTaskExecutionResult
import com.edu.quickaside.application.tasks.TASK_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.tasks.TASK_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.tasks.TaskIdProvider
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
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
class CaptureSubmissionTaskExecutionDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-030-capture-task-pipeline-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun singleTaskSubmissionPersistsCaptureExactTaskAndSourceLinkedLedger() = runBlocking {
        val captureId = CaptureId("task-pipeline-single")
        val dueDate = LocalDate.of(2026, 10, 2)
        val occurredAt = Instant.parse("2026-09-24T20:00:00Z")
        val taskExecutor = executor(
            taskIds = listOf("single-task"),
            entryIds = listOf("single-entry"),
            times = listOf(occurredAt),
        )

        val saved = submission(
            captureId = captureId,
            actions = listOf(task("Revisar release", TaskSpace.TRABAJO, dueDate)),
            taskExecutor = taskExecutor,
        ).submit("Revisar release el 2 de octubre") as CaptureSubmissionResult.Saved

        val receipt = (saved.execution as CaptureExecutionOutcome.Executed.Tasks).receipt
        val expectedTask = Task(
            id = TaskId("single-task"),
            title = "Revisar release",
            space = TaskSpace.TRABAJO,
            dueDate = dueDate,
            completedAt = null,
        )

        assertNotNull(database.captureDao().getById(captureId.value))
        assertEquals(listOf(expectedTask), receipt.tasks)
        assertEquals(expectedTask, database.taskDao().getById("single-task")?.toDomain())
        assertNull(database.taskDao().getById("single-task")?.completedAtEpochMillis)
        assertExecutionLedger(
            entryId = "single-entry",
            sourceCaptureId = captureId.value,
            taskIds = listOf("single-task"),
            occurredAt = occurredAt,
        )
    }

    @Test
    fun multiTaskSubmissionPreservesActionOrderFieldsAndOneLedger() = runBlocking {
        val captureId = CaptureId("task-pipeline-batch")
        val dueDate = LocalDate.of(2026, 10, 5)
        val occurredAt = Instant.parse("2026-09-24T20:01:00Z")
        val taskExecutor = executor(
            taskIds = listOf("task-second", "task-first", "task-third"),
            entryIds = listOf("batch-entry"),
            times = listOf(occurredAt),
        )

        val saved = submission(
            captureId = captureId,
            actions = listOf(
                task("same title", TaskSpace.TRABAJO, dueDate),
                task("same title", TaskSpace.PERSONAL, dueDate),
                task("without date", TaskSpace.PERSONAL),
            ),
            taskExecutor = taskExecutor,
        ).submitVoice("Guarda tres pendientes") as CaptureSubmissionResult.Saved

        val receipt = (saved.execution as CaptureExecutionOutcome.Executed.Tasks).receipt
        assertEquals(
            listOf("task-second", "task-first", "task-third"),
            receipt.tasks.map { it.id.value },
        )
        assertEquals(
            listOf("same title", "same title", "without date"),
            receipt.tasks.map(Task::title),
        )
        assertEquals(
            listOf(TaskSpace.TRABAJO, TaskSpace.PERSONAL, TaskSpace.PERSONAL),
            receipt.tasks.map(Task::space),
        )
        assertEquals(listOf(dueDate, dueDate, null), receipt.tasks.map(Task::dueDate))
        assertTrue(receipt.tasks.all { it.completedAt == null })
        assertEquals(1, database.actionLedgerEntryDao().getRecent(50).size)
        assertExecutionLedger(
            entryId = "batch-entry",
            sourceCaptureId = captureId.value,
            taskIds = receipt.tasks.map { it.id.value },
            occurredAt = occurredAt,
        )
    }

    @Test
    fun mixedListAndTaskSubmissionRetainsCaptureAndCreatesNoMutation() = runBlocking {
        val captureId = CaptureId("task-pipeline-mixed")
        val taskExecutor = executor()
        val saved = submission(
            captureId = captureId,
            actions = listOf(
                CapturePlanAction.AddListItem(BuiltInListDefinitions.COMPRAS.id, "leche"),
                task("Pagar luz"),
            ),
            taskExecutor = taskExecutor,
        ).submit("Compra leche y paga la luz") as CaptureSubmissionResult.Saved

        assertEquals(CaptureExecutionOutcome.NotEligible, saved.execution)
        assertNotNull(database.captureDao().getById(captureId.value))
        assertTrue(database.taskDao().getAll().isEmpty())
        assertTrue(
            database.listItemDao()
                .getContinuousByDefinitionId(BuiltInListDefinitions.COMPRAS.id.value)
                .isEmpty(),
        )
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
    }

    @Test
    fun exactTaskBatchUndoDeletesOnlyReturnedTasksAndPreservesUnrelatedState() = runBlocking {
        val captureId = CaptureId("task-pipeline-undo")
        val unrelatedTask = Task(
            id = TaskId("unrelated-task"),
            title = "Keep this task",
            space = TaskSpace.PERSONAL,
            dueDate = LocalDate.of(2026, 11, 1),
            completedAt = null,
        )
        database.taskDao().insertStrict(unrelatedTask.toEntity())
        database.listItemDao().insert(
            ListItemEntity(
                id = "unrelated-item",
                listDefinitionId = BuiltInListDefinitions.COMPRAS.id.value,
                listSessionId = null,
                text = "Keep this item",
                isCompleted = false,
                createdAtEpochMillis = 1_000,
            ),
        )
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = "unrelated-entry",
                occurredAtEpochMillis = 1_001,
                sourceCaptureId = null,
            ),
        )
        database.actionLedgerMutationDao().insertAll(
            listOf(
                ActionLedgerMutationEntity(
                    actionLedgerEntryId = "unrelated-entry",
                    position = 0,
                    operation = ActionLedgerOperation.CREATE.name,
                    targetType = TASK_ACTION_LEDGER_TARGET_TYPE,
                    targetId = unrelatedTask.id.value,
                    payloadVersion = TASK_CREATE_ACTION_PAYLOAD_VERSION,
                    beforeState = null,
                    afterState = null,
                ),
            ),
        )

        val taskExecutor = executor(
            taskIds = listOf("undo-task-one", "undo-task-two"),
            entryIds = listOf("undo-entry"),
            times = listOf(
                Instant.parse("2026-09-24T20:02:00Z"),
                Instant.parse("2026-09-24T20:03:00Z"),
            ),
        )
        val saved = submission(
            captureId = captureId,
            actions = listOf(task("one"), task("two")),
            taskExecutor = taskExecutor,
        ).submit("Guarda dos pendientes") as CaptureSubmissionResult.Saved
        val receipt = (saved.execution as CaptureExecutionOutcome.Executed.Tasks).receipt

        assertEquals(
            UndoCapturePlanTaskExecutionResult.Undone(
                actionLedgerEntryId = receipt.actionLedgerEntryId,
                taskIds = receipt.tasks.map(Task::id),
            ),
            taskExecutor.undoExecution(
                actionLedgerEntryId = receipt.actionLedgerEntryId,
                expectedTaskIds = receipt.tasks.map(Task::id),
            ),
        )
        receipt.tasks.forEach { task -> assertNull(database.taskDao().getById(task.id.value)) }
        assertEquals(unrelatedTask, database.taskDao().getById(unrelatedTask.id.value)?.toDomain())
        assertNotNull(database.listItemDao().getById("unrelated-item"))
        assertNotNull(database.actionLedgerEntryDao().getById("unrelated-entry"))
        assertNotNull(database.actionLedgerEntryDao().getById("undo-entry")?.undoneAtEpochMillis)
        assertNotNull(database.captureDao().getById(captureId.value))
        assertEquals(
            UndoCapturePlanTaskExecutionResult.AlreadyUndone,
            taskExecutor.undoExecution(receipt.actionLedgerEntryId, receipt.tasks.map(Task::id)),
        )
    }

    private fun submission(
        captureId: CaptureId,
        actions: List<CapturePlanAction>,
        taskExecutor: CapturePlanTaskExecutor,
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
        taskExecutor = taskExecutor,
        idProvider = { captureId },
        capturedAtProvider = { Instant.parse("2026-09-24T20:00:00Z") },
    )

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

    private fun task(
        title: String,
        space: TaskSpace = TaskSpace.PERSONAL,
        dueDate: LocalDate? = null,
    ) = CapturePlanAction.CreateTask(
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

    private class QueueTaskIdProvider(ids: List<String>) : TaskIdProvider {
        private val values = ArrayDeque(ids.map(::TaskId))
        override fun nextTaskId(): TaskId = values.removeFirst()
    }

    private class QueueEntryIdProvider(ids: List<String>) : ActionLedgerIdProvider {
        private val values = ArrayDeque(ids.map(::ActionLedgerEntryId))
        override fun nextEntryId(): ActionLedgerEntryId = values.removeFirst()
    }

    private class QueueActionClock(times: List<Instant>) : ActionLedgerClock {
        private val values = ArrayDeque(times)
        override fun now(): Instant = values.removeFirst()
    }
}

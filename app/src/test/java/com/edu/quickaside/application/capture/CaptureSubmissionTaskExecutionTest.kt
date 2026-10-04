package com.edu.quickaside.application.capture

import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CaptureSubmissionTaskExecutionTest {
    private val capturedAt = Instant.parse("2026-09-24T18:00:00Z")
    private val captureId = CaptureId("capture-030")

    @Test
    fun allCreateTaskPlanExecutesExactlyOnceAfterPersistenceAndInterpretation() = runBlocking {
        val events = mutableListOf<String>()
        val plan = plan(
            task("Revisar PR", TaskSpace.TRABAJO, LocalDate.of(2026, 9, 25)),
        )
        val receipt = executedTaskReceipt("task-ledger", "task-1")
        val executor = RecordingTaskExecutor(receipt) { events += "execute" }
        val submission = submission(
            writer = CaptureWriter { events += "persist" },
            interpreter = CaptureInterpreter {
                events += "interpret"
                CaptureInterpretationResult.Success(plan)
            },
            taskExecutor = executor,
        )

        val saved = submission.submit("Revisar PR mañana") as CaptureSubmissionResult.Saved

        assertEquals(listOf("persist", "interpret", "execute"), events)
        assertEquals(1, executor.executeCalls)
        assertSame(plan, executor.executedPlans.single())
        assertEquals(CaptureExecutionOutcome.Executed.Tasks(receipt), saved.execution)
    }

    @Test
    fun multiTaskReceiptPreservesOrderedTaskAndLedgerIdentity() = runBlocking {
        val receipt = executedTaskReceipt(
            "task-ledger-batch",
            "task-second",
            "task-first",
            "task-third",
        )
        val executor = RecordingTaskExecutor(receipt)
        val saved = submission(
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(
                    plan(
                        task("Segundo", TaskSpace.TRABAJO),
                        task("Primero", TaskSpace.PERSONAL),
                        task("Tercero", TaskSpace.PERSONAL),
                    ),
                )
            },
            taskExecutor = executor,
        ).submitVoice("Guarda tres pendientes") as CaptureSubmissionResult.Saved

        val execution = saved.execution as CaptureExecutionOutcome.Executed.Tasks
        assertEquals(1, executor.executeCalls)
        assertEquals(ActionLedgerEntryId("task-ledger-batch"), execution.receipt.actionLedgerEntryId)
        assertEquals(
            listOf(TaskId("task-second"), TaskId("task-first"), TaskId("task-third")),
            execution.receipt.tasks.map(Task::id),
        )
    }

    @Test
    fun allListPlanStillExecutesOnlyTheListExecutor() = runBlocking {
        val listReceipt = CapturePlanListExecutionResult.Executed(
            items = listOf(
                ListItem(
                    id = ListItemId("item-1"),
                    listDefinitionId = ListDefinitionId("compras"),
                    text = "leche",
                    listSessionId = null,
                    isCompleted = false,
                    createdAt = capturedAt,
                ),
            ),
            actionLedgerEntryId = ActionLedgerEntryId("list-ledger"),
        )
        val listExecutor = RecordingListExecutor(listReceipt)
        val taskExecutor = RecordingTaskExecutor(executedTaskReceipt("unused", "unused"))
        val saved = submission(
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(
                    plan(CapturePlanAction.AddListItem(ListDefinitionId("compras"), "leche")),
                )
            },
            listExecutor = listExecutor,
            taskExecutor = taskExecutor,
        ).submit("Compra leche") as CaptureSubmissionResult.Saved

        assertEquals(1, listExecutor.executeCalls)
        assertEquals(0, taskExecutor.executeCalls)
        assertEquals(CaptureExecutionOutcome.Executed.ListItems(listReceipt), saved.execution)
    }

    @Test
    fun mixedListAndTaskPlanCallsNeitherExecutor() = runBlocking {
        val listExecutor = RecordingListExecutor()
        val taskExecutor = RecordingTaskExecutor(executedTaskReceipt("unused", "unused"))
        val saved = submission(
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(
                    plan(
                        CapturePlanAction.AddListItem(ListDefinitionId("compras"), "leche"),
                        task("Pagar luz"),
                    ),
                )
            },
            listExecutor = listExecutor,
            taskExecutor = taskExecutor,
        ).submit("Compra leche y paga la luz") as CaptureSubmissionResult.Saved

        assertEquals(CaptureExecutionOutcome.NotEligible, saved.execution)
        assertEquals(0, listExecutor.executeCalls)
        assertEquals(0, taskExecutor.executeCalls)
    }

    @Test
    fun unsupportedActionFamilyCallsNeitherExecutor() = runBlocking {
        val listExecutor = RecordingListExecutor()
        val taskExecutor = RecordingTaskExecutor(executedTaskReceipt("unused", "unused"))
        val saved = submission(
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(
                    plan(CapturePlanAction.UndoLast),
                )
            },
            listExecutor = listExecutor,
            taskExecutor = taskExecutor,
        ).submit("Guardar como nota") as CaptureSubmissionResult.Saved

        assertEquals(CaptureExecutionOutcome.NotEligible, saved.execution)
        assertEquals(0, listExecutor.executeCalls)
        assertEquals(0, taskExecutor.executeCalls)
    }

    @Test
    fun missingTaskExecutorReturnsFailedTasksAndKeepsCapturePersisted() = runBlocking {
        val savedCaptures = mutableListOf<Capture>()
        val saved = submission(
            writer = CaptureWriter(savedCaptures::add),
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(plan(task("Persistido")))
            },
        ).submit("Persistido") as CaptureSubmissionResult.Saved

        val execution = saved.execution as CaptureExecutionOutcome.Failed.Tasks
        assertTrue(execution.result.cause is IllegalStateException)
        assertEquals(listOf(captureId), savedCaptures.map(Capture::id))
    }

    @Test
    fun everyAllowedTaskRejectionMapsToRejectedTasksAndKeepsCapturePersisted() = runBlocking {
        val results = listOf<CapturePlanTaskExecutionResult>(
            CapturePlanTaskExecutionResult.UnsupportedAction(actionIndex = 0),
            CapturePlanTaskExecutionResult.Rejected(
                actionIndex = 0,
                reason = CapturePlanTaskExecutionRejectionReason.BLANK_TITLE,
            ),
            CapturePlanTaskExecutionResult.RejectedPlan(
                CapturePlanTaskPlanRejectionReason.EMPTY_ACTIONS,
            ),
            CapturePlanTaskExecutionResult.MissingSourceCapture,
        )

        results.forEachIndexed { index, result ->
            val savedCaptures = mutableListOf<Capture>()
            val saved = submission(
                writer = CaptureWriter(savedCaptures::add),
                interpreter = CaptureInterpreter {
                    CaptureInterpretationResult.Success(plan(task("Rechazado $index")))
                },
                taskExecutor = RecordingTaskExecutor(result),
                id = CaptureId("rejection-$index"),
            ).submit("Rechazado $index") as CaptureSubmissionResult.Saved

            assertEquals(
                CaptureExecutionOutcome.Rejected.Tasks(result),
                saved.execution,
            )
            assertEquals(1, savedCaptures.size)
        }
    }

    @Test
    fun taskFailureMapsToFailedTasksAndKeepsCapturePersisted() = runBlocking {
        val failure = CapturePlanTaskExecutionResult.Failed(
            IllegalStateException("task write failed"),
        )
        val savedCaptures = mutableListOf<Capture>()
        val saved = submission(
            writer = CaptureWriter(savedCaptures::add),
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(plan(task("Falla")))
            },
            taskExecutor = RecordingTaskExecutor(failure),
        ).submit("Falla") as CaptureSubmissionResult.Saved

        assertEquals(CaptureExecutionOutcome.Failed.Tasks(failure), saved.execution)
        assertEquals(listOf(captureId), savedCaptures.map(Capture::id))
    }

    @Test
    fun taskCancellationPropagatesAfterCapturePersistence() = runBlocking {
        val savedCaptures = mutableListOf<Capture>()
        val cancellation = CancellationException("cancel task execution")
        val executor = object : CapturePlanTaskExecutor {
            override suspend fun execute(plan: CapturePlan): CapturePlanTaskExecutionResult {
                throw cancellation
            }

            override suspend fun undoExecution(
                actionLedgerEntryId: ActionLedgerEntryId,
                expectedTaskIds: List<TaskId>,
            ): UndoCapturePlanTaskExecutionResult = error("Undo is not used")
        }
        val submission = submission(
            writer = CaptureWriter(savedCaptures::add),
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(plan(task("Cancelar")))
            },
            taskExecutor = executor,
        )

        try {
            submission.submit("Cancelar")
            fail("CancellationException expected")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(listOf(captureId), savedCaptures.map(Capture::id))
    }

    @Test
    fun resultModelAllowsOnlyFamilySpecificPayloads() {
        val listRejected = listOf<CapturePlanListExecutionResult>(
            CapturePlanListExecutionResult.UnsupportedAction(0),
            CapturePlanListExecutionResult.Rejected(
                0,
                CapturePlanListExecutionRejectionReason.NO_ACTIVE_SESSION,
            ),
            CapturePlanListExecutionResult.MissingSourceCapture,
        )
        listRejected.forEach { result -> CaptureExecutionOutcome.Rejected.ListItems(result) }

        val taskRejected = listOf<CapturePlanTaskExecutionResult>(
            CapturePlanTaskExecutionResult.UnsupportedAction(0),
            CapturePlanTaskExecutionResult.Rejected(
                0,
                CapturePlanTaskExecutionRejectionReason.BLANK_TITLE,
            ),
            CapturePlanTaskExecutionResult.RejectedPlan(
                CapturePlanTaskPlanRejectionReason.EMPTY_ACTIONS,
            ),
            CapturePlanTaskExecutionResult.MissingSourceCapture,
        )
        taskRejected.forEach { result -> CaptureExecutionOutcome.Rejected.Tasks(result) }

        val listExecuted = CapturePlanListExecutionResult.Executed(
            items = listOf(
                ListItem(
                    id = ListItemId("item"),
                    listDefinitionId = ListDefinitionId("compras"),
                    text = "item",
                    listSessionId = null,
                    isCompleted = false,
                    createdAt = capturedAt,
                ),
            ),
            actionLedgerEntryId = ActionLedgerEntryId("list-entry"),
        )
        val taskExecuted = executedTaskReceipt("task-entry", "task")
        val listFailed = CapturePlanListExecutionResult.Failed(IllegalStateException("list"))
        val taskFailed = CapturePlanTaskExecutionResult.Failed(IllegalStateException("task"))

        assertIllegalArgument { CaptureExecutionOutcome.Rejected.ListItems(listExecuted) }
        assertIllegalArgument { CaptureExecutionOutcome.Rejected.ListItems(listFailed) }
        assertIllegalArgument { CaptureExecutionOutcome.Rejected.Tasks(taskExecuted) }
        assertIllegalArgument { CaptureExecutionOutcome.Rejected.Tasks(taskFailed) }
    }

    private fun submission(
        writer: CaptureWriter = CaptureWriter { },
        interpreter: CaptureInterpreter,
        listExecutor: CapturePlanListExecutor? = null,
        taskExecutor: CapturePlanTaskExecutor? = null,
        id: CaptureId = captureId,
    ) = CaptureSubmission(
        writer = writer,
        interpreter = interpreter,
        listExecutor = listExecutor,
        taskExecutor = taskExecutor,
        idProvider = { id },
        capturedAtProvider = { capturedAt },
    )

    private fun plan(
        vararg actions: CapturePlanAction,
        sourceCaptureId: CaptureId = captureId,
    ) = CapturePlan(
        sourceCaptureId = sourceCaptureId,
        actions = actions.toList(),
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

    private fun executedTaskReceipt(
        ledgerId: String,
        vararg taskIds: String,
    ) = CapturePlanTaskExecutionResult.Executed(
        tasks = taskIds.mapIndexed { index, id ->
            Task(
                id = TaskId(id),
                title = "task-$index",
                space = if (index % 2 == 0) TaskSpace.PERSONAL else TaskSpace.TRABAJO,
                dueDate = null,
                completedAt = null,
            )
        },
        actionLedgerEntryId = ActionLedgerEntryId(ledgerId),
    )

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("IllegalArgumentException expected")
        } catch (_: IllegalArgumentException) {
            // Expected by the family-specific rejection invariant.
        }
    }

    private class RecordingTaskExecutor(
        private val result: CapturePlanTaskExecutionResult,
        private val onExecute: () -> Unit = {},
    ) : CapturePlanTaskExecutor {
        var executeCalls = 0
            private set
        val executedPlans = mutableListOf<CapturePlan>()

        override suspend fun execute(plan: CapturePlan): CapturePlanTaskExecutionResult {
            executeCalls += 1
            executedPlans += plan
            onExecute()
            return result
        }

        override suspend fun undoExecution(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedTaskIds: List<TaskId>,
        ): UndoCapturePlanTaskExecutionResult = error("Undo is not used")
    }

    private class RecordingListExecutor(
        private val result: CapturePlanListExecutionResult =
            CapturePlanListExecutionResult.UnsupportedAction(0),
    ) : CapturePlanListExecutor {
        var executeCalls = 0
            private set

        override suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult {
            executeCalls += 1
            return result
        }

        override suspend fun undoExecution(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedItemIds: List<ListItemId>,
            autoCreatedMandadoSessionId: ListSessionId?,
        ): UndoCapturePlanListExecutionResult = error("Undo is not used")
    }
}

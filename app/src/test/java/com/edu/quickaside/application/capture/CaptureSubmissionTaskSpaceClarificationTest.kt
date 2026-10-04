package com.edu.quickaside.application.capture

import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.TaskId
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

class CaptureSubmissionTaskSpaceClarificationTest {
    private val clarification = CaptureClarification.TaskSpace(
        sourceCaptureId = CaptureId("capture-034"),
        title = "Revisar PR",
        dueDate = LocalDate.of(2026, 10, 4),
    )
    private val savedCaptures = mutableListOf<Capture>()
    private val events = mutableListOf<String>()
    private val receipt = CapturePlanTaskExecutionResult.Executed(
        listOf(Task(TaskId("task-034"), clarification.title, TaskSpace.TRABAJO, clarification.dueDate, null)),
        ActionLedgerEntryId("ledger-034"),
    )

    @Test
    fun submissionPersistsThenInterpretsWithoutAnyFamilyMutation() = runBlocking {
        val executor = executor { receipt }
        val saved = submission(executor).submit("Revisar PR mañana") as CaptureSubmissionResult.Saved

        assertEquals(listOf("persist", "interpret"), events)
        assertEquals(CaptureInterpretationResult.ClarificationRequired(clarification), saved.interpretation)
        assertEquals(CaptureExecutionOutcome.NoValidPlan, saved.execution)
        assertEquals(listOf(saved.capture), savedCaptures)
        assertEquals(emptyList<CapturePlan>(), executor.plans)
    }

    @Test
    fun trabajoResolutionForwardsExactlyOnePlanAndPreservesReceiptWithoutSavingOrInterpretingAgain() = runBlocking {
        val executor = executor { receipt }
        val submission = submission(executor)
        submission.submit("Revisar PR mañana")
        val outcome = submission.resolveTaskSpaceClarification(clarification, TaskSpace.TRABAJO)

        assertEquals(listOf(CapturePlan(clarification.sourceCaptureId, listOf(
            CapturePlanAction.CreateTask(TaskSpace.TRABAJO, clarification.title, clarification.dueDate),
        ))), executor.plans)
        assertEquals(CaptureExecutionOutcome.Executed.Tasks(receipt), outcome)
        assertSame(receipt, (outcome as CaptureExecutionOutcome.Executed.Tasks).receipt)
        assertEquals(listOf("persist", "interpret", "execute"), events)
        assertEquals(1, savedCaptures.size)
    }

    @Test
    fun personalResolutionForwardsPersonalAndOptionalDate() = runBlocking {
        val executor = executor { receipt }
        submission(executor).resolveTaskSpaceClarification(clarification.copy(dueDate = null), TaskSpace.PERSONAL)
        val action = executor.plans.single().actions.single() as CapturePlanAction.CreateTask
        assertEquals(TaskSpace.PERSONAL, action.space)
        assertEquals(null, action.dueDate)
        assertEquals(emptyList<Capture>(), savedCaptures)
    }

    @Test
    fun missingExecutorReturnsHonestTaskFailure() = runBlocking {
        val outcome = submission(null).resolveTaskSpaceClarification(clarification, TaskSpace.TRABAJO)
        assertTrue(outcome is CaptureExecutionOutcome.Failed.Tasks)
        assertEquals(emptyList<Capture>(), savedCaptures)
    }

    @Test
    fun typedRejectionAndFailureArePreserved() = runBlocking {
        val rejection = CapturePlanTaskExecutionResult.Rejected(0, CapturePlanTaskExecutionRejectionReason.TASK_TITLE_TOO_LONG)
        val failure = CapturePlanTaskExecutionResult.Failed(IllegalStateException("write failed"))
        assertEquals(CaptureExecutionOutcome.Rejected.Tasks(rejection), submission(executor { rejection })
            .resolveTaskSpaceClarification(clarification, TaskSpace.PERSONAL))
        assertEquals(CaptureExecutionOutcome.Failed.Tasks(failure), submission(executor { failure })
            .resolveTaskSpaceClarification(clarification, TaskSpace.PERSONAL))
        assertEquals(emptyList<Capture>(), savedCaptures)
    }

    @Test
    fun executorExceptionUsesExistingTaskFailureSemantics() = runBlocking {
        val failure = IllegalStateException("write exception")
        val outcome = submission(executor { throw failure })
            .resolveTaskSpaceClarification(clarification, TaskSpace.PERSONAL) as CaptureExecutionOutcome.Failed.Tasks
        assertSame(failure, outcome.result.cause)
    }

    @Test
    fun resolutionCancellationPropagatesAndOriginalCaptureRemainsSaved() = runBlocking {
        val cancellation = CancellationException("cancel resolution")
        val executor = executor { throw cancellation }
        val submission = submission(executor)
        submission.submitVoice("Revisar PR mañana")
        try {
            submission.resolveTaskSpaceClarification(clarification, TaskSpace.TRABAJO)
            fail("CancellationException expected")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(1, savedCaptures.size)
        assertEquals(1, executor.plans.size)
        assertEquals(listOf("persist", "interpret", "execute"), events)
    }

    private fun submission(executor: CapturePlanTaskExecutor?) = CaptureSubmission(
        writer = CaptureWriter { savedCaptures += it; events += "persist" },
        interpreter = CaptureInterpreter { events += "interpret"; CaptureInterpretationResult.ClarificationRequired(clarification) },
        taskExecutor = executor,
        listExecutor = object : CapturePlanListExecutor {
            override suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult = error("No List mutation allowed")
            override suspend fun undoExecution(
                actionLedgerEntryId: ActionLedgerEntryId,
                expectedItemIds: List<com.edu.quickaside.domain.common.ListItemId>,
                autoCreatedMandadoSessionId: com.edu.quickaside.domain.common.ListSessionId?,
            ): UndoCapturePlanListExecutionResult = error("No List Undo allowed")
        },
        memoryExecutor = object : CapturePlanMemoryExecutor {
            override suspend fun execute(plan: CapturePlan): CapturePlanMemoryExecutionResult = error("No Memory mutation allowed")
            override suspend fun undoExecution(
                actionLedgerEntryId: ActionLedgerEntryId,
                expectedTargets: List<MemoryExecutionTarget>,
            ): UndoCapturePlanMemoryExecutionResult = error("No Memory Undo allowed")
        },
        idProvider = { clarification.sourceCaptureId },
        capturedAtProvider = { Instant.parse("2026-10-03T18:00:00Z") },
    )

    private fun executor(result: suspend () -> CapturePlanTaskExecutionResult) = RecordingExecutor(result)

    private inner class RecordingExecutor(private val result: suspend () -> CapturePlanTaskExecutionResult) : CapturePlanTaskExecutor {
        val plans = mutableListOf<CapturePlan>()
        override suspend fun execute(plan: CapturePlan): CapturePlanTaskExecutionResult {
            plans += plan
            events += "execute"
            return result()
        }
        override suspend fun undoExecution(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedTaskIds: List<TaskId>,
        ): UndoCapturePlanTaskExecutionResult = error("No Undo before UI choice")
    }
}

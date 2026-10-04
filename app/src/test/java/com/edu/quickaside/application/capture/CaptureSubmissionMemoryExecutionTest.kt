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
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptureSubmissionMemoryExecutionTest {
    private val captureId = CaptureId("capture-033")
    private val now = Instant.parse("2026-10-03T18:00:00Z")
    private val note = CapturePlanAction.CreateNote("Nota")
    private val log = CapturePlanAction.CreateStructuredLog(mapOf("peso" to "210 lbs"))
    private val task = CapturePlanAction.CreateTask(TaskSpace.PERSONAL, "Pendiente")
    private val item = CapturePlanAction.AddListItem(ListDefinitionId("compras"), "leche")
    private val receipt = CapturePlanMemoryExecutionResult.Executed(
        records = listOf(
            CreatedMemoryRecord.Note(Note(NoteId("second"), "Nota", captureId, now)),
            CreatedMemoryRecord.StructuredLog(
                StructuredLog(StructuredLogId("log"), mapOf("peso" to "210 lbs"), captureId, now),
            ),
            CreatedMemoryRecord.Note(Note(NoteId("first"), "Otra nota", captureId, now)),
        ),
        actionLedgerEntryId = ActionLedgerEntryId("memory-ledger"),
    )

    @Test
    fun interleavedMemoryExecutesExactlyOnceAfterPersistenceAndInterpretation() = runBlocking {
        val events = mutableListOf<String>()
        val plan = plan(note, log, CapturePlanAction.CreateNote("Otra nota"))
        val executor = RecordingMemoryExecutor { events += "execute"; receipt }
        val saved = CaptureSubmission(
            writer = CaptureWriter { events += "persist" },
            interpreter = CaptureInterpreter {
                events += "interpret"
                CaptureInterpretationResult.Success(plan)
            },
            memoryExecutor = executor,
            idProvider = { captureId },
        ).submit("Nota, registro y otra nota") as CaptureSubmissionResult.Saved

        assertEquals(listOf("persist", "interpret", "execute"), events)
        assertEquals(1, executor.plans.size)
        assertSame(plan, executor.plans.single())
        val executed = saved.execution as CaptureExecutionOutcome.Executed.Memory
        assertSame(receipt, executed.receipt)
        assertEquals(
            listOf(
                MemoryExecutionTarget.Note(NoteId("second")),
                MemoryExecutionTarget.StructuredLog(StructuredLogId("log")),
                MemoryExecutionTarget.Note(NoteId("first")),
            ),
            executed.receipt.records.map { it.target },
        )
    }

    @Test
    fun listAndTaskFamiliesKeepTheirOwnExecutors() = runBlocking {
        for (action in listOf(item, task)) {
            val calls = mutableListOf<String>()
            val memory = RecordingMemoryExecutor { calls += "memory"; receipt }
            val saved = submission(
                plan(action), memory, calls = calls,
            ).submit("Otra familia") as CaptureSubmissionResult.Saved
            assertEquals(listOf(if (action == item) "list" else "task"), calls)
            assertTrue(memory.plans.isEmpty())
            assertEquals(
                if (action == item) {
                    CaptureExecutionOutcome.Rejected.ListItems(CapturePlanListExecutionResult.UnsupportedAction(0))
                } else {
                    CaptureExecutionOutcome.Rejected.Tasks(CapturePlanTaskExecutionResult.UnsupportedAction(0))
                },
                saved.execution,
            )
        }
    }

    @Test
    fun mixedFamiliesAndUndoLastExecuteNothing() = runBlocking {
        val plans = listOf(plan(note, task), plan(log, item), plan(note, CapturePlanAction.UndoLast),
            plan(item, task), plan(CapturePlanAction.UndoLast))
        for (plan in plans) {
            val calls = mutableListOf<String>()
            val memory = RecordingMemoryExecutor { calls += "memory"; receipt }
            val captures = mutableListOf<Capture>()
            val saved = submission(plan, memory, captures, calls).submit("Sin aplicar") as CaptureSubmissionResult.Saved
            assertEquals(CaptureExecutionOutcome.NotEligible, saved.execution)
            assertTrue(calls.isEmpty())
            assertTrue(memory.plans.isEmpty())
            assertEquals(1, captures.size)
        }
    }

    @Test
    fun missingMemoryExecutorFailsHonestlyAfterPersistence() = runBlocking {
        val captures = mutableListOf<Capture>()
        val saved = submission(plan(note), captures = captures).submit("Nota") as CaptureSubmissionResult.Saved
        val failed = saved.execution as CaptureExecutionOutcome.Failed.Memory
        assertTrue(failed.result.cause is IllegalStateException)
        assertEquals("Eligible Memory CapturePlan has no configured executor", failed.result.cause.message)
        assertEquals(listOf(saved.capture), captures)
    }

    @Test
    fun allowedMemoryRejectionsKeepTypedResultAndPersistedCapture() = runBlocking {
        val rejections = listOf(
            CapturePlanMemoryExecutionResult.UnsupportedAction(0),
            CapturePlanMemoryExecutionResult.Rejected(0, CapturePlanMemoryExecutionRejectionReason.BLANK_NOTE_TEXT),
            CapturePlanMemoryExecutionResult.RejectedPlan(CapturePlanMemoryPlanRejectionReason.EMPTY_ACTIONS),
            CapturePlanMemoryExecutionResult.MissingSourceCapture,
        )
        for (result in rejections) {
            val captures = mutableListOf<Capture>()
            val saved = submission(plan(note), RecordingMemoryExecutor { result }, captures)
                .submit("Nota") as CaptureSubmissionResult.Saved
            assertEquals(CaptureExecutionOutcome.Rejected.Memory(result), saved.execution)
            assertEquals(listOf(saved.capture), captures)
        }
    }

    @Test
    fun returnedAndThrownFailuresRemainMemoryFailuresAfterPersistence() = runBlocking {
        val cause = IllegalStateException("memory write failed")
        val failure = CapturePlanMemoryExecutionResult.Failed(cause)
        for (throws in listOf(false, true)) {
            val captures = mutableListOf<Capture>()
            val executor = RecordingMemoryExecutor { if (throws) throw cause else failure }
            val saved = submission(plan(log), executor, captures).submit("Registro") as CaptureSubmissionResult.Saved
            val outcome = saved.execution as CaptureExecutionOutcome.Failed.Memory
            assertSame(cause, outcome.result.cause)
            if (!throws) assertSame(failure, outcome.result)
            assertEquals(listOf(saved.capture), captures)
        }
    }

    @Test
    fun cancellationPropagatesUnchangedAfterPersistence() = runBlocking {
        val captures = mutableListOf<Capture>()
        val cancellation = CancellationException("cancel Memory")
        val executor = RecordingMemoryExecutor { throw cancellation }
        val submission = submission(plan(note), executor, captures)
        val actual = assertThrows(CancellationException::class.java) {
            runBlocking { submission.submitVoice("Nota") }
        }
        assertSame(cancellation, actual)
        assertEquals(listOf(captureId), captures.map { it.id })
        assertEquals(1, executor.plans.size)
    }

    @Test
    fun rejectedMemoryCannotWrapExecutedOrFailed() {
        assertThrows(IllegalArgumentException::class.java) { CaptureExecutionOutcome.Rejected.Memory(receipt) }
        assertThrows(IllegalArgumentException::class.java) {
            CaptureExecutionOutcome.Rejected.Memory(CapturePlanMemoryExecutionResult.Failed(IllegalStateException()))
        }
    }

    private fun plan(vararg actions: CapturePlanAction) = CapturePlan(captureId, actions.toList())

    private fun submission(
        plan: CapturePlan,
        memory: CapturePlanMemoryExecutor? = null,
        captures: MutableList<Capture> = mutableListOf(),
        calls: MutableList<String> = mutableListOf(),
    ) = CaptureSubmission(
        writer = CaptureWriter { captures += it },
        interpreter = CaptureInterpreter { CaptureInterpretationResult.Success(plan) },
        memoryExecutor = memory,
        listExecutor = object : CapturePlanListExecutor {
            override suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult {
                calls += "list"
                return CapturePlanListExecutionResult.UnsupportedAction(0)
            }
            override suspend fun undoExecution(
                actionLedgerEntryId: ActionLedgerEntryId,
                expectedItemIds: List<ListItemId>,
                autoCreatedMandadoSessionId: ListSessionId?,
            ): UndoCapturePlanListExecutionResult = error("Unused")
        },
        taskExecutor = object : CapturePlanTaskExecutor {
            override suspend fun execute(plan: CapturePlan): CapturePlanTaskExecutionResult {
                calls += "task"
                return CapturePlanTaskExecutionResult.UnsupportedAction(0)
            }
            override suspend fun undoExecution(
                actionLedgerEntryId: ActionLedgerEntryId,
                expectedTaskIds: List<TaskId>,
            ): UndoCapturePlanTaskExecutionResult = error("Unused")
        },
        idProvider = { captureId },
        capturedAtProvider = { now },
    )

    private class RecordingMemoryExecutor(
        private val execute: () -> CapturePlanMemoryExecutionResult,
    ) : CapturePlanMemoryExecutor {
        val plans = mutableListOf<CapturePlan>()
        override suspend fun execute(plan: CapturePlan): CapturePlanMemoryExecutionResult {
            plans += plan
            return execute()
        }
        override suspend fun undoExecution(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedTargets: List<MemoryExecutionTarget>,
        ): UndoCapturePlanMemoryExecutionResult = error("Unused")
    }
}

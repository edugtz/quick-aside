package com.edu.quickaside.application.capture

import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CaptureSubmissionExecutionTest {
    private val capturedAt = Instant.parse("2026-09-19T18:00:00Z")
    private val captureId = CaptureId("capture-028")

    @Test
    fun eligiblePlanPersistsThenInterpretsThenExecutesExactlyOnce() = runBlocking {
        val events = mutableListOf<String>()
        val plan = plan(compras("leche"))
        val receipt = executedReceipt("ledger-1", "item-1")
        val executor = RecordingExecutor(receipt) { events += "execute" }
        val submission = submission(
            writer = CaptureWriter { events += "persist" },
            interpreter = CaptureInterpreter {
                events += "interpret"
                CaptureInterpretationResult.Success(plan)
            },
            executor = executor,
        )

        val saved = submission.submit("Compra leche") as CaptureSubmissionResult.Saved

        assertEquals(listOf("persist", "interpret", "execute"), events)
        assertEquals(1, executor.executeCalls)
        assertSame(plan, executor.executedPlans.single())
        assertEquals(CaptureExecutionOutcome.Executed(receipt), saved.execution)
    }

    @Test
    fun persistenceFailureDoesNotInterpretOrExecute() = runBlocking {
        var interpretationCalls = 0
        val executor = RecordingExecutor(executedReceipt("unused", "unused"))
        val submission = submission(
            writer = CaptureWriter { throw IllegalStateException("database unavailable") },
            interpreter = CaptureInterpreter {
                interpretationCalls += 1
                CaptureInterpretationResult.Success(plan(compras("unused")))
            },
            executor = executor,
        )

        assertTrue(submission.submit("No perder") is CaptureSubmissionResult.Failed)
        assertEquals(0, interpretationCalls)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun multipleListActionsExecuteAsOneCompleteOrderedPlanAndRetainExactReceiptIds() = runBlocking {
        val plan = plan(compras("uno"), mandado("dos"), compras("tres"))
        val receipt = executedReceipt("ledger-batch", "item-a", "item-b", "item-c")
        val executor = RecordingExecutor(receipt)
        val saved = submission(
            interpreter = CaptureInterpreter { CaptureInterpretationResult.Success(plan) },
            executor = executor,
        ).submitVoice("Guarda tres cosas") as CaptureSubmissionResult.Saved

        assertEquals(1, executor.executeCalls)
        assertEquals(listOf(plan), executor.executedPlans)
        val execution = saved.execution as CaptureExecutionOutcome.Executed
        assertEquals(ActionLedgerEntryId("ledger-batch"), execution.receipt.actionLedgerEntryId)
        assertEquals(
            listOf(ListItemId("item-a"), ListItemId("item-b"), ListItemId("item-c")),
            execution.receipt.items.map(ListItem::id),
        )
    }

    @Test
    fun mixedPlanIsSavedButNotEligibleAndExecutesNothing() = runBlocking {
        val executor = RecordingExecutor(executedReceipt("unused", "unused"))
        val mixed = plan(
            compras("leche"),
            CapturePlanAction.CreateTask(TaskSpace.PERSONAL, "Pagar luz"),
        )
        val saved = submission(
            interpreter = CaptureInterpreter { CaptureInterpretationResult.Success(mixed) },
            executor = executor,
        ).submit("Compra leche y paga la luz") as CaptureSubmissionResult.Saved

        assertEquals(captureId, saved.capture.id)
        assertEquals(CaptureExecutionOutcome.NotEligible, saved.execution)
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun everyUnsupportedActionFamilyIsSavedWithoutExecutorInvocation() = runBlocking {
        val unsupportedActions = listOf(
            CapturePlanAction.CreateTask(TaskSpace.TRABAJO, "Revisar PR"),
            CapturePlanAction.CreateNote("Nota"),
            CapturePlanAction.CreateStructuredLog(mapOf("peso" to "210 lbs")),
            CapturePlanAction.UndoLast,
        )
        val executor = RecordingExecutor(executedReceipt("unused", "unused"))

        unsupportedActions.forEachIndexed { index, action ->
            val saved = submission(
                interpreter = CaptureInterpreter {
                    CaptureInterpretationResult.Success(plan(action))
                },
                executor = executor,
                id = CaptureId("unsupported-$index"),
            ).submit("captura $index") as CaptureSubmissionResult.Saved
            assertEquals(CaptureExecutionOutcome.NotEligible, saved.execution)
        }

        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun nonSuccessInterpretationsNeverInvokeExecutorAndPreserveTheirSemantics() = runBlocking {
        val interpretations = listOf(
            CaptureInterpretationResult.BlankInput,
            CaptureInterpretationResult.InvalidPlan(
                listOf(
                    CapturePlanValidationIssue.Plan(
                        CapturePlanValidationReason.EMPTY_ACTIONS,
                    ),
                ),
            ),
            CaptureInterpretationResult.ProviderFailure(
                IllegalStateException("provider unavailable"),
            ),
        )
        val executor = RecordingExecutor(executedReceipt("unused", "unused"))

        interpretations.forEachIndexed { index, interpretation ->
            val saved = submission(
                interpreter = CaptureInterpreter { interpretation },
                executor = executor,
                id = CaptureId("non-success-$index"),
            ).submit("captura $index") as CaptureSubmissionResult.Saved
            assertSame(interpretation, saved.interpretation)
            assertEquals(CaptureExecutionOutcome.NoValidPlan, saved.execution)
        }

        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun executorRejectionKeepsCaptureSavedWithoutClaimingExecution() = runBlocking {
        val rejection = CapturePlanListExecutionResult.Rejected(
            actionIndex = 0,
            reason = CapturePlanListExecutionRejectionReason.NO_ACTIVE_SESSION,
        )
        val saved = submission(
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(plan(mandado("aguacate")))
            },
            executor = RecordingExecutor(rejection),
        ).submit("Agrega aguacate") as CaptureSubmissionResult.Saved

        assertEquals(captureId, saved.capture.id)
        assertEquals(CaptureExecutionOutcome.Rejected(rejection), saved.execution)
    }

    @Test
    fun executorFailureKeepsCaptureSavedWithoutClaimingExecution() = runBlocking {
        val failure = CapturePlanListExecutionResult.Failed(IllegalStateException("write failed"))
        val saved = submission(
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(plan(compras("cuerdas")))
            },
            executor = RecordingExecutor(failure),
        ).submit("Compra cuerdas") as CaptureSubmissionResult.Saved

        assertEquals(captureId, saved.capture.id)
        assertEquals(CaptureExecutionOutcome.Failed(failure), saved.execution)
    }

    @Test
    fun executorCancellationPropagatesAfterCaptureWasPersisted() = runBlocking {
        val savedCaptures = mutableListOf<Capture>()
        val cancellation = CancellationException("cancel execution")
        val executor = object : CapturePlanListExecutor {
            override suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult {
                throw cancellation
            }

            override suspend fun undoExecution(
                actionLedgerEntryId: ActionLedgerEntryId,
                expectedItemIds: List<ListItemId>,
            ): UndoCapturePlanListExecutionResult = error("Undo is not used")
        }
        val submission = submission(
            writer = CaptureWriter(savedCaptures::add),
            interpreter = CaptureInterpreter {
                CaptureInterpretationResult.Success(plan(compras("leche")))
            },
            executor = executor,
        )

        try {
            submission.submit("Compra leche")
            fail("CancellationException expected")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(listOf(captureId), savedCaptures.map(Capture::id))
    }

    private fun submission(
        writer: CaptureWriter = CaptureWriter { },
        interpreter: CaptureInterpreter,
        executor: CapturePlanListExecutor,
        id: CaptureId = captureId,
    ) = CaptureSubmission(
        writer = writer,
        interpreter = interpreter,
        listExecutor = executor,
        idProvider = { id },
        capturedAtProvider = { capturedAt },
    )

    private fun plan(vararg actions: CapturePlanAction) = CapturePlan(
        sourceCaptureId = captureId,
        actions = actions.toList(),
    )

    private fun compras(text: String) = CapturePlanAction.AddListItem(
        ListDefinitionId("compras"),
        text,
    )

    private fun mandado(text: String) = CapturePlanAction.AddListItem(
        ListDefinitionId("mandado"),
        text,
    )

    private fun executedReceipt(
        ledgerId: String,
        vararg itemIds: String,
    ) = CapturePlanListExecutionResult.Executed(
        items = itemIds.mapIndexed { index, id ->
            ListItem(
                id = ListItemId(id),
                listDefinitionId = ListDefinitionId("compras"),
                text = "item-$index",
                listSessionId = null,
                isCompleted = false,
                createdAt = capturedAt,
            )
        },
        actionLedgerEntryId = ActionLedgerEntryId(ledgerId),
    )

    private class RecordingExecutor(
        private val result: CapturePlanListExecutionResult,
        private val onExecute: () -> Unit = {},
    ) : CapturePlanListExecutor {
        var executeCalls = 0
            private set
        val executedPlans = mutableListOf<CapturePlan>()

        override suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult {
            executeCalls += 1
            executedPlans += plan
            onExecute()
            return result
        }

        override suspend fun undoExecution(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedItemIds: List<ListItemId>,
        ): UndoCapturePlanListExecutionResult = error("Undo is not used")
    }
}

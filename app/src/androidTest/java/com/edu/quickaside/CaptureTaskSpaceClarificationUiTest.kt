package com.edu.quickaside

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edu.quickaside.application.capture.CaptureClarification
import com.edu.quickaside.application.capture.CaptureInterpretationResult
import com.edu.quickaside.application.capture.CaptureInterpreter
import com.edu.quickaside.application.capture.CapturePlanTaskExecutionResult
import com.edu.quickaside.application.capture.CapturePlanTaskExecutor
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.UndoCapturePlanTaskExecutionResult
import com.edu.quickaside.application.tasks.TaskStore
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureKind
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureTaskSpaceClarificationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val title = "Revisar PR"
    private val question = "¿Dónde guardo “$title”?"
    private val sourceId = CaptureId("ui-034-capture")
    private val dueDate = LocalDate.of(2026, 10, 4)
    private val savedCaptures = mutableListOf<Capture>()
    private val store = FakeTaskStore()
    private val speechFactory = FakeSpeechTranscriberFactory()

    @Test
    fun textClarificationWaitsForChoiceThenTrabajoReusesReceiptAndExactUndo() {
        val executor = RecordingExecutor()
        setContent(executor)
        submitText()
        assertPending(executor)

        choice("Trabajo").performClick()
        waitForText("Pendiente guardado")
        composeRule.onNodeWithText(question).assertDoesNotExist()
        composeRule.onNodeWithText("Deshacer").assertIsDisplayed().performClick()
        waitForText("Cambio deshecho")

        assertEquals(listOf(CapturePlan(sourceId, listOf(
            CapturePlanAction.CreateTask(TaskSpace.TRABAJO, title, dueDate),
        ))), executor.plans)
        assertEquals(listOf(UndoCall(executor.receipt.actionLedgerEntryId, executor.receipt.tasks.map(Task::id))), executor.undoCalls)
        assertEquals(1, savedCaptures.size)
    }

    @Test
    fun personalChoiceIsForwarded() {
        val executor = RecordingExecutor()
        setContent(executor)
        submitText()
        waitForText(question)
        choice("Personal").performClick()
        waitForText("Pendiente guardado")
        assertEquals(TaskSpace.PERSONAL, (executor.plans.single().actions.single() as CapturePlanAction.CreateTask).space)
    }

    @Test
    fun rapidRepeatedCallbacksCannotExecuteTwiceWhileResolutionIsInFlight() {
        val gate = CompletableDeferred<Unit>()
        val executor = RecordingExecutor(gate = gate)
        setContent(executor)
        submitText()
        waitForText(question)
        // Invoke the same callback twice before recomposition, matching queued rapid taps.
        choice("Trabajo").performSemanticsAction(SemanticsActions.OnClick) { click ->
            click()
            click()
        }
        composeRule.waitForIdle()
        assertEquals(1, executor.plans.size)
        composeRule.onNodeWithText(question).assertDoesNotExist()
        choice("Trabajo").assertDoesNotExist()
        choice("Personal").assertDoesNotExist()
        assertNoSuccess()

        composeRule.runOnIdle { gate.complete(Unit) }
        waitForText("Pendiente guardado")
        assertEquals(1, executor.plans.size)
        assertEquals(1, savedCaptures.size)
    }

    @Test
    fun backDismissalPreservesCaptureWithoutExecutionOrSuccess() {
        val executor = RecordingExecutor()
        setContent(executor)
        submitText()
        assertPending(executor)
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
        )
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithText(question).assertDoesNotExist() }.isSuccess
        }
        assertEquals(0, executor.plans.size)
        assertEquals(1, savedCaptures.size)
        assertNoSuccess()
    }

    @Test
    fun failedResolutionHasNoSuccessOrUndo() {
        assertResolutionNotApplied(CapturePlanTaskExecutionResult.Failed(IllegalStateException("write failed")))
    }

    @Test
    fun rejectedResolutionHasNoSuccessOrUndo() {
        assertResolutionNotApplied(CapturePlanTaskExecutionResult.MissingSourceCapture)
    }

    @Test
    fun failedUndoNeverClaimsChangeUndone() {
        val executor = RecordingExecutor(undoFails = true)
        setContent(executor)
        submitText()
        waitForText(question)
        choice("Personal").performClick()
        waitForText("Pendiente guardado")
        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("No se pudo deshacer.")
        composeRule.onNodeWithText("Cambio deshecho").assertDoesNotExist()
        assertEquals(1, executor.undoCalls.size)
    }

    @Test
    fun voiceClarificationSurvivesCaptureCloseDeduplicatesFinalsAndRefreshesTasksAfterChoiceAndUndo() {
        val executor = RecordingExecutor()
        setContent(executor)
        composeRule.onNode(hasText("Pendientes") and hasClickAction()).performClick()
        waitForText("No hay pendientes en Personal.")
        val readsBefore = store.readCount
        composeRule.onNodeWithContentDescription("Capturar").performClick()
        waitForText("Listo para escuchar…")
        val transcriber = speechFactory.latest()
        transcriber.emitFinal("Revisar PR mañana")
        transcriber.emitFinal("No duplicar")
        assertPending(executor)
        composeRule.onNodeWithText("Captura").assertDoesNotExist()
        assertEquals(1, savedCaptures.size)
        assertEquals(CaptureKind.VOICE, savedCaptures.single().kind)
        choice("Personal").performClick()
        waitForText("Pendiente guardado")
        waitForText(title)
        assertEquals(1, executor.plans.size)
        val readsAfterExecution = store.readCount
        org.junit.Assert.assertTrue(readsAfterExecution > readsBefore)
        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("Cambio deshecho")
        waitForText("No hay pendientes en Personal.")
        org.junit.Assert.assertTrue(store.readCount > readsAfterExecution)
        assertEquals(listOf(UndoCall(executor.receipt.actionLedgerEntryId, executor.receipt.tasks.map(Task::id))), executor.undoCalls)
    }

    @Test
    fun unsupportedInterpretationShowsNeutralCopyWithoutDialogOrMutation() {
        val executor = RecordingExecutor()
        setContent(executor, unsupported = true)
        submitText()
        waitForText("Captura guardada · interpretación lista, sin aplicar")
        composeRule.onNodeWithText(question).assertDoesNotExist()
        assertNoSuccess()
        assertEquals(0, executor.plans.size)
        assertEquals(1, savedCaptures.size)
    }

    private fun assertResolutionNotApplied(result: CapturePlanTaskExecutionResult) {
        val executor = RecordingExecutor(result = result)
        setContent(executor)
        submitText()
        waitForText(question)
        choice("Trabajo").performClick()
        waitForText("Captura guardada · no se pudo aplicar la interpretación")
        assertNoSuccess()
        assertEquals(1, executor.plans.size)
        assertEquals(1, savedCaptures.size)
    }

    private fun assertPending(executor: RecordingExecutor) {
        waitForText(question)
        choice("Trabajo").assertIsDisplayed()
        choice("Personal").assertIsDisplayed()
        assertEquals(0, executor.plans.size)
        assertNoSuccess()
        composeRule.onNodeWithText("Captura guardada · interpretación lista, sin aplicar").assertDoesNotExist()
    }

    private fun assertNoSuccess() {
        composeRule.onNodeWithText("Pendiente guardado").assertDoesNotExist()
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()
        composeRule.onNodeWithText("Cambio deshecho").assertDoesNotExist()
    }

    private fun choice(label: String) = composeRule.onNode(hasText(label) and hasAnyAncestor(isDialog()))

    private fun submitText() {
        composeRule.onNode(hasSetTextAction()).performTextInput("Revisar PR mañana")
        composeRule.onNodeWithContentDescription("Enviar captura").performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithText(text).assertIsDisplayed() }.isSuccess
        }
    }

    private fun setContent(executor: RecordingExecutor, unsupported: Boolean = false) {
        val submission = CaptureSubmission(
            writer = CaptureWriter { savedCaptures += it },
            interpreter = CaptureInterpreter { capture ->
                if (unsupported) CaptureInterpretationResult.Unsupported
                else CaptureInterpretationResult.ClarificationRequired(CaptureClarification.TaskSpace(capture.id, title, dueDate))
            },
            taskExecutor = executor,
            idProvider = { sourceId },
            capturedAtProvider = { Instant.parse("2026-10-03T18:00:00Z") },
        )
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = submission,
                        captureReader = { savedCaptures.toList() },
                        capturePlanTaskExecutor = executor,
                        taskStore = store,
                        speechTranscriberFactory = speechFactory,
                        microphonePermissionController = FakeMicrophonePermissionController(granted = true),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private data class UndoCall(val ledgerId: ActionLedgerEntryId, val taskIds: List<TaskId>)

    private inner class RecordingExecutor(
        private val result: CapturePlanTaskExecutionResult? = null,
        private val gate: CompletableDeferred<Unit>? = null,
        private val undoFails: Boolean = false,
    ) : CapturePlanTaskExecutor {
        val receipt = CapturePlanTaskExecutionResult.Executed(
            listOf(Task(TaskId("ui-034-task"), title, TaskSpace.PERSONAL, dueDate, null)),
            ActionLedgerEntryId("ui-034-ledger"),
        )
        val plans = mutableListOf<CapturePlan>()
        val undoCalls = mutableListOf<UndoCall>()

        override suspend fun execute(plan: CapturePlan): CapturePlanTaskExecutionResult {
            plans += plan
            gate?.await()
            val outcome = result ?: receipt
            if (outcome is CapturePlanTaskExecutionResult.Executed) store.tasks = outcome.tasks
            return outcome
        }

        override suspend fun undoExecution(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedTaskIds: List<TaskId>,
        ): UndoCapturePlanTaskExecutionResult {
            undoCalls += UndoCall(actionLedgerEntryId, expectedTaskIds)
            if (undoFails) return UndoCapturePlanTaskExecutionResult.Failed(IllegalStateException("undo failed"))
            store.tasks = emptyList()
            return UndoCapturePlanTaskExecutionResult.Undone(actionLedgerEntryId, expectedTaskIds)
        }
    }

    private class FakeTaskStore : TaskStore {
        var tasks: List<Task> = emptyList()
        var readCount = 0
        override suspend fun readAll(): List<Task> { readCount++; return tasks }
        override suspend fun save(task: Task) { tasks = tasks.filterNot { it.id == task.id } + task }
        override suspend fun getById(id: TaskId): Task? = tasks.singleOrNull { it.id == id }
    }
}

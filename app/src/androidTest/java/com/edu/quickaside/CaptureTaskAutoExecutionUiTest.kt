package com.edu.quickaside

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureInterpretationResult
import com.edu.quickaside.application.capture.CaptureInterpreter
import com.edu.quickaside.application.capture.CapturePlanTaskExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanTaskExecutionResult
import com.edu.quickaside.application.capture.CapturePlanTaskExecutor
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.UndoCapturePlanTaskExecutionResult
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import com.edu.quickaside.application.tasks.CreateTaskActionResult
import com.edu.quickaside.application.tasks.ReversibleTaskActions
import com.edu.quickaside.application.tasks.TaskCompletionActionResult
import com.edu.quickaside.application.tasks.TaskStore
import com.edu.quickaside.application.tasks.UndoTaskCompletionChangeResult
import com.edu.quickaside.application.tasks.UndoTaskCreateResult
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureTaskAutoExecutionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val capturedAt = Instant.parse("2026-09-24T21:00:00Z")

    @Test
    fun textSingleTaskShowsReceiptAndForwardsExactUndo() {
        val receipt = executed("task-ledger-single", "task-single")
        val store = FakeTaskStore()
        val executor = RecordingTaskExecutor(
            executeResult = receipt,
            undoResult = UndoCapturePlanTaskExecutionResult.Undone(
                actionLedgerEntryId = receipt.actionLedgerEntryId,
                taskIds = receipt.tasks.map(Task::id),
            ),
            onExecuted = { store.tasks = receipt.tasks },
            onUndone = { store.tasks = emptyList() },
        )
        setContent(
            submission(listOf(task("Revisar PR")), executor),
            executor,
            store,
        )

        submitText("Revisar PR")

        waitForText("Pendiente guardado")
        composeRule.onNodeWithText("Deshacer").assertIsDisplayed()
        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("Cambio deshecho")

        assertEquals(1, executor.executePlans.size)
        assertEquals(
            listOf(
                UndoCall(
                    actionLedgerEntryId = ActionLedgerEntryId("task-ledger-single"),
                    taskIds = listOf(TaskId("task-single")),
                ),
            ),
            executor.undoCalls,
        )
    }

    @Test
    fun textBatchShowsCountAndPreservesOrderedUndoIds() {
        val receipt = executed(
            "task-ledger-batch",
            "task-second",
            "task-first",
            "task-third",
        )
        val executor = RecordingTaskExecutor(
            executeResult = receipt,
            undoResult = UndoCapturePlanTaskExecutionResult.Undone(
                receipt.actionLedgerEntryId,
                receipt.tasks.map(Task::id),
            ),
        )
        setContent(
            submission(
                listOf(task("Segundo"), task("Primero"), task("Tercero")),
                executor,
            ),
            executor,
            FakeTaskStore(),
        )

        submitText("Guarda tres pendientes")

        waitForText("3 pendientes guardados")
        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("Cambio deshecho")
        assertEquals(
            listOf(
                UndoCall(
                    receipt.actionLedgerEntryId,
                    listOf(
                        TaskId("task-second"),
                        TaskId("task-first"),
                        TaskId("task-third"),
                    ),
                ),
            ),
            executor.undoCalls,
        )
    }

    @Test
    fun mixedPlanShowsNotAppliedCopyWithoutUndoOrExecutorCalls() {
        val executor = RecordingTaskExecutor(executed("unused", "unused"))
        setContent(
            submission(
                listOf(
                    CapturePlanAction.AddListItem(
                        com.edu.quickaside.domain.common.ListDefinitionId("compras"),
                        "leche",
                    ),
                    task("Pagar luz"),
                ),
                executor,
            ),
            executor,
            FakeTaskStore(),
        )

        submitText("Compra leche y paga la luz")

        waitForText("Captura guardada · interpretación lista, sin aplicar")
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()
        assertEquals(0, executor.executePlans.size)
    }

    @Test
    fun unsupportedPlanShowsNotAppliedCopyWithoutUndoOrExecutorCalls() {
        val executor = RecordingTaskExecutor(executed("unused", "unused"))
        setContent(
            submission(listOf(CapturePlanAction.UndoLast), executor),
            executor,
            FakeTaskStore(),
        )

        submitText("Sólo nota")

        waitForText("Captura guardada · interpretación lista, sin aplicar")
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()
        assertEquals(0, executor.executePlans.size)
    }

    @Test
    fun taskRejectionAndFailureNeverShowSuccessReceipt() {
        val rejectionExecutor = RecordingTaskExecutor(
            CapturePlanTaskExecutionResult.Rejected(
                actionIndex = 0,
                reason = CapturePlanTaskExecutionRejectionReason.BLANK_TITLE,
            ),
        )
        setContent(
            submission(listOf(task("Rechazado")), rejectionExecutor),
            rejectionExecutor,
            FakeTaskStore(),
        )
        submitText("Rechazado")
        waitForText("Captura guardada · no se pudo aplicar la interpretación")
        composeRule.onNodeWithText("Pendiente guardado").assertDoesNotExist()
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()

        val failureExecutor = RecordingTaskExecutor(
            CapturePlanTaskExecutionResult.Failed(IllegalStateException("write failed")),
        )
        setContent(
            submission(listOf(task("Fallido")), failureExecutor),
            failureExecutor,
            FakeTaskStore(),
        )
        submitText("Fallido")
        waitForText("Captura guardada · no se pudo aplicar la interpretación")
        composeRule.onNodeWithText("Pendiente guardado").assertDoesNotExist()
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()
    }

    @Test
    fun undoFailureNeverClaimsSuccess() {
        val receipt = executed("task-ledger-failed-undo", "task-failed-undo")
        val executor = RecordingTaskExecutor(
            executeResult = receipt,
            undoResult = UndoCapturePlanTaskExecutionResult.Failed(
                IllegalStateException("undo failed"),
            ),
        )
        setContent(
            submission(listOf(task("No borrar")), executor),
            executor,
            FakeTaskStore(),
        )

        submitText("No borrar")
        waitForText("Pendiente guardado")
        composeRule.onNodeWithText("Deshacer").performClick()

        waitForText("No se pudo deshacer.")
        composeRule.onNodeWithText("Cambio deshecho").assertDoesNotExist()
    }

    @Test
    fun voiceUsesTaskReceiptOnceAndClosesCaptureSurface() {
        val receipt = executed("voice-task-ledger", "voice-task")
        val executor = RecordingTaskExecutor(receipt)
        val factory = FakeSpeechTranscriberFactory()
        val savedCaptures = mutableListOf<Capture>()
        setContent(
            submission(listOf(task("Voz pendiente")), executor, savedCaptures),
            executor,
            FakeTaskStore(),
            speechFactory = factory,
        )

        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")
        factory.latest().emitFinal("Voz pendiente")
        factory.latest().emitFinal("No duplicar")

        waitForText("Pendiente guardado")
        composeRule.onNodeWithText("Captura").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Capturar").assertIsDisplayed()
        assertEquals(1, executor.executePlans.size)
        assertEquals(1, savedCaptures.size)
        assertEquals(com.edu.quickaside.domain.capture.CaptureKind.VOICE, savedCaptures.single().kind)
    }

    @Test
    fun globalTaskCaptureRefreshesPendientesAfterExecutionAndUndo() {
        val receipt = executed("visible-task-ledger", "visible-task")
        val store = FakeTaskStore()
        val executor = RecordingTaskExecutor(
            executeResult = receipt,
            undoResult = UndoCapturePlanTaskExecutionResult.Undone(
                receipt.actionLedgerEntryId,
                receipt.tasks.map(Task::id),
            ),
            onExecuted = { store.tasks = receipt.tasks },
            onUndone = { store.tasks = emptyList() },
        )
        val factory = FakeSpeechTranscriberFactory()
        setContent(
            submission(listOf(task("Pendiente visible")), executor),
            executor,
            store,
            speechFactory = factory,
        )

        composeRule.onNode(hasText("Pendientes") and hasClickAction()).performClick()
        waitForText("No hay pendientes en Personal.")
        composeRule.onNodeWithContentDescription("Capturar").performClick()
        waitForText("Listo para escuchar…")
        factory.latest().emitFinal("Pendiente visible")

        waitForText("Pendiente guardado")
        waitForText("task-0")
        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("Cambio deshecho")
        waitForText("No hay pendientes en Personal.")
        assertEquals(1, executor.executePlans.size)
        assertEquals(1, executor.undoCalls.size)
    }

    private fun submission(
        actions: List<CapturePlanAction>,
        taskExecutor: CapturePlanTaskExecutor,
        savedCaptures: MutableList<Capture> = mutableListOf(),
    ) = CaptureSubmission(
        writer = CaptureWriter { savedCaptures += it },
        interpreter = CaptureInterpreter { capture ->
            CaptureInterpretationResult.Success(
                CapturePlan(
                    sourceCaptureId = capture.id,
                    actions = actions,
                ),
            )
        },
        taskExecutor = taskExecutor,
        idProvider = { CaptureId("ui-task-capture") },
        capturedAtProvider = { capturedAt },
    )

    private fun setContent(
        submission: CaptureSubmission,
        taskExecutor: CapturePlanTaskExecutor,
        taskStore: FakeTaskStore,
        speechFactory: FakeSpeechTranscriberFactory = FakeSpeechTranscriberFactory(),
    ) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = submission,
                        captureReader = { emptyList() },
                        capturePlanTaskExecutor = taskExecutor,
                        taskStore = taskStore,
                        reversibleTaskActions = FakeReversibleTaskActions(),
                        speechTranscriberFactory = speechFactory,
                        microphonePermissionController = FakeMicrophonePermissionController(
                            granted = true,
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun submitText(text: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
        composeRule.onNodeWithContentDescription("Enviar captura").performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun task(title: String) = CapturePlanAction.CreateTask(
        space = TaskSpace.PERSONAL,
        title = title,
        dueDate = null,
    )

    private fun executed(
        ledgerId: String,
        vararg taskIds: String,
    ) = CapturePlanTaskExecutionResult.Executed(
        tasks = taskIds.mapIndexed { index, id ->
            Task(
                id = TaskId(id),
                title = "task-$index",
                space = TaskSpace.PERSONAL,
                dueDate = null,
                completedAt = null,
            )
        },
        actionLedgerEntryId = ActionLedgerEntryId(ledgerId),
    )

    private data class UndoCall(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val taskIds: List<TaskId>,
    )

    private class RecordingTaskExecutor(
        private val executeResult: CapturePlanTaskExecutionResult,
        private val undoResult: UndoCapturePlanTaskExecutionResult =
            UndoCapturePlanTaskExecutionResult.Failed(IllegalStateException("unused")),
        private val onExecuted: () -> Unit = {},
        private val onUndone: () -> Unit = {},
    ) : CapturePlanTaskExecutor {
        val executePlans = mutableListOf<CapturePlan>()
        val undoCalls = mutableListOf<UndoCall>()

        override suspend fun execute(plan: CapturePlan): CapturePlanTaskExecutionResult {
            executePlans += plan
            if (executeResult is CapturePlanTaskExecutionResult.Executed) {
                onExecuted()
            }
            return executeResult
        }

        override suspend fun undoExecution(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedTaskIds: List<TaskId>,
        ): UndoCapturePlanTaskExecutionResult {
            undoCalls += UndoCall(actionLedgerEntryId, expectedTaskIds)
            if (undoResult is UndoCapturePlanTaskExecutionResult.Undone) {
                onUndone()
            }
            return undoResult
        }
    }

    private class FakeTaskStore : TaskStore {
        var tasks: List<Task> = emptyList()

        override suspend fun save(task: Task) {
            tasks = tasks.filterNot { it.id == task.id } + task
        }

        override suspend fun getById(id: TaskId): Task? = tasks.singleOrNull { it.id == id }

        override suspend fun readAll(): List<Task> = tasks
    }

    private class FakeReversibleTaskActions : ReversibleTaskActions {
        override suspend fun create(
            title: String,
            space: TaskSpace,
            dueDate: LocalDate?,
        ): CreateTaskActionResult = CreateTaskActionResult.Failed(
            IllegalStateException("unused"),
        )

        override suspend fun undoCreate(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedTaskId: TaskId,
        ): UndoTaskCreateResult = UndoTaskCreateResult.Failed(IllegalStateException("unused"))

        override suspend fun complete(taskId: TaskId): TaskCompletionActionResult =
            TaskCompletionActionResult.Failed(IllegalStateException("unused"))

        override suspend fun reopen(taskId: TaskId): TaskCompletionActionResult =
            TaskCompletionActionResult.Failed(IllegalStateException("unused"))

        override suspend fun undoCompletionChange(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedTaskId: TaskId,
        ): UndoTaskCompletionChangeResult = UndoTaskCompletionChangeResult.Failed(
            IllegalStateException("unused"),
        )
    }
}

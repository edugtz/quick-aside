package com.edu.quickaside

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.tasks.CreateTaskActionResult
import com.edu.quickaside.application.tasks.ReversibleTaskActions
import com.edu.quickaside.application.tasks.TaskCompletionActionResult
import com.edu.quickaside.application.tasks.TaskStore
import com.edu.quickaside.application.tasks.UndoTaskCompletionChangeResult
import com.edu.quickaside.application.tasks.UndoTaskCreateResult
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class PendientesUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var store: FakeTaskStore
    private lateinit var actions: FakeReversibleTaskActions

    @Test
    fun pendientesOpensRealUiWithPersonalDefaultGlobalCaptureAndHonestSyncStatus() {
        setContent(
            tasks = listOf(
                task("personal", "Llamar al taller"),
                task("work", "Revisar el PR", TaskSpace.TRABAJO),
            ),
        )
        openPendientes()

        composeRule.onNodeWithText("Personal").assertIsDisplayed()
        composeRule.onNodeWithText("Llamar al taller").assertIsDisplayed()
        composeRule.onNodeWithText("Revisar el PR").assertDoesNotExist()
        composeRule.onNodeWithText("Google Tasks aún no conectado").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Capturar").assertIsDisplayed()
    }

    @Test
    fun switchingToTrabajoFiltersOnlyAndCreateUsesSelectedSpaceWithNullDueDate() {
        setContent(
            tasks = listOf(
                task("personal", "Sólo Personal"),
                task("work", "Sólo Trabajo", TaskSpace.TRABAJO),
            ),
        )
        openPendientes()

        composeRule.onNodeWithText("Trabajo").performClick()
        waitForText("Sólo Trabajo")
        composeRule.onNodeWithText("Sólo Personal").assertDoesNotExist()
        assertEquals(2, store.tasks.size)

        val exactTitle = "  Preparar demo  "
        composeRule.onNode(hasSetTextAction()).performTextInput(exactTitle)
        composeRule.onNodeWithContentDescription("Agregar pendiente").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { actions.createCalls.size == 1 }
        val call = actions.createCalls.single()
        assertEquals(exactTitle, call.title)
        assertEquals(TaskSpace.TRABAJO, call.space)
        assertEquals(null, call.dueDate)
        waitForText("Preparar demo")
    }

    @Test
    fun dueDatesSectionsAndDeterministicOrderingAreVisible() {
        setContent(
            tasks = listOf(
                task("no-date", "Sin fecha"),
                task("due-late", "Fecha posterior", dueDate = LocalDate.of(2026, 9, 17)),
                task("due-beta", "Beta", dueDate = LocalDate.of(2026, 9, 10)),
                task("due-alpha", "alfa", dueDate = LocalDate.of(2026, 9, 10)),
                task(
                    "completed-old",
                    "Completada antigua",
                    completedAt = Instant.parse("2026-09-10T10:00:00Z"),
                ),
                task(
                    "completed-new",
                    "Completada reciente",
                    completedAt = Instant.parse("2026-09-12T10:00:00Z"),
                ),
            ),
        )
        openPendientes()

        waitForText("10 sep 2026")
        waitForText("17 sep 2026")
        assertTextOrder(
            listOf("alfa", "Beta", "Fecha posterior", "Sin fecha"),
        )
        composeRule.onNodeWithTag("PendientesList")
            .performScrollToNode(hasText("Completada reciente"))
        waitForText("Completada reciente")
        waitForText("Pendientes")
        waitForText("Completados")
        assertTextOrder(listOf("Completada reciente", "Completada antigua"))
    }

    @Test
    fun eachSpaceHasUsefulEmptyState() {
        setContent()
        openPendientes()
        waitForText("No hay pendientes en Personal.")

        composeRule.onNodeWithText("Trabajo").performClick()
        waitForText("No hay pendientes en Trabajo.")
        composeRule.onNodeWithContentDescription("Agregar pendiente").assertIsNotEnabled()
    }

    @Test
    fun blankTextCannotSubmit() {
        setContent()
        openPendientes()

        composeRule.onNode(hasSetTextAction()).performTextInput("   ")
        composeRule.onNodeWithContentDescription("Agregar pendiente").assertIsNotEnabled()
        assertTrue(actions.createCalls.isEmpty())
    }

    @Test
    fun createSuccessUpdatesUiAndUndoUsesExactLedgerAndTaskIds() {
        setContent()
        openPendientes()

        val exactTitle = "  Comprar pan  "
        composeRule.onNode(hasSetTextAction()).performTextInput(exactTitle)
        composeRule.onNodeWithContentDescription("Agregar pendiente").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { actions.createCalls.size == 1 }
        waitForText("Comprar pan")
        waitForText("Pendiente agregado")
        composeRule.onNodeWithText("Deshacer").assertIsDisplayed()
        assertEquals(exactTitle, actions.createCalls.single().title)
        assertEquals(TaskSpace.PERSONAL, actions.createCalls.single().space)
        assertEquals(null, actions.createCalls.single().dueDate)

        composeRule.onNodeWithText("Deshacer").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { actions.undoCreateCalls.size == 1 }
        val undoCall = actions.undoCreateCalls.single()
        assertEquals(ActionLedgerEntryId("created-entry-1"), undoCall.actionLedgerEntryId)
        assertEquals(TaskId("created-task-1"), undoCall.expectedTaskId)
        waitForText("No hay pendientes en Personal.")
        composeRule.onNodeWithText("Comprar pan").assertDoesNotExist()
    }

    @Test
    fun createFailureKeepsEnteredText() {
        setContent()
        actions.createResult = CreateTaskActionResult.Failed(
            IllegalStateException("unavailable"),
        )
        openPendientes()

        val entered = "Conservar este pendiente"
        composeRule.onNode(hasSetTextAction()).performTextInput(entered)
        composeRule.onNodeWithContentDescription("Agregar pendiente").performClick()

        waitForText("No se pudo agregar el pendiente.")
        assertEquals(entered, editableText())
    }

    @Test
    fun createUndoFailureReloadsPersistedStateAndDoesNotInventSuccess() {
        setContent()
        actions.undoCreateResult = UndoTaskCreateResult.Failed(
            IllegalStateException("unavailable"),
        )
        openPendientes()

        composeRule.onNode(hasSetTextAction()).performTextInput("No conservar tras undo")
        composeRule.onNodeWithContentDescription("Agregar pendiente").performClick()
        waitForText("Pendiente agregado")
        composeRule.onNodeWithText("Deshacer").performClick()

        waitForText("No se pudo deshacer.")
        composeRule.onNodeWithText("No conservar tras undo").assertDoesNotExist()
        composeRule.onNodeWithText("No hay pendientes en Personal.").assertIsDisplayed()
    }

    @Test
    fun completeSuccessUsesExactTaskIdShowsUndoAndCompletionUndoReloadsState() {
        val pending = task("complete-me", "Enviar factura")
        val completed = pending.copy(
            completedAt = Instant.parse("2026-09-12T12:00:00Z"),
        )
        setContent(tasks = listOf(pending))
        actions.completeResult = TaskCompletionActionResult.Changed(
            task = completed,
            actionLedgerEntryId = ActionLedgerEntryId("complete-entry-1"),
        )
        actions.onUndoCompletion = { store.tasks = listOf(pending) }
        openPendientes()

        composeRule
            .onNodeWithContentDescription("Marcar Enviar factura como completado")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { actions.completeCalls.size == 1 }
        assertEquals(listOf(TaskId("complete-me")), actions.completeCalls)
        waitForText("Completados")
        waitForText("Pendiente completado")
        composeRule.onNodeWithText("Deshacer").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { actions.undoCompletionCalls.size == 1 }
        val undoCall = actions.undoCompletionCalls.single()
        assertEquals(ActionLedgerEntryId("complete-entry-1"), undoCall.actionLedgerEntryId)
        assertEquals(TaskId("complete-me"), undoCall.expectedTaskId)
        waitForText("Pendientes")
        composeRule.onNodeWithContentDescription("Marcar Enviar factura como completado")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reabrir Enviar factura").assertDoesNotExist()
    }

    @Test
    fun reopenSuccessUsesExactTaskIdAndShowsUndo() {
        val completed = task(
            "reopen-me",
            "Revisar contrato",
            completedAt = Instant.parse("2026-09-12T12:00:00Z"),
        )
        setContent(tasks = listOf(completed))
        actions.reopenResult = TaskCompletionActionResult.Changed(
            task = completed.copy(completedAt = null),
            actionLedgerEntryId = ActionLedgerEntryId("reopen-entry-1"),
        )
        openPendientes()

        composeRule.onNodeWithContentDescription("Reabrir Revisar contrato").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { actions.reopenCalls.size == 1 }
        assertEquals(listOf(TaskId("reopen-me")), actions.reopenCalls)
        waitForText("Pendiente reabierto")
        composeRule.onNodeWithText("Deshacer").assertIsDisplayed()
    }

    @Test
    fun completionFailureDoesNotInventSuccessOrChangeVisibleTask() {
        val pending = task("failed-complete", "No completar")
        setContent(tasks = listOf(pending))
        actions.completeResult = TaskCompletionActionResult.Failed(
            IllegalStateException("unavailable"),
        )
        openPendientes()

        composeRule
            .onNodeWithContentDescription("Marcar No completar como completado")
            .performClick()
        waitForText("No se pudo actualizar el pendiente.")
        composeRule.onNodeWithText("Pendiente completado").assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription("Marcar No completar como completado")
            .assertIsDisplayed()
    }

    @Test
    fun alreadyInRequestedStateDoesNotInventCompletionSuccess() {
        val pending = task("already-pending", "Ya estaba pendiente")
        setContent(tasks = listOf(pending))
        actions.completeResult = TaskCompletionActionResult.AlreadyInRequestedState
        openPendientes()

        composeRule
            .onNodeWithContentDescription("Marcar Ya estaba pendiente como completado")
            .performClick()
        waitForText("El pendiente ya estaba actualizado.")
        composeRule.onNodeWithText("Pendiente completado").assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription("Marcar Ya estaba pendiente como completado")
            .assertIsDisplayed()
    }

    @Test
    fun readFailureShowsRetryAndRetryLoadsLocalState() {
        setContent(readFailure = IllegalStateException("unavailable"))
        openPendientes()
        waitForText("No se pudieron cargar tus pendientes.")

        composeRule.activity.runOnUiThread { store.readFailure = null }
        composeRule.onNodeWithContentDescription("Reintentar carga de pendientes").performClick()
        waitForText("No hay pendientes en Personal.")
    }

    @Test
    fun unavailableSnapshotBlocksCreatePreservesInputAndRetryKeepsExistingTasks() {
        val readGate = CompletableDeferred<Unit>()
        setContent(
            tasks = listOf(task("existing", "Tarea existente")),
            readGate = readGate,
            readResponses = listOf(
                FakeReadAllResponse.Failure(IllegalStateException("unavailable")),
                FakeReadAllResponse.Success(listOf(task("existing", "Tarea existente"))),
            ),
        )
        openPendientes()
        waitForText("Cargando pendientes…")

        val entered = "Conservar tras reintento"
        composeRule.onNode(hasSetTextAction()).performTextInput(entered)
        composeRule.onNodeWithContentDescription("Agregar pendiente").assertIsNotEnabled()
        assertEquals(entered, editableText())
        assertTrue(actions.createCalls.isEmpty())

        readGate.complete(Unit)
        waitForText("No se pudieron cargar tus pendientes.")
        composeRule.onNodeWithContentDescription("Agregar pendiente").assertIsNotEnabled()
        assertEquals(entered, editableText())
        assertTrue(actions.createCalls.isEmpty())

        composeRule.activity.runOnUiThread { composeRule.activity.currentFocus?.clearFocus() }
        composeRule.onNodeWithContentDescription("Reintentar carga de pendientes").performClick()
        waitForEnabled("Agregar pendiente")
        composeRule.onNodeWithContentDescription("Agregar pendiente").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { actions.createCalls.size == 1 }
        assertEquals(entered, actions.createCalls.single().title)
        waitForText("Tarea existente")
        waitForText("Conservar tras reintento")
    }

    private fun setContent(
        tasks: List<Task> = emptyList(),
        readFailure: Exception? = null,
        readGate: CompletableDeferred<Unit>? = null,
        readResponses: List<FakeReadAllResponse>? = null,
    ) {
        store = FakeTaskStore(tasks)
        store.readFailure = readFailure
        store.readAllGate = readGate
        store.readAllResponses = readResponses
        actions = FakeReversibleTaskActions()
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = CaptureSubmission(CaptureWriter { }),
                        captureReader = CaptureReader { emptyList() },
                        taskStore = store,
                        reversibleTaskActions = actions,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun openPendientes() {
        composeRule.onNodeWithText("Pendientes").performClick()
        waitForText("Google Tasks aún no conectado")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForEnabled(contentDescription: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription(contentDescription).assertIsEnabled()
            }.isSuccess
        }
    }

    private fun editableText(): String = composeRule
        .onNode(hasSetTextAction())
        .fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
        .text

    private fun assertTextOrder(texts: List<String>) {
        val topPositions = texts.map { text ->
            composeRule.onNodeWithText(text, substring = false).fetchSemanticsNode()
                .boundsInRoot.top
        }
        assertTrue(
            "Expected $texts in vertical order but positions were $topPositions",
            topPositions.zipWithNext().all { (first, second) -> first < second },
        )
    }

    private fun task(
        id: String,
        title: String,
        space: TaskSpace = TaskSpace.PERSONAL,
        dueDate: LocalDate? = null,
        completedAt: Instant? = null,
    ): Task = Task(
        id = TaskId(id),
        title = title,
        space = space,
        dueDate = dueDate,
        completedAt = completedAt,
    )
}

private sealed interface FakeReadAllResponse {
    data class Success(val tasks: List<Task>) : FakeReadAllResponse
    data class Failure(val exception: Exception) : FakeReadAllResponse
}

private class FakeTaskStore(initialTasks: List<Task>) : TaskStore {
    var tasks: List<Task> = initialTasks
    var readFailure: Exception? = null
    var readAllGate: CompletableDeferred<Unit>? = null
    var readAllResponses: List<FakeReadAllResponse>? = null
    private var readAllResponseIndex = 0

    override suspend fun save(task: Task) {
        tasks = tasks.filterNot { it.id == task.id } + task
    }

    override suspend fun getById(id: TaskId): Task? = tasks.singleOrNull { it.id == id }

    override suspend fun readAll(): List<Task> {
        readAllGate?.await()
        readAllResponses?.getOrNull(readAllResponseIndex++)?.let { response ->
            return when (response) {
                is FakeReadAllResponse.Success -> response.tasks
                is FakeReadAllResponse.Failure -> throw response.exception
            }
        }
        return readFailure?.let { throw it } ?: tasks
    }
}

private class FakeReversibleTaskActions : ReversibleTaskActions {
    data class CreateCall(
        val title: String,
        val space: TaskSpace,
        val dueDate: LocalDate?,
    )

    data class UndoCreateCall(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val expectedTaskId: TaskId,
    )

    data class UndoCompletionCall(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val expectedTaskId: TaskId,
    )

    val createCalls = mutableListOf<CreateCall>()
    val undoCreateCalls = mutableListOf<UndoCreateCall>()
    val completeCalls = mutableListOf<TaskId>()
    val reopenCalls = mutableListOf<TaskId>()
    val undoCompletionCalls = mutableListOf<UndoCompletionCall>()
    var createResult: CreateTaskActionResult? = null
    var undoCreateResult: UndoTaskCreateResult? = null
    var completeResult: TaskCompletionActionResult? = null
    var reopenResult: TaskCompletionActionResult? = null
    var undoCompletionResult: UndoTaskCompletionChangeResult? = null
    var onUndoCompletion: (() -> Unit)? = null

    override suspend fun create(
        title: String,
        space: TaskSpace,
        dueDate: LocalDate?,
    ): CreateTaskActionResult {
        createCalls += CreateCall(title, space, dueDate)
        return createResult ?: CreateTaskActionResult.Saved(
            task = Task(
                id = TaskId("created-task-${createCalls.size}"),
                title = title,
                space = space,
                dueDate = dueDate,
            ),
            actionLedgerEntryId = ActionLedgerEntryId("created-entry-${createCalls.size}"),
        )
    }

    override suspend fun undoCreate(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskId: TaskId,
    ): UndoTaskCreateResult {
        undoCreateCalls += UndoCreateCall(actionLedgerEntryId, expectedTaskId)
        return undoCreateResult ?: UndoTaskCreateResult.Undone(
            actionLedgerEntryId = actionLedgerEntryId,
            taskId = expectedTaskId,
        )
    }

    override suspend fun complete(taskId: TaskId): TaskCompletionActionResult {
        completeCalls += taskId
        return completeResult ?: TaskCompletionActionResult.Failed(
            IllegalStateException("complete result not configured"),
        )
    }

    override suspend fun reopen(taskId: TaskId): TaskCompletionActionResult {
        reopenCalls += taskId
        return reopenResult ?: TaskCompletionActionResult.Failed(
            IllegalStateException("reopen result not configured"),
        )
    }

    override suspend fun undoCompletionChange(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskId: TaskId,
    ): UndoTaskCompletionChangeResult {
        undoCompletionCalls += UndoCompletionCall(actionLedgerEntryId, expectedTaskId)
        onUndoCompletion?.invoke()
        return undoCompletionResult ?: UndoTaskCompletionChangeResult.Undone(
            actionLedgerEntryId = actionLedgerEntryId,
            taskId = expectedTaskId,
        )
    }
}

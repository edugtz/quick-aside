package com.edu.quickaside

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureInterpretationResult
import com.edu.quickaside.application.capture.CaptureInterpreter
import com.edu.quickaside.application.capture.CapturePlanMemoryExecutionResult
import com.edu.quickaside.application.capture.CapturePlanMemoryExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanMemoryExecutor
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CreatedMemoryRecord
import com.edu.quickaside.application.capture.MemoryExecutionTarget
import com.edu.quickaside.application.capture.UndoCapturePlanMemoryExecutionResult
import com.edu.quickaside.application.memory.MemoryStore
import com.edu.quickaside.application.memory.NoteCreationResult
import com.edu.quickaside.application.memory.StructuredLogCreationResult
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureKind
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import com.edu.quickaside.domain.tasks.TaskSpace
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureMemoryAutoExecutionUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val now = Instant.parse("2026-10-03T18:00:00Z")
    private val noteAction = CapturePlanAction.CreateNote("Nota visible")
    private val logAction = CapturePlanAction.CreateStructuredLog(mapOf("peso" to "210 lbs"))

    @Test
    fun singleNoteTextReceiptForwardsExactTargetedUndo() {
        val receipt = receipt(noteRecord("note"))
        val executor = RecordingMemoryExecutor(receipt)
        setContent(listOf(noteAction), executor)
        submitText("Nota visible")
        undoAndAssert(receipt, executor, "Nota guardada")
    }

    @Test
    fun singleStructuredLogTextReceiptForwardsLogTarget() {
        val receipt = receipt(logRecord())
        val executor = RecordingMemoryExecutor(receipt)
        setContent(listOf(logAction), executor)
        submitText("Hoy hice 210 lbs")
        undoAndAssert(receipt, executor, "Registro guardado")
    }

    @Test
    fun interleavedMemoryBatchPreservesUndoOrder() {
        val receipt = receipt(noteRecord("second"), logRecord(), noteRecord("first"))
        val executor = RecordingMemoryExecutor(receipt)
        setContent(listOf(noteAction, logAction, CapturePlanAction.CreateNote("Otra nota")), executor)
        submitText("Nota, registro y otra nota")
        undoAndAssert(receipt, executor, "3 elementos guardados en Memoria")
        assertEquals(
            listOf(MemoryExecutionTarget.Note(NoteId("second")),
                MemoryExecutionTarget.StructuredLog(StructuredLogId("log")),
                MemoryExecutionTarget.Note(NoteId("first"))),
            executor.undoCalls.single().targets,
        )
    }

    @Test
    fun voiceExecutesOnceAndClosesCaptureDespiteRepeatedFinalEvents() {
        val executor = RecordingMemoryExecutor(receipt(noteRecord("voice")))
        val factory = FakeSpeechTranscriberFactory()
        val captures = mutableListOf<Capture>()
        setContent(listOf(noteAction), executor, speechFactory = factory, captures = captures)
        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")
        factory.latest().emitFinal("Nota por voz")
        factory.latest().emitFinal("No duplicar")
        waitForText("Nota guardada")
        composeRule.onNodeWithText("Deshacer").assertIsDisplayed()
        composeRule.onNodeWithText("Captura").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Capturar").assertIsDisplayed()
        assertEquals(1, executor.plans.size)
        assertEquals(1, captures.size)
        assertEquals(CaptureKind.VOICE, captures.single().kind)
    }

    @Test
    fun rejectionShowsGenericFailureWithoutReceiptOrUndo() {
        assertExecutionFailure(CapturePlanMemoryExecutionResult.Rejected(
            0, CapturePlanMemoryExecutionRejectionReason.BLANK_NOTE_TEXT,
        ))
    }

    @Test
    fun executionFailureShowsGenericFailureWithoutReceiptOrUndo() {
        assertExecutionFailure(CapturePlanMemoryExecutionResult.Failed(IllegalStateException("write failed")))
    }

    @Test
    fun failedUndoNeverClaimsSuccessAndRefreshesMemory() {
        assertUndoFailure(UndoCapturePlanMemoryExecutionResult.Failed(IllegalStateException("undo failed")))
    }

    @Test
    fun nonSuccessUndoNeverClaimsSuccessAndRefreshesMemory() {
        assertUndoFailure(UndoCapturePlanMemoryExecutionResult.TargetMismatch)
    }

    @Test
    fun mixedFamilyIsNotAppliedWithoutMemoryExecutionOrUndo() {
        assertNotEligible(listOf(noteAction, CapturePlanAction.CreateTask(TaskSpace.PERSONAL, "Pagar luz")))
    }

    @Test
    fun unsupportedUndoLastIsNotAppliedWithoutExecutionOrUndo() {
        assertNotEligible(listOf(CapturePlanAction.UndoLast))
    }

    @Test
    fun globalCaptureRefreshesNotesAfterExecutionAndUndo() {
        assertMemoryScreenRefresh(isNote = true)
    }

    @Test
    fun globalCaptureRefreshesStructuredLogsAfterExecutionAndUndo() {
        assertMemoryScreenRefresh(isNote = false)
    }

    private fun assertMemoryScreenRefresh(isNote: Boolean) {
        val receipt = receipt(if (isNote) noteRecord("visible") else logRecord())
        val store = FakeMemoryStore()
        val executor = RecordingMemoryExecutor(receipt, store = store)
        val factory = FakeSpeechTranscriberFactory()
        setContent(listOf(if (isNote) noteAction else logAction), executor, store, factory)
        openMemoryScreen(isNote)
        val empty = if (isNote) "Aún no tienes notas." else "Aún no tienes registros."
        val visible = if (isNote) "Nota visible" else "210 lbs"
        waitForText(empty)
        composeRule.onNodeWithContentDescription("Capturar").performClick()
        waitForText("Listo para escuchar…")
        factory.latest().emitFinal("Guardar en memoria")
        waitForText(if (isNote) "Nota guardada" else "Registro guardado")
        waitForText(visible)
        val readsBeforeUndo = store.reads
        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("Cambio deshecho")
        waitForText(empty)
        composeRule.onNodeWithText(visible).assertDoesNotExist()
        assertTrue(store.reads > readsBeforeUndo)
        assertEquals(1, executor.plans.size)
        assertEquals(listOf(UndoCall(receipt.actionLedgerEntryId, receipt.records.map { it.target })), executor.undoCalls)
    }

    private fun assertExecutionFailure(result: CapturePlanMemoryExecutionResult) {
        val executor = RecordingMemoryExecutor(result)
        setContent(listOf(noteAction, logAction, noteAction), executor)
        submitText("Falla memoria")
        waitForText("Captura guardada · no se pudo aplicar la interpretación")
        assertNoSuccessOrUndo()
        assertEquals(1, executor.plans.size)
    }

    private fun assertNotEligible(actions: List<CapturePlanAction>) {
        val executor = RecordingMemoryExecutor(receipt(noteRecord("unused")))
        setContent(actions, executor)
        submitText("Sin aplicar")
        waitForText("Captura guardada · interpretación lista, sin aplicar")
        assertNoSuccessOrUndo()
        assertTrue(executor.plans.isEmpty())
    }

    private fun assertNoSuccessOrUndo() {
        composeRule.onNodeWithText("Nota guardada").assertDoesNotExist()
        composeRule.onNodeWithText("Registro guardado").assertDoesNotExist()
        composeRule.onNodeWithText("3 elementos guardados en Memoria").assertDoesNotExist()
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()
    }

    private fun assertUndoFailure(result: UndoCapturePlanMemoryExecutionResult) {
        val store = FakeMemoryStore()
        val executor = RecordingMemoryExecutor(receipt(noteRecord("retained")), result, store)
        val factory = FakeSpeechTranscriberFactory()
        setContent(listOf(noteAction), executor, store, factory)
        openMemoryScreen(isNote = true)
        waitForText("Aún no tienes notas.")
        composeRule.onNodeWithContentDescription("Capturar").performClick()
        waitForText("Listo para escuchar…")
        factory.latest().emitFinal("Nota visible")
        waitForText("Nota guardada")
        waitForText("Nota visible")
        val readsBeforeUndo = store.reads
        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("No se pudo deshacer.")
        composeRule.onNodeWithText("Cambio deshecho").assertDoesNotExist()
        waitForText("Nota visible")
        assertTrue(store.reads > readsBeforeUndo)
    }

    private fun openMemoryScreen(isNote: Boolean) {
        composeRule.onNode(hasText("Memoria") and hasClickAction()).performClick()
        composeRule.onNodeWithText(if (isNote) "Notas" else "Registros").performClick()
    }

    private fun undoAndAssert(
        receipt: CapturePlanMemoryExecutionResult.Executed,
        executor: RecordingMemoryExecutor,
        message: String,
    ) {
        waitForText(message)
        composeRule.onNodeWithText("Deshacer").assertIsDisplayed().performClick()
        waitForText("Cambio deshecho")
        assertEquals(1, executor.plans.size)
        assertEquals(listOf(UndoCall(receipt.actionLedgerEntryId, receipt.records.map { it.target })), executor.undoCalls)
    }

    private fun setContent(
        actions: List<CapturePlanAction>,
        executor: RecordingMemoryExecutor,
        store: FakeMemoryStore = FakeMemoryStore(),
        speechFactory: FakeSpeechTranscriberFactory = FakeSpeechTranscriberFactory(),
        captures: MutableList<Capture> = mutableListOf(),
    ) {
        val submission = CaptureSubmission(
            writer = CaptureWriter { captures += it },
            interpreter = CaptureInterpreter { CaptureInterpretationResult.Success(CapturePlan(it.id, actions)) },
            memoryExecutor = executor,
            idProvider = { CaptureId("ui-memory-capture") },
            capturedAtProvider = { now },
        )
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = submission,
                        captureReader = { emptyList() },
                        capturePlanMemoryExecutor = executor,
                        memoryStore = store,
                        speechTranscriberFactory = speechFactory,
                        microphonePermissionController = FakeMicrophonePermissionController(granted = true),
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
            runCatching { composeRule.onNodeWithText(text, substring = true).assertIsDisplayed() }.isSuccess
        }
    }

    private fun noteRecord(id: String) = CreatedMemoryRecord.Note(Note(NoteId(id), "Nota visible", createdAt = now))
    private fun logRecord() = CreatedMemoryRecord.StructuredLog(
        StructuredLog(StructuredLogId("log"), mapOf("peso" to "210 lbs"), createdAt = now),
    )
    private fun receipt(vararg records: CreatedMemoryRecord) = CapturePlanMemoryExecutionResult.Executed(
        records.toList(), ActionLedgerEntryId("memory-ledger"),
    )

    private data class UndoCall(val ledgerId: ActionLedgerEntryId, val targets: List<MemoryExecutionTarget>)

    private class RecordingMemoryExecutor(
        private val result: CapturePlanMemoryExecutionResult,
        private val undoResult: UndoCapturePlanMemoryExecutionResult? = null,
        private val store: FakeMemoryStore? = null,
    ) : CapturePlanMemoryExecutor {
        val plans = mutableListOf<CapturePlan>()
        val undoCalls = mutableListOf<UndoCall>()
        override suspend fun execute(plan: CapturePlan): CapturePlanMemoryExecutionResult {
            plans += plan
            if (result is CapturePlanMemoryExecutionResult.Executed) store?.records = result.records
            return result
        }
        override suspend fun undoExecution(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedTargets: List<MemoryExecutionTarget>,
        ): UndoCapturePlanMemoryExecutionResult {
            undoCalls += UndoCall(actionLedgerEntryId, expectedTargets)
            val outcome = undoResult ?: UndoCapturePlanMemoryExecutionResult.Undone(actionLedgerEntryId, expectedTargets)
            if (outcome is UndoCapturePlanMemoryExecutionResult.Undone) store?.records = emptyList()
            return outcome
        }
    }

    private class FakeMemoryStore : MemoryStore {
        var records: List<CreatedMemoryRecord> = emptyList()
        var reads = 0
        override suspend fun readRecentNotes(limit: Int): List<Note> {
            reads++
            return records.filterIsInstance<CreatedMemoryRecord.Note>().map { it.note }.take(limit)
        }
        override suspend fun readRecentStructuredLogs(limit: Int): List<StructuredLog> {
            reads++
            return records.filterIsInstance<CreatedMemoryRecord.StructuredLog>().map { it.log }.take(limit)
        }
        override suspend fun createNote(text: String, sourceCaptureId: CaptureId?): NoteCreationResult = error("Unused")
        override suspend fun createStructuredLog(fields: Map<String, String>, sourceCaptureId: CaptureId?): StructuredLogCreationResult = error("Unused")
        override suspend fun getNote(id: NoteId): Note? = error("Unused")
        override suspend fun getStructuredLog(id: StructuredLogId): StructuredLog? = error("Unused")
    }
}

package com.edu.quickaside

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.memory.MemoryStore
import com.edu.quickaside.application.memory.NoteCreationResult
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.memory.NoteTimestampFormatter
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val timestampFormatter = NoteTimestampFormatter(
        zoneId = ZoneId.of("UTC"),
        locale = Locale.ENGLISH,
        clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneId.of("UTC")),
    )
    private lateinit var store: FakeNotesMemoryStore

    @Before
    fun setUp() {
        store = FakeNotesMemoryStore()
    }

    @Test
    fun memoriaExposesAnAccessibleNotesAffordanceWithoutAddingDestination() {
        setContent()
        openMemoria()

        waitForText("Capturas recientes")
        composeRule.onNodeWithContentDescription("Abrir notas").assertIsDisplayed()
        composeRule.onNodeWithText("Notas").assertIsDisplayed()
    }

    @Test
    fun openingNotesReadsRecentNotesAndPreservesSuppliedOrderAndLocalTime() {
        val newer = note("note-newer", "Nota más reciente", "2026-09-06T11:00:00Z")
        val older = note("note-older", "Nota anterior", "2026-09-05T18:30:00Z")
        store.recentNotes = listOf(newer, older)
        setContent()
        openNotes()

        waitForText("Nota más reciente")
        assertEquals(1, store.readCalls.get())
        composeRule.onNodeWithText("Nota anterior").assertIsDisplayed()
        composeRule.onNodeWithText("Creada · Hoy, 11:00").assertIsDisplayed()
        composeRule.onNodeWithText("Creada · Ayer, 18:30").assertIsDisplayed()

        val newerTop = composeRule.onNodeWithText("Nota más reciente")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val olderTop = composeRule.onNodeWithText("Nota anterior")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue("The supplied recent order must be preserved", newerTop < olderTop)
    }

    @Test
    fun emptyNotesRendersValidEmptyState() {
        setContent()
        openNotes()

        waitForText("Aún no tienes notas.")
        composeRule.onNodeWithText("Las notas que guardes aparecerán aquí.").assertIsDisplayed()
    }

    @Test
    fun whitespaceOnlyNoteCannotBeSubmitted() {
        setContent()
        openNotes()

        waitForText("Aún no tienes notas.")
        composeRule.onNode(hasSetTextAction()).performTextReplacement(" \t\n ")
        composeRule.onNodeWithContentDescription("Guardar nota").assertIsNotEnabled()
        assertTrue(store.createCalls.isEmpty())
    }

    @Test
    fun exactValidTextIsSentUnchangedWithNullSourceAndSavedNoteClearsInput() {
        val exactText = "  Llamar al taller  "
        val savedNote = note("saved-note", exactText, "2026-09-06T12:00:00Z")
        store.createResult = NoteCreationResult.Saved(savedNote)
        setContent()
        openNotes()

        waitForText("Aún no tienes notas.")
        composeRule.onNode(hasSetTextAction()).performTextReplacement(exactText)
        composeRule.onNodeWithContentDescription("Guardar nota").performClick()

        waitForText("Nota guardada")
        assertEquals(listOf(NoteCall(exactText, null)), store.createCalls)
        assertEquals("", editableText())
        composeRule.onNodeWithText(exactText, substring = false).assertIsDisplayed()
    }

    @Test
    fun saveFailureRetainsInputAndShowsUnderstandableFeedback() {
        val entered = "No borrar esta nota"
        store.createResult = NoteCreationResult.Failed(IllegalStateException("database detail"))
        setContent()
        openNotes()

        waitForText("Aún no tienes notas.")
        composeRule.onNode(hasSetTextAction()).performTextReplacement(entered)
        composeRule.onNodeWithContentDescription("Guardar nota").performClick()

        waitForText("No se pudo guardar la nota.")
        assertEquals(entered, editableText())
        composeRule.onNodeWithText("database detail").assertDoesNotExist()
    }

    @Test
    fun loadingFailureRendersRetryAndRetryReloadsNotes() {
        store.readFailuresRemaining = 1
        store.recentNotes = listOf(note("after-retry", "Después del reintento", "2026-09-06T10:00:00Z"))
        setContent()
        openNotes()

        waitForText("No se pudieron cargar tus notas.")
        composeRule.onNodeWithContentDescription("Reintentar carga de notas").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reintentar carga de notas").performClick()

        waitForText("Después del reintento")
        assertEquals(2, store.readCalls.get())
    }

    @Test
    fun duplicateSaveIsPreventedWhileSaving() {
        val savedNote = note("slow-note", "Nota guardada después", "2026-09-06T12:00:00Z")
        val gate = CompletableDeferred<NoteCreationResult>()
        store.createGate = gate
        setContent()
        openNotes()

        waitForText("Aún no tienes notas.")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Nota en proceso")
        composeRule.onNodeWithContentDescription("Guardar nota").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { store.createCalls.size == 1 }

        composeRule.onNodeWithContentDescription("Guardar nota").assertIsNotEnabled()
        assertEquals(1, store.createCalls.size)
        gate.complete(NoteCreationResult.Saved(savedNote))
        waitForText("Nota guardada después")
    }

    @Test
    fun existingNoteRowsAreReadOnlyAndHaveNoEditOrDeleteActions() {
        store.recentNotes = listOf(note("read-only", "Una nota de solo lectura", "2026-09-06T09:00:00Z"))
        setContent()
        openNotes()

        waitForText("Una nota de solo lectura")
        composeRule.onAllNodes(hasText("Una nota de solo lectura") and hasClickAction())
            .assertCountEquals(0)
        composeRule.onNodeWithText("Editar").assertDoesNotExist()
        composeRule.onNodeWithText("Eliminar").assertDoesNotExist()
    }

    @Test
    fun toolbarBackFromNotesReturnsToCaptureHistory() {
        setContent()
        openNotes()

        waitForText("Aún no tienes notas.")
        composeRule.onNodeWithContentDescription("Volver a Memoria").performClick()

        waitForText("Capturas recientes")
        composeRule.onNodeWithContentDescription("Abrir notas").assertIsDisplayed()
    }

    @Test
    fun androidBackFromNotesReturnsToCaptureHistory() {
        setContent()
        openNotes()

        waitForText("Aún no tienes notas.")
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        waitForText("Capturas recientes")
        composeRule.onNodeWithContentDescription("Abrir notas").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationSwitchResetsMemoryRouteToHistory() {
        setContent()
        openNotes()

        waitForText("Aún no tienes notas.")
        composeRule.onNode(hasText("Pendientes") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Memoria") and hasClickAction()).performClick()

        waitForText("Capturas recientes")
        composeRule.onNodeWithContentDescription("Abrir notas").assertIsDisplayed()
        composeRule.onNodeWithText("Aún no tienes notas.").assertDoesNotExist()
    }

    @Test
    fun finalNoteCanScrollAboveGlobalCaptureFab() {
        store.recentNotes = (1..8).map { index ->
            note(
                id = "scroll-note-$index",
                text = if (index == 8) "Nota final" else "Nota $index",
                createdAt = "2026-09-06T${(12 - index).toString().padStart(2, '0')}:00:00Z",
            )
        }
        setContent()
        openNotes()

        waitForText("Notas recientes")
        composeRule.onNodeWithTag("NotesList")
            .performScrollToNode(hasText("Nota final"))

        val finalNoteBottom = composeRule.onNodeWithText("Nota final")
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        val fabTop = composeRule.onNodeWithContentDescription("Capturar")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue("The final Note card must scroll above the Capture FAB", finalNoteBottom <= fabTop)
    }

    @Test
    fun capturesRepresentativeNotesVisualEvidenceOnDevice() {
        setContent()
        openMemoria()
        waitForText("Capturas recientes")
        saveScreenshot("memoria-notes-affordance.png")

        openNotes()
        waitForText("Aún no tienes notas.")
        saveScreenshot("notes-empty-create.png")

        composeRule.onNodeWithContentDescription("Volver a Memoria").performClick()
        store.recentNotes = listOf(
            note("visual-newer", "Revisar el contrato", "2026-09-06T11:00:00Z"),
            note("visual-older", "Llamar al taller", "2026-09-05T18:30:00Z"),
        )
        openNotes()
        waitForText("Revisar el contrato")
        saveScreenshot("notes-with-two-notes.png")
    }

    private fun setContent() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = CaptureSubmission(CaptureWriter { }),
                        captureReader = CaptureReader { emptyList() },
                        memoryStore = store,
                        noteTimestampFormatter = timestampFormatter,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun openMemoria() {
        composeRule.onNode(hasText("Memoria") and hasClickAction()).performClick()
        composeRule.waitForIdle()
    }

    private fun openNotes() {
        openMemoria()
        composeRule.onNodeWithContentDescription("Abrir notas").performClick()
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun editableText(): String = composeRule
        .onNode(hasSetTextAction())
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text

    private fun note(id: String, text: String, createdAt: String): Note = Note(
        id = NoteId(id),
        text = text,
        sourceCaptureId = null,
        createdAt = Instant.parse(createdAt),
    )

    private fun saveScreenshot(fileName: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Quick Aside/Change 014",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = checkNotNull(
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
            )
            try {
                resolver.openOutputStream(uri).use { output ->
                    checkNotNull(output)
                    check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                resolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    },
                    null,
                    null,
                )
            } catch (failure: Throwable) {
                resolver.delete(uri, null, null)
                throw failure
            }
        }
        screenshot.recycle()
    }
}

private data class NoteCall(
    val text: String,
    val sourceCaptureId: CaptureId?,
)

private class FakeNotesMemoryStore : MemoryStore {
    var recentNotes: List<Note> = emptyList()
    var readFailuresRemaining: Int = 0
    var createResult: NoteCreationResult = NoteCreationResult.Failed(
        IllegalStateException("No create result configured"),
    )
    var createGate: CompletableDeferred<NoteCreationResult>? = null
    val readCalls = AtomicInteger(0)
    val createCalls = mutableListOf<NoteCall>()

    override suspend fun createNote(text: String, sourceCaptureId: CaptureId?): NoteCreationResult {
        createCalls += NoteCall(text, sourceCaptureId)
        return createGate?.await() ?: createResult
    }

    override suspend fun readRecentNotes(limit: Int): List<Note> {
        readCalls.incrementAndGet()
        if (readFailuresRemaining > 0) {
            readFailuresRemaining -= 1
            throw IllegalStateException("read failure")
        }
        return recentNotes
    }

    override suspend fun getNote(id: NoteId): Note? = recentNotes.firstOrNull { it.id == id }

    override suspend fun createStructuredLog(
        fields: Map<String, String>,
        sourceCaptureId: CaptureId?,
    ): com.edu.quickaside.application.memory.StructuredLogCreationResult =
        error("Structured logs are outside this fake's scope")

    override suspend fun readRecentStructuredLogs(limit: Int): List<StructuredLog> =
        error("Structured logs are outside this fake's scope")

    override suspend fun getStructuredLog(id: StructuredLogId): StructuredLog? =
        error("Structured logs are outside this fake's scope")
}

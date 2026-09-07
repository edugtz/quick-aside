package com.edu.quickaside

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.memory.MemoryStore
import com.edu.quickaside.application.memory.NoteCreationResult
import com.edu.quickaside.application.memory.StructuredLogCreationResult
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
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
class StructuredLogsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val timestampFormatter = NoteTimestampFormatter(
        zoneId = ZoneId.of("UTC"),
        locale = Locale.ENGLISH,
        clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneId.of("UTC")),
    )
    private lateinit var store: FakeStructuredLogsMemoryStore

    @Before
    fun setUp() {
        store = FakeStructuredLogsMemoryStore()
    }

    @Test
    fun memoriaExposesNotesAndStructuredLogsWithoutAddingDestination() {
        setContent()
        openMemoria()

        waitForText("Capturas recientes")
        composeRule.onNodeWithContentDescription("Abrir notas").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Abrir registros").assertIsDisplayed()
        composeRule.onNodeWithText("Notas").assertIsDisplayed()
        composeRule.onNodeWithText("Registros").assertIsDisplayed()
        listOf("Inicio", "Pendientes", "Listas", "Memoria").forEach { label ->
            composeRule.onAllNodes(hasText(label) and hasClickAction()).assertCountEquals(1)
        }
        composeRule.onNodeWithText("Buscar").assertDoesNotExist()
        composeRule.onNodeWithText("Archivo").assertDoesNotExist()
    }

    @Test
    fun openingStructuredLogsReadsRecentLogsAndShowsSuppliedOrderAndLocalTime() {
        val newer = log(
            id = "log-newer",
            fields = linkedMapOf("zeta" to "último", "alpha" to "primero"),
            createdAt = "2026-09-06T11:00:00Z",
        )
        val older = log(
            id = "log-older",
            fields = mapOf("campo" to "anterior"),
            createdAt = "2026-09-05T18:30:00Z",
        )
        store.recentStructuredLogs = listOf(newer, older)
        setContent()
        openStructuredLogs()

        waitForText("último")
        assertEquals(1, store.readCalls.get())
        composeRule.onNodeWithText("Creado · Hoy, 11:00").assertIsDisplayed()
        composeRule.onNodeWithText("Creado · Ayer, 18:30").assertIsDisplayed()

        val newerTop = composeRule.onNodeWithText("último").fetchSemanticsNode().boundsInRoot.top
        val olderTop = composeRule.onNodeWithText("anterior").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The supplied record order must be preserved", newerTop < olderTop)

        val alphaTop = composeRule.onNodeWithText("alpha").fetchSemanticsNode().boundsInRoot.top
        val zetaTop = composeRule.onNodeWithText("zeta").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Fields must render in key ascending order", alphaTop < zetaTop)
    }

    @Test
    fun emptyStateAndInitialEditorAreVisible() {
        setContent()
        openStructuredLogs()

        waitForText("Aún no tienes registros.")
        composeRule.onNodeWithText("Los registros que guardes aparecerán aquí.").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        composeRule.onNodeWithContentDescription("Guardar registro").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Eliminar campo 1").assertDoesNotExist()
    }

    @Test
    fun loadingStateIsVisibleWhileRecentLogsAreBeingRead() {
        val gate = CompletableDeferred<List<StructuredLog>>()
        store.readGate = gate
        setContent()
        openStructuredLogs()

        waitForText("Cargando registros…")
        gate.complete(emptyList())
        waitForText("Aún no tienes registros.")
    }

    @Test
    fun userCanAddAndRemoveRowsButCannotRemoveTheLastRow() {
        setContent()
        openStructuredLogs()
        waitForText("Aún no tienes registros.")

        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        composeRule.onNodeWithContentDescription("Agregar campo").performClick()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(4)
        composeRule.onNodeWithContentDescription("Eliminar campo 2").performClick()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        composeRule.onNodeWithContentDescription("Eliminar campo 1").assertDoesNotExist()
    }

    @Test
    fun incompleteRowsAndDuplicateExactKeysCannotBeSaved() {
        setContent()
        openStructuredLogs()
        waitForText("Aún no tienes registros.")

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[1].performTextReplacement("valor sin campo")
        composeRule.onNodeWithContentDescription("Guardar registro").assertIsNotEnabled()
        assertTrue(store.createCalls.isEmpty())

        fields[0].performTextReplacement("campo")
        composeRule.onNodeWithContentDescription("Guardar registro").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Agregar campo").performClick()
        composeRule.onAllNodes(hasSetTextAction())[2].performTextReplacement("campo")
        composeRule.onAllNodes(hasSetTextAction())[3].performTextReplacement("otro valor")
        composeRule.onNodeWithContentDescription("Guardar registro").assertIsNotEnabled()
        assertTrue(store.createCalls.isEmpty())
    }

    @Test
    fun keyWithoutValueCannotBeSaved() {
        setContent()
        openStructuredLogs()
        waitForText("Aún no tienes registros.")

        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("campo")
        composeRule.onNodeWithContentDescription("Guardar registro").assertIsNotEnabled()
        assertTrue(store.createCalls.isEmpty())
    }

    @Test
    fun exactWhitespaceIsPreservedAndManualCreateUsesNullSource() {
        val exactKey = "  ejercicio  "
        val exactValue = "  press inclinado  "
        store.createResult = StructuredLogCreationResult.Saved(
            log(
                id = "saved-log",
                fields = mapOf(exactKey to exactValue),
                createdAt = "2026-09-06T12:00:00Z",
            ),
        )
        setContent()
        openStructuredLogs()
        waitForText("Aún no tienes registros.")

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement(exactKey)
        fields[1].performTextReplacement(exactValue)
        composeRule.onNodeWithContentDescription("Guardar registro").performClick()

        waitForText("Registro guardado")
        assertEquals(
            listOf(LogCall(mapOf(exactKey to exactValue), null)),
            store.createCalls,
        )
    }

    @Test
    fun savedRecordIsVisibleAndEditorResetsToOneEmptyRow() {
        val saved = log(
            id = "saved-log",
            fields = mapOf("campo" to "valor"),
            createdAt = "2026-09-06T12:00:00Z",
        )
        store.createResult = StructuredLogCreationResult.Saved(saved)
        setContent()
        openStructuredLogs()
        waitForText("Aún no tienes registros.")

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("campo")
        fields[1].performTextReplacement("valor")
        composeRule.onNodeWithContentDescription("Guardar registro").performClick()

        waitForText("Registro guardado")
        composeRule.onNodeWithText("campo").assertIsDisplayed()
        composeRule.onNodeWithText("valor").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        assertEquals("", editableText(0))
        assertEquals("", editableText(1))
    }

    @Test
    fun failedSaveRetainsAllFieldsAndHidesInternalDetails() {
        store.createResult = StructuredLogCreationResult.Failed(
            IllegalStateException("database detail"),
        )
        setContent()
        openStructuredLogs()
        waitForText("Aún no tienes registros.")

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("campo")
        fields[1].performTextReplacement("valor")
        composeRule.onNodeWithContentDescription("Guardar registro").performClick()

        waitForText("No se pudo guardar el registro.")
        assertEquals("campo", editableText(0))
        assertEquals("valor", editableText(1))
        composeRule.onNodeWithText("database detail").assertDoesNotExist()
    }

    @Test
    fun duplicateSaveIsPreventedWhileSaving() {
        val gate = CompletableDeferred<StructuredLogCreationResult>()
        store.createGate = gate
        setContent()
        openStructuredLogs()
        waitForText("Aún no tienes registros.")

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("campo")
        fields[1].performTextReplacement("valor")
        composeRule.onNodeWithContentDescription("Guardar registro").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { store.createCalls.size == 1 }

        composeRule.onNodeWithContentDescription("Guardar registro").assertIsNotEnabled()
        assertEquals(1, store.createCalls.size)
        gate.complete(
            StructuredLogCreationResult.Saved(
                log("slow-log", mapOf("campo" to "valor"), "2026-09-06T12:00:00Z"),
            ),
        )
        waitForText("Registro guardado")
    }

    @Test
    fun readFailureShowsRetryAndRetryReloadsRecords() {
        store.readFailuresRemaining = 1
        store.recentStructuredLogs = listOf(
            log("after-retry", mapOf("después" to "reintento"), "2026-09-06T10:00:00Z"),
        )
        setContent()
        openStructuredLogs()

        waitForText("No se pudieron cargar tus registros.")
        composeRule.onNodeWithContentDescription("Reintentar carga de registros").performClick()
        waitForText("reintento")
        assertEquals(2, store.readCalls.get())
    }

    @Test
    fun recordCardsAreReadOnlyAndBackResetsToCaptureHistory() {
        store.recentStructuredLogs = listOf(
            log("read-only", mapOf("campo" to "solo lectura"), "2026-09-06T09:00:00Z"),
        )
        setContent()
        openStructuredLogs()

        waitForText("solo lectura")
        composeRule.onAllNodes(hasText("solo lectura") and hasClickAction()).assertCountEquals(0)
        composeRule.onNodeWithText("Editar").assertDoesNotExist()
        composeRule.onNodeWithText("Eliminar").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Volver a Memoria").performClick()
        waitForText("Capturas recientes")
        composeRule.onNodeWithContentDescription("Abrir registros").assertIsDisplayed()
    }

    @Test
    fun systemBackAndBottomNavigationResetStructuredLogsRoute() {
        setContent()
        openStructuredLogs()
        waitForText("Aún no tienes registros.")

        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForText("Capturas recientes")

        openStructuredLogs()
        waitForText("Aún no tienes registros.")
        composeRule.onNode(hasText("Pendientes") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Memoria") and hasClickAction()).performClick()
        waitForText("Capturas recientes")
        composeRule.onNodeWithText("Aún no tienes registros.").assertDoesNotExist()
    }

    @Test
    fun finalRecordCanScrollAboveGlobalCaptureFab() {
        store.recentStructuredLogs = (1..8).map { index ->
            log(
                id = "scroll-log-$index",
                fields = if (index == 8) {
                    mapOf("último" to "campo final")
                } else {
                    mapOf("campo" to "registro $index")
                },
                createdAt = "2026-09-06T${(12 - index).toString().padStart(2, '0')}:00:00Z",
            )
        }
        setContent()
        openStructuredLogs()

        waitForText("Registros recientes")
        composeRule.onNodeWithTag("StructuredLogsList")
            .performScrollToNode(hasText("campo final"))
        // Scroll to the absolute end so the measurement reflects the best the
        // user can do; ScrollBy clamps at the list's maximum scroll position.
        composeRule.onNodeWithTag("StructuredLogsList")
            .performSemanticsAction(SemanticsActions.ScrollBy) { action ->
                action(0f, 10_000_000f)
            }
        composeRule.waitForIdle()

        val finalCardBottom = composeRule.onNodeWithTag("StructuredLogCard-scroll-log-8")
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        val fabTop = composeRule.onNodeWithContentDescription("Capturar")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(
            "The final StructuredLog card must scroll above the Capture FAB " +
                "(cardBottom=$finalCardBottom fabTop=$fabTop)",
            finalCardBottom <= fabTop,
        )
    }

    @Test
    fun capturesRepresentativeStructuredLogsVisualEvidenceOnDevice() {
        setContent()
        openMemoria()
        waitForText("Capturas recientes")
        saveScreenshot("memoria-notas-registros-affordances.png")

        openStructuredLogs()
        waitForText("Aún no tienes registros.")
        saveScreenshot("structured-logs-empty-create.png")

        composeRule.onNodeWithContentDescription("Volver a Memoria").performClick()
        store.recentStructuredLogs = listOf(
            log("visual-newer", mapOf("ejercicio" to "press inclinado", "peso" to "210 lbs"), "2026-09-06T11:00:00Z"),
            log("visual-older", mapOf("lugar" to "gimnasio", "duración" to "45 min"), "2026-09-05T18:30:00Z"),
        )
        openStructuredLogs()
        waitForText("press inclinado")
        saveScreenshot("structured-logs-with-two-records.png")
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

    private fun openStructuredLogs() {
        openMemoria()
        composeRule.onNodeWithContentDescription("Abrir registros").performClick()
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun editableText(index: Int): String = composeRule
        .onAllNodes(hasSetTextAction())[index]
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text

    private fun saveScreenshot(fileName: String) {
        composeRule.waitForIdle()
        SystemClock.sleep(300)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Quick Aside/Change 015",
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

    private fun log(
        id: String,
        fields: Map<String, String>,
        createdAt: String,
    ): StructuredLog = StructuredLog(
        id = StructuredLogId(id),
        fields = fields,
        sourceCaptureId = null,
        createdAt = Instant.parse(createdAt),
    )
}

private data class LogCall(
    val fields: Map<String, String>,
    val sourceCaptureId: CaptureId?,
)

private class FakeStructuredLogsMemoryStore : MemoryStore {
    var recentStructuredLogs: List<StructuredLog> = emptyList()
    var readFailuresRemaining: Int = 0
    var readGate: CompletableDeferred<List<StructuredLog>>? = null
    var createResult: StructuredLogCreationResult = StructuredLogCreationResult.Failed(
        IllegalStateException("No create result configured"),
    )
    var createGate: CompletableDeferred<StructuredLogCreationResult>? = null
    val readCalls = AtomicInteger(0)
    val createCalls = mutableListOf<LogCall>()

    override suspend fun createNote(
        text: String,
        sourceCaptureId: CaptureId?,
    ): NoteCreationResult = error("Notes are outside this fake's scope")

    override suspend fun readRecentNotes(limit: Int): List<Note> =
        error("Notes are outside this fake's scope")

    override suspend fun getNote(id: NoteId): Note? =
        error("Notes are outside this fake's scope")

    override suspend fun createStructuredLog(
        fields: Map<String, String>,
        sourceCaptureId: CaptureId?,
    ): StructuredLogCreationResult {
        createCalls += LogCall(fields, sourceCaptureId)
        return createGate?.await() ?: createResult
    }

    override suspend fun readRecentStructuredLogs(limit: Int): List<StructuredLog> {
        readCalls.incrementAndGet()
        if (readFailuresRemaining > 0) {
            readFailuresRemaining -= 1
            throw IllegalStateException("read failure")
        }
        return readGate?.await() ?: recentStructuredLogs
    }

    override suspend fun getStructuredLog(id: StructuredLogId): StructuredLog? =
        recentStructuredLogs.firstOrNull { it.id == id }
}

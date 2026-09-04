package com.edu.quickaside

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureTranscriptCorrectionResult
import com.edu.quickaside.application.capture.CaptureTranscriptCorrector
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.data.local.QuickAsideDatabase
import com.edu.quickaside.data.local.RoomCaptureReader
import com.edu.quickaside.data.local.RoomCaptureTranscriptCorrector
import com.edu.quickaside.data.local.toDomain
import com.edu.quickaside.data.local.toEntity
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptCorrectionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "transcript-correction-ui-${UUID.randomUUID()}.db"
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun onlyVoiceCardsExposeTranscriptEdit() = runBlocking {
        database.captureDao().insert(
            capture(
                id = "voice-editable",
                input = CaptureInput.Voice("Voz editable"),
            ).toEntity(),
        )
        database.captureDao().insert(
            capture(
                id = "text-not-editable",
                input = CaptureInput.Text("Texto sin edición"),
            ).toEntity(),
        )
        setContent(corrector = RoomCaptureTranscriptCorrector(database.captureDao()))
        openMemoria()

        waitForText("Voz editable")
        composeRule.onNodeWithContentDescription("Editar transcript").assertIsDisplayed()
        composeRule.onNodeWithText("Texto sin edición").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Editar transcript").assertCountEquals(1)
        Unit
    }

    @Test
    fun uncorrectedVoiceEditorStartsWithOriginalAndShowsItAsReadOnly() = runBlocking {
        val original = capture(
            id = "voice-original",
            input = CaptureInput.Voice("comprar leche manana"),
        )
        database.captureDao().insert(original.toEntity())
        setContent(corrector = RoomCaptureTranscriptCorrector(database.captureDao()))
        openMemoria()

        openEditor()

        composeRule.onNodeWithText("Editar transcript").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertTextContains("comprar leche manana", substring = false)
        composeRule.onNodeWithText("Original: comprar leche manana").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        Unit
    }

    @Test
    fun correctedVoiceEditorStartsWithCurrentEffectiveTranscript() = runBlocking {
        val corrected = capture(
            id = "voice-already-corrected",
            input = CaptureInput.Voice("comprar leche manana"),
            correction = "Comprar leche mañana",
        )
        database.captureDao().insert(corrected.toEntity())
        setContent(corrector = RoomCaptureTranscriptCorrector(database.captureDao()))
        openMemoria()

        openEditor()

        composeRule.onNode(hasSetTextAction()).assertTextContains("Comprar leche mañana", substring = false)
        composeRule.onNodeWithText("Original: comprar leche manana").assertIsDisplayed()
        Unit
    }

    @Test
    fun exactNonBlankTextReachesCorrectorAndUpdatesVisibleCapture() = runBlocking {
        val original = capture(
            id = "voice-exact-input",
            input = CaptureInput.Voice("recognized text"),
        )
        val corrector = RecordingCorrector { _, correction ->
            CaptureTranscriptCorrectionResult.Saved(
                original.copy(transcriptCorrection = correction),
            )
        }
        database.captureDao().insert(original.toEntity())
        setContent(corrector = corrector)
        openMemoria()
        openEditor()

        val exactCorrection = "  Comprar leche mañana  "
        composeRule.onNode(hasSetTextAction()).performTextReplacement(exactCorrection)
        composeRule.onNodeWithText("Guardar").performClick()

        waitForText("Corrección guardada")
        composeRule.onNodeWithText(exactCorrection).assertIsDisplayed()
        assertEquals(listOf(exactCorrection), corrector.calls.map { it.second })
        composeRule.onNodeWithText("Editar transcript").assertDoesNotExist()
    }

    @Test
    fun blankCorrectionCannotBeSaved() = runBlocking {
        val original = capture(
            id = "voice-blank-input",
            input = CaptureInput.Voice("Texto original"),
        )
        val corrector = RecordingCorrector { _, _ ->
            error("Blank input must not reach the corrector")
        }
        database.captureDao().insert(original.toEntity())
        setContent(corrector = corrector)
        openMemoria()
        openEditor()

        composeRule.onNode(hasSetTextAction()).performTextReplacement(" \t\n ")

        composeRule.onNodeWithText("Guardar").assertIsNotEnabled()
        assertTrue(corrector.calls.isEmpty())
    }

    @Test
    fun unchangedEffectiveTranscriptDoesNotCallCorrector() = runBlocking {
        val original = capture(
            id = "voice-no-op",
            input = CaptureInput.Voice("Sin cambios"),
        )
        val corrector = RecordingCorrector { _, _ ->
            error("Unchanged input must not reach the corrector")
        }
        database.captureDao().insert(original.toEntity())
        setContent(corrector = corrector)
        openMemoria()
        openEditor()

        composeRule.onNodeWithText("Guardar").assertIsNotEnabled()
        assertTrue(corrector.calls.isEmpty())
    }

    @Test
    fun cancelClosesEditorWithoutMutation() = runBlocking {
        val original = capture(
            id = "voice-cancel",
            input = CaptureInput.Voice("Texto original"),
        )
        val corrector = RecordingCorrector { _, _ ->
            error("Cancel must not reach the corrector")
        }
        database.captureDao().insert(original.toEntity())
        setContent(corrector = corrector)
        openMemoria()
        openEditor()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("Texto editado")
        composeRule.onNodeWithText("Cancelar").performClick()

        waitForEditorToClose()
        assertTrue(corrector.calls.isEmpty())
        assertEquals(original.toEntity(), database.captureDao().getById(original.id.value))
    }

    @Test
    fun backDismissalClosesEditorWithoutMutation() = runBlocking {
        val original = capture(
            id = "voice-back",
            input = CaptureInput.Voice("Texto original"),
        )
        val corrector = RecordingCorrector { _, _ ->
            error("Back dismissal must not reach the corrector")
        }
        database.captureDao().insert(original.toEntity())
        setContent(corrector = corrector)
        openMemoria()
        openEditor()

        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        waitForEditorToClose()
        assertTrue(corrector.calls.isEmpty())
        assertEquals(original.toEntity(), database.captureDao().getById(original.id.value))
    }

    @Test
    fun persistenceFailureKeepsEditorAndEnteredTextAndShowsError() = runBlocking {
        val original = capture(
            id = "voice-failure",
            input = CaptureInput.Voice("Texto original"),
        )
        val enteredText = "Texto que no se pudo guardar"
        val corrector = RecordingCorrector { _, _ ->
            CaptureTranscriptCorrectionResult.Failed(
                IllegalStateException("database unavailable"),
            )
        }
        database.captureDao().insert(original.toEntity())
        setContent(corrector = corrector)
        openMemoria()
        openEditor()
        composeRule.onNode(hasSetTextAction()).performTextReplacement(enteredText)

        composeRule.onNodeWithText("Guardar").performClick()

        waitForText("No se pudo guardar la corrección.")
        composeRule.onNodeWithText("Editar transcript").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertTextContains(enteredText, substring = false)
        composeRule.onNodeWithText("Corrección guardada").assertDoesNotExist()
    }

    @Test
    fun missingCorrectionIsNotReportedAsSuccess() = runBlocking {
        openVoiceWithCorrectorResult(CaptureTranscriptCorrectionResult.Missing)

        composeRule.onNodeWithText("Guardar").performClick()

        waitForText("No se encontró la captura.")
        composeRule.onNodeWithText("Editar transcript").assertIsDisplayed()
        composeRule.onNodeWithText("Corrección guardada").assertDoesNotExist()
    }

    @Test
    fun notVoiceCorrectionIsNotReportedAsSuccess() = runBlocking {
        openVoiceWithCorrectorResult(CaptureTranscriptCorrectionResult.NotVoice)

        composeRule.onNodeWithText("Guardar").performClick()

        waitForText("Solo las capturas de voz se pueden editar.")
        composeRule.onNodeWithText("Editar transcript").assertIsDisplayed()
        composeRule.onNodeWithText("Corrección guardada").assertDoesNotExist()
    }

    @Test
    fun realRoomCorrectionUpdatesMemoriaImmediatelyAndPreservesOriginal() = runBlocking {
        val original = capture(
            id = "voice-room-success",
            input = CaptureInput.Voice("comprar leche manana"),
        )
        database.captureDao().insert(original.toEntity())
        setContent(corrector = RoomCaptureTranscriptCorrector(database.captureDao()))
        openMemoria()
        openEditor()

        val correction = "Comprar leche mañana"
        composeRule.onNode(hasSetTextAction()).performTextReplacement(correction)
        composeRule.onNodeWithText("Guardar").performClick()

        waitForText("Corrección guardada")
        composeRule.onNodeWithText(correction).assertIsDisplayed()
        composeRule.onNodeWithText("Editar transcript").assertDoesNotExist()
        val stored = checkNotNull(database.captureDao().getById(original.id.value))
        assertEquals(original.originalInput, stored.toDomain().originalInput)
        assertEquals("comprar leche manana", stored.originalText)
        assertEquals(correction, stored.correctedTranscript)
    }

    @Test
    fun secondCorrectionUpdatesEffectiveTranscriptAgainAndKeepsOriginal() = runBlocking {
        val original = capture(
            id = "voice-second-correction",
            input = CaptureInput.Voice("texto original"),
        )
        database.captureDao().insert(original.toEntity())
        setContent(corrector = RoomCaptureTranscriptCorrector(database.captureDao()))
        openMemoria()
        openEditor()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("Primera corrección")
        composeRule.onNodeWithText("Guardar").performClick()
        waitForText("Primera corrección")

        openEditor()
        composeRule.onNode(hasSetTextAction()).assertTextContains("Primera corrección", substring = false)
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Segunda corrección")
        composeRule.onNodeWithText("Guardar").performClick()

        waitForText("Segunda corrección")
        val stored = checkNotNull(database.captureDao().getById(original.id.value))
        assertEquals("texto original", stored.originalText)
        assertEquals("Segunda corrección", stored.correctedTranscript)
    }

    private fun openVoiceWithCorrectorResult(
        result: CaptureTranscriptCorrectionResult,
    ) {
        val original = capture(
            id = "voice-result-${UUID.randomUUID()}",
            input = CaptureInput.Voice("Texto para resultado"),
        )
        val corrector = RecordingCorrector { _, _ -> result }
        runBlocking { database.captureDao().insert(original.toEntity()) }
        setContent(corrector = corrector)
        openMemoria()
        openEditor()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Texto nuevo")
    }

    private fun setContent(corrector: CaptureTranscriptCorrector) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = CaptureSubmission(CaptureWriter { }),
                        captureReader = RoomCaptureReader(database),
                        captureTranscriptCorrector = corrector,
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

    private fun openEditor() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("Editar transcript").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithContentDescription("Editar transcript").performClick()
        waitForText("Editar transcript")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun waitForEditorToClose() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Editar transcript").assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
    }

    private fun capture(
        id: String,
        input: CaptureInput,
        correction: String? = null,
    ): Capture = Capture(
        id = CaptureId(id),
        originalInput = input,
        capturedAt = Instant.parse("2026-09-03T16:00:00Z"),
        transcriptCorrection = correction,
    )

    private class RecordingCorrector(
        private val resultProvider: (CaptureId, String) -> CaptureTranscriptCorrectionResult,
    ) : CaptureTranscriptCorrector {
        val calls = mutableListOf<Pair<CaptureId, String>>()

        override suspend fun correct(
            captureId: CaptureId,
            correctedTranscript: String,
        ): CaptureTranscriptCorrectionResult {
            calls += captureId to correctedTranscript
            return resultProvider(captureId, correctedTranscript)
        }
    }
}

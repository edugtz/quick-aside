package com.edu.quickaside

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.speech.SpeechTranscriberError
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.data.local.QuickAsideDatabase
import com.edu.quickaside.data.local.RoomCaptureReader
import com.edu.quickaside.data.local.RoomCaptureWriter
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
class VoiceCaptureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "voice-capture-ui-test-${UUID.randomUUID()}.db"
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun microphoneActionEntersVoiceCaptureAndStartsFakeTranscriber() {
        val factory = FakeSpeechTranscriberFactory()
        setContent(factory = factory)

        composeRule.onNodeWithContentDescription("Hablar").performClick()

        waitForText("Listo para escuchar…")
        composeRule.onNodeWithText("Captura").assertIsDisplayed()
        composeRule.onNodeWithText("Escuchando…").assertDoesNotExist()
        assertEquals(1, factory.latest().startCount)
    }

    @Test
    fun permissionDeniedCreatesNoCaptureAndShowsExitState() {
        val factory = FakeSpeechTranscriberFactory()
        val permission = FakeMicrophonePermissionController(
            granted = false,
            requestResult = false,
            rationale = false,
        )
        setContent(factory = factory, permission = permission)

        composeRule.onNodeWithContentDescription("Hablar").performClick()

        waitForText("El micrófono está bloqueado")
        composeRule.onNodeWithText("Concede el permiso desde Ajustes y vuelve a intentarlo.")
            .assertIsDisplayed()
        assertEquals(1, permission.requestCount)
        assertTrue(factory.transcribers.isEmpty())
        assertTrue(readRecent().isEmpty())
    }

    @Test
    fun suppliedPartialTranscriptRendersAsSecondaryText() {
        val factory = FakeSpeechTranscriberFactory()
        setContent(factory = factory)
        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")

        factory.latest().emitPartial("Comprar leche")

        waitForText("Comprar leche")
        composeRule.onNodeWithText("Escuchando…").assertIsDisplayed()
    }

    @Test
    fun noPartialTranscriptIsStillAValidListeningState() {
        val factory = FakeSpeechTranscriberFactory()
        setContent(factory = factory)
        composeRule.onNodeWithContentDescription("Hablar").performClick()

        waitForText("Listo para escuchar…")
        composeRule.onNodeWithText("Habla cuando quieras").assertIsDisplayed()
        assertTrue(readRecent().isEmpty())
    }

    @Test
    fun finalTranscriptCreatesExactlyOneVoiceCaptureAndPreservesItExactly() = runBlocking {
        val captureId = CaptureId("voice-ui-final")
        val capturedAt = Instant.parse("2026-09-03T17:00:00Z")
        val submission = CaptureSubmission(
            writer = RoomCaptureWriter(database),
            idProvider = { captureId },
            capturedAtProvider = { capturedAt },
        )
        val factory = FakeSpeechTranscriberFactory()
        setContent(submission = submission, factory = factory)
        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")

        val finalTranscript = "  Comprar leche mañana  "
        factory.latest().emitFinal(finalTranscript)
        factory.latest().emitFinal("No debe crear una segunda captura")

        waitForText("Captura guardada")
        composeRule.onNodeWithContentDescription("Capturar").assertIsDisplayed()
        val captures = RoomCaptureReader(database).readRecent()
        assertEquals(1, captures.size)
        assertEquals(
            CaptureInput.Voice(finalTranscript),
            captures.single().originalInput,
        )
        assertEquals(captureId, captures.single().id)
        assertEquals(capturedAt, captures.single().capturedAt)
    }

    @Test
    fun blankFinalResultCreatesNoCaptureAndOffersRetry() {
        val factory = FakeSpeechTranscriberFactory()
        setContent(factory = factory)
        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")

        factory.latest().emitFinal(" \t\n ")

        waitForText("No escuché una frase. Intenta de nuevo.")
        assertTrue(readRecent().isEmpty())
        composeRule.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    @Test
    fun recognizerErrorCreatesNoCaptureAndShowsUnderstandableMessage() {
        val factory = FakeSpeechTranscriberFactory()
        setContent(factory = factory)
        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")

        factory.latest().emitError(SpeechTranscriberError.NetworkFailure)

        waitForText("No se pudo conectar con el servicio de voz.")
        assertTrue(readRecent().isEmpty())
        composeRule.onNodeWithText("100").assertDoesNotExist()
    }

    @Test
    fun cancellationCreatesNoCaptureAndCleansUpRecognizer() {
        val factory = FakeSpeechTranscriberFactory()
        setContent(factory = factory)
        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")

        composeRule.onNodeWithText("Cancelar").performClick()

        composeRule.onNodeWithContentDescription("Capturar").assertIsDisplayed()
        composeRule.onNodeWithText("Captura").assertDoesNotExist()
        assertEquals(1, factory.latest().cancelCount)
        assertEquals(1, factory.latest().destroyCount)
        assertTrue(readRecent().isEmpty())
    }

    @Test
    fun successfulVoiceCaptureClosesSurfaceShowsReceiptAndIsVisibleInMemoria() = runBlocking {
        val captureId = CaptureId("voice-ui-memoria")
        val submission = CaptureSubmission(
            writer = RoomCaptureWriter(database),
            idProvider = { captureId },
            capturedAtProvider = { Instant.parse("2026-09-03T17:10:00Z") },
        )
        val factory = FakeSpeechTranscriberFactory()
        setContent(submission = submission, factory = factory)
        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")

        factory.latest().emitFinal("Mañana revisa el PR")

        waitForText("Captura guardada")
        composeRule.onNode(hasText("Memoria") and hasClickAction()).performClick()
        waitForText("Mañana revisa el PR")
        composeRule.onNodeWithText("Voz ·", substring = true).assertIsDisplayed()
        assertEquals(1, RoomCaptureReader(database).readRecent().size)
    }

    @Test
    fun disposingCaptureSurfaceCancelsAndDestroysActiveRecognizer() {
        val factory = FakeSpeechTranscriberFactory()
        setContent(factory = factory)
        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")
        val transcriber = factory.latest()

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent { Text("Contenido reemplazado") }
        }
        composeRule.waitForIdle()

        assertEquals(1, transcriber.cancelCount)
        assertEquals(1, transcriber.destroyCount)
    }

    private fun setContent(
        submission: CaptureSubmission = CaptureSubmission(CaptureWriter { }),
        factory: FakeSpeechTranscriberFactory,
        permission: FakeMicrophonePermissionController =
            FakeMicrophonePermissionController(granted = true),
    ) {
        val reader = RoomCaptureReader(database)
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = submission,
                        captureReader = reader,
                        speechTranscriberFactory = factory,
                        microphonePermissionController = permission,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun readRecent() = runBlocking {
        RoomCaptureReader(database).readRecent()
    }
}

package com.edu.quickaside

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.data.local.QuickAsideDatabase
import com.edu.quickaside.data.local.RoomCaptureReader
import com.edu.quickaside.data.local.RoomCaptureWriter
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureHistoryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "capture-history-ui-test-${UUID.randomUUID()}.db"
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun memoriaShowsEmptyStateWhenNoCapturesExist() {
        setContent()
        openMemoria()

        waitForText("Aún no hay capturas")
        composeRule.onNodeWithText("Capturas recientes").assertIsDisplayed()
        composeRule.onNodeWithText("Las capturas que guardes desde Inicio aparecerán aquí.")
            .assertIsDisplayed()
    }

    @Test
    fun memoriaShowsOnePersistedTextCaptureWithOriginalContentAndKind() = runBlocking {
        val capture = capture(
            id = "one-text",
            originalInput = CaptureInput.Text("Compra pollo"),
        )
        database.captureDao().insert(capture.toEntity())
        setContent()
        openMemoria()

        waitForText("Compra pollo")
        composeRule.onNodeWithText("Texto ·", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Aún no hay capturas").assertDoesNotExist()
    }

    @Test
    fun memoriaShowsMultipleRecentCapturesIncludingExistingVoiceTranscript() {
        runBlocking {
            database.captureDao().insert(
                capture(
                    id = "older-text",
                    capturedAt = Instant.parse("2026-09-02T12:00:00Z"),
                    originalInput = CaptureInput.Text("Captura más antigua"),
                ).toEntity(),
            )
            database.captureDao().insert(
                capture(
                    id = "newer-voice",
                    capturedAt = Instant.parse("2026-09-03T12:00:00Z"),
                    originalInput = CaptureInput.Voice("Mañana revisa el PR"),
                ).toEntity(),
            )
            setContent()
            openMemoria()

            waitForText("Mañana revisa el PR")
            composeRule.onNodeWithText("Captura más antigua").assertIsDisplayed()
            composeRule.onNodeWithText("Voz ·", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Texto ·", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun captureSavedFromInicioIsVisibleInMemoriaWithoutRestart() = runBlocking {
        val captureId = CaptureId("from-inicio")
        val captureTime = Instant.parse("2026-09-03T16:30:00Z")
        val submission = CaptureSubmission(
            writer = RoomCaptureWriter(database),
            idProvider = { captureId },
            capturedAtProvider = { captureTime },
        )
        setContent(submission)

        composeRule.onNode(hasSetTextAction()).performTextInput("Guardada desde Inicio")
        composeRule.onNodeWithContentDescription("Enviar captura").performClick()
        waitForText("Captura guardada")

        openMemoria()

        waitForText("Guardada desde Inicio")
        assertEquals(
            Capture(
                id = captureId,
                originalInput = CaptureInput.Text("Guardada desde Inicio"),
                capturedAt = captureTime,
            ),
            database.captureDao().getById(captureId.value)?.toDomain(),
        )
    }

    private fun setContent(
        submission: CaptureSubmission = CaptureSubmission(CaptureWriter { }),
    ) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = submission,
                        captureReader = RoomCaptureReader(database),
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

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun capture(
        id: String,
        capturedAt: Instant = Instant.parse("2026-09-03T12:00:00Z"),
        originalInput: CaptureInput,
    ): Capture = Capture(
        id = CaptureId(id),
        originalInput = originalInput,
        capturedAt = capturedAt,
    )
}

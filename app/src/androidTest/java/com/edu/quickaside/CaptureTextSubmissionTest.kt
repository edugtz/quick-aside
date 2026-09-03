package com.edu.quickaside

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.data.local.toDomain
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureTextSubmissionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun enteredTextShowsReceiptClearsFieldAndUsesProductionRoomWiring() = runBlocking {
        val application = composeRule.activity.application as QuickAsideApplication
        val captureId = CaptureId("ui-production-${UUID.randomUUID()}")
        val capturedAt = Instant.parse("2026-09-03T16:30:00Z")
        val submission = CaptureSubmission(
            writer = application.captureWriter,
            idProvider = { captureId },
            capturedAtProvider = { capturedAt },
        )
        setContent(submission)

        val input = "Compra pollo"
        composeRule.onNode(hasSetTextAction()).performTextInput(input)
        composeRule.onNodeWithContentDescription("Enviar captura").performClick()
        waitForSnackbar("Captura guardada")

        composeRule.onNodeWithContentDescription("Enviar captura").assertIsDisplayed()
        assertEquals("", editableText())
        assertEquals(
            Capture(
                id = captureId,
                originalInput = CaptureInput.Text(input),
                capturedAt = capturedAt,
            ),
            application.database.captureDao().getById(captureId.value)?.toDomain(),
        )
    }

    @Test
    fun failedPersistenceKeepsEnteredTextAndShowsError() {
        val submission = CaptureSubmission(
            writer = CaptureWriter { throw IllegalStateException("database unavailable") },
            idProvider = { CaptureId("ui-failure") },
            capturedAtProvider = { Instant.parse("2026-09-03T16:30:00Z") },
        )
        setContent(submission)

        val input = "No borrar este texto"
        composeRule.onNode(hasSetTextAction()).performTextInput(input)
        composeRule.onNodeWithContentDescription("Enviar captura").performClick()
        waitForSnackbar("No se pudo guardar la captura")

        assertEquals(input, editableText())
        composeRule.onNodeWithContentDescription("Enviar captura").assertIsDisplayed()
    }

    private fun setContent(submission: CaptureSubmission) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(captureSubmission = submission)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitForSnackbar(message: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(message).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun editableText(): String = composeRule
        .onNode(hasSetTextAction())
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text
}

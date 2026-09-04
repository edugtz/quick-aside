package com.edu.quickaside

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.data.local.QuickAsideDatabase
import com.edu.quickaside.data.local.RoomCaptureReader
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
class CaptureCorrectionHistoryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "capture-correction-history-${UUID.randomUUID()}.db"
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun memoriaRendersEffectiveVoiceTranscriptWithoutReplacingOriginalInStorage() = runBlocking {
        val capture = Capture(
            id = CaptureId("corrected-history"),
            originalInput = CaptureInput.Voice("comprar leche manana"),
            capturedAt = Instant.parse("2026-09-03T16:00:00Z"),
            transcriptCorrection = "Comprar leche mañana",
        )
        database.captureDao().insert(capture.toEntity())
        setContent()

        composeRule.onNode(hasText("Memoria") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Comprar leche mañana").assertIsDisplayed()
            }.isSuccess
        }

        composeRule.onNodeWithText("comprar leche manana").assertDoesNotExist()
        val stored = checkNotNull(database.captureDao().getById(capture.id.value))
        assertEquals("comprar leche manana", stored.originalText)
        assertEquals("Comprar leche mañana", stored.correctedTranscript)
    }

    private fun setContent() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = CaptureSubmission(CaptureWriter { }),
                        captureReader = RoomCaptureReader(database),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}

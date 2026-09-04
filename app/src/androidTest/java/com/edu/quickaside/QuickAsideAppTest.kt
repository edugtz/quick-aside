package com.edu.quickaside

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.data.local.QuickAsideDatabase
import com.edu.quickaside.data.local.RoomCaptureReader
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.util.UUID
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAsideAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "quick-aside-shell-ui-test-${UUID.randomUUID()}.db"
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun inicioMicrophoneOpensCaptureWithoutAddingNavigationDestination() {
        val permission = FakeMicrophonePermissionController(granted = true)
        val factory = FakeSpeechTranscriberFactory()
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = CaptureSubmission(CaptureWriter { }),
                        captureReader = RoomCaptureReader(database),
                        speechTranscriberFactory = factory,
                        microphonePermissionController = permission,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Inicio").assertCountEquals(2)
        listOf("Pendientes", "Listas", "Memoria").forEach { label ->
            composeRule.onAllNodesWithText(label).assertCountEquals(1)
        }

        composeRule.onNodeWithContentDescription("Capturar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hablar").assertIsDisplayed()
        composeRule.onNodeWithText("Captura").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Hablar").performClick()

        composeRule.onNodeWithText("Captura").assertIsDisplayed()
        composeRule.onNodeWithText("Listo para escuchar…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Capturar").assertDoesNotExist()
        composeRule.onNodeWithText("Inicio").assertDoesNotExist()
    }
}

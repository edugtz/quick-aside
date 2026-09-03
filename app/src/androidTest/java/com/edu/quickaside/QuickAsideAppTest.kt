package com.edu.quickaside

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAsideAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun inicioMicrophoneOpensCaptureWithoutAddingNavigationDestination() {
        composeRule.onAllNodesWithText("Inicio").assertCountEquals(2)
        listOf("Pendientes", "Listas", "Memoria").forEach { label ->
            composeRule.onAllNodesWithText(label).assertCountEquals(1)
        }

        composeRule.onNodeWithContentDescription("Capturar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hablar").assertIsDisplayed()
        composeRule.onNodeWithText("Captura").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Hablar").performClick()

        composeRule.onNodeWithText("Capturar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Capturar").assertDoesNotExist()
        composeRule.onAllNodesWithText("Inicio").assertCountEquals(2)
        listOf("Pendientes", "Listas", "Memoria").forEach { label ->
            composeRule.onAllNodesWithText(label).assertCountEquals(1)
        }
    }
}

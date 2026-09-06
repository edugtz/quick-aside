package com.edu.quickaside

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.ListStore
import com.edu.quickaside.application.lists.ListSessionWithItems
import com.edu.quickaside.application.lists.SessionFinishResult
import com.edu.quickaside.application.lists.SessionStartResult
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListDefinition
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComprasUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var store: FakeComprasListStore

    @Before
    fun setUp() {
        store = FakeComprasListStore()
    }

    @Test
    fun openingComprasLoadsContinuousItemsWithoutStartingOrCreatingASession() {
        store.items = listOf(item("milk", "Leche"))
        setContent(store)
        openCompras()

        waitForText("Leche")
        assertEquals(listOf(BuiltInListDefinitions.COMPRAS.id), store.readCurrentItemsCalls)
        assertTrue(store.startCalls.isEmpty())
        assertTrue(store.activeSessionCalls.isEmpty())
        composeRule.onNodeWithText("Siempre disponible para agregar lo que necesitas.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Iniciar compras", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Terminar compras", substring = true).assertDoesNotExist()
    }

    @Test
    fun emptyComprasRendersAValidEmptyState() {
        setContent(store)
        openCompras()

        waitForText("Aún no hay productos.")
        composeRule.onNodeWithText("Agrega algo que necesites comprar.").assertIsDisplayed()
    }

    @Test
    fun exactTextReachesStoreWithNullSessionAndSavedItemAppearsImmediately() {
        setContent(store)
        openCompras()

        waitForText("Aún no hay productos.")
        val exactText = "  Cuerdas guitarra  "
        composeRule.onNode(hasSetTextAction()).performTextInput(exactText)
        composeRule.onNodeWithContentDescription("Agregar producto").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { store.addedTexts.size == 1 }
        assertEquals(listOf(exactText), store.addedTexts)
        assertEquals(listOf(null), store.addedSessionIds)
        waitForText("Cuerdas guitarra")
        assertEquals("", editableText())
    }

    @Test
    fun blankTextCannotBeSubmitted() {
        setContent(store)
        openCompras()

        waitForText("Aún no hay productos.")
        composeRule.onNode(hasSetTextAction()).performTextInput(" \t\n ")
        composeRule.onNodeWithContentDescription("Agregar producto").assertIsNotEnabled()
        assertTrue(store.addedTexts.isEmpty())
    }

    @Test
    fun addFailureRetainsInputAndShowsConciseError() {
        store.addResult = AddListItemResult.Failed(IllegalStateException("unavailable"))
        setContent(store)
        openCompras()

        waitForText("Aún no hay productos.")
        val entered = "No borrar este producto"
        composeRule.onNode(hasSetTextAction()).performTextInput(entered)
        composeRule.onNodeWithContentDescription("Agregar producto").performClick()

        waitForText("No se pudo agregar el producto.")
        assertEquals(entered, editableText())
    }

    @Test
    fun successfulCompletionUsesIdUpdatesOnlyMatchingItemAndKeepsRowsVisible() {
        val milk = item("milk", "Leche")
        val bread = item("bread", "Pan", completed = true)
        store.items = listOf(milk, bread)
        setContent(store)
        openCompras()

        waitForText("Leche")
        composeRule.onNodeWithContentDescription("Marcar Leche como completado").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { store.completionCalls.size == 1 }
        assertEquals(listOf(milk.id to true), store.completionCalls)
        composeRule.onNodeWithText("Leche").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Desmarcar Leche").assertIsDisplayed()
        composeRule.onNodeWithText("Pan").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Desmarcar Pan").assertIsDisplayed()
    }

    @Test
    fun completionFailureRetainsVisibleStateAndShowsConciseError() {
        store.items = listOf(item("milk", "Leche"))
        store.completionResult = ItemCompletionResult.Failed(IllegalStateException("unavailable"))
        setContent(store)
        openCompras()

        waitForText("Leche")
        composeRule.onNodeWithContentDescription("Marcar Leche como completado").performClick()

        waitForText("No se pudo actualizar el producto.")
        composeRule.onNodeWithContentDescription("Marcar Leche como completado").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Desmarcar Leche").assertDoesNotExist()
    }

    @Test
    fun androidBackFromComprasReturnsToListasRoot() {
        setContent(store)
        openCompras()
        waitForText("Aún no hay productos.")

        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        waitForText("Gestiona tus listas")
        composeRule.onNodeWithContentDescription("Abrir Compras").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationSwitchResetsNestedListRoute() {
        setContent(store)
        openCompras()
        waitForText("Aún no hay productos.")

        composeRule.onNode(hasText("Pendientes") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Listas") and hasClickAction()).performClick()

        waitForText("Gestiona tus listas")
        composeRule.onNodeWithContentDescription("Abrir Compras").assertIsDisplayed()
        composeRule.onNodeWithText("Tu lista de compras").assertDoesNotExist()
    }

    private fun setContent(listStore: ListStore) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = CaptureSubmission(CaptureWriter { }),
                        captureReader = CaptureReader { emptyList() },
                        listStore = listStore,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun openCompras() {
        composeRule.onNode(hasText("Listas") and hasClickAction()).performClick()
        waitForText("Gestiona tus listas")
        composeRule.onNodeWithContentDescription("Abrir Compras").performClick()
        waitForText("Tu lista de compras")
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

    private fun item(id: String, text: String, completed: Boolean = false): ListItem = ListItem(
        id = ListItemId(id),
        listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
        text = text,
        listSessionId = null,
        isCompleted = completed,
        createdAt = Instant.parse("2026-09-05T10:00:00Z"),
    )
}

private class FakeComprasListStore : ListStore {
    val readCurrentItemsCalls = mutableListOf<ListDefinitionId>()
    val activeSessionCalls = mutableListOf<ListDefinitionId>()
    val startCalls = mutableListOf<ListDefinitionId>()
    val addedTexts = mutableListOf<String>()
    val addedSessionIds = mutableListOf<ListSessionId?>()
    val completionCalls = mutableListOf<Pair<ListItemId, Boolean>>()
    var items: List<ListItem> = emptyList()
    var addResult: AddListItemResult? = null
    var completionResult: ItemCompletionResult? = null

    override suspend fun readBuiltInDefinitions(): List<ListDefinition> =
        BuiltInListDefinitions.ALL

    override suspend fun getActiveSession(listDefinitionId: ListDefinitionId): ListSession? {
        activeSessionCalls += listDefinitionId
        return null
    }

    override suspend fun startSession(listDefinitionId: ListDefinitionId): SessionStartResult {
        startCalls += listDefinitionId
        return SessionStartResult.NotSessionBased
    }

    override suspend fun finishActiveSession(listDefinitionId: ListDefinitionId): SessionFinishResult =
        SessionFinishResult.NotSessionBased

    override suspend fun finishSession(listSessionId: ListSessionId): SessionFinishResult =
        SessionFinishResult.MissingSession

    override suspend fun readSession(listSessionId: ListSessionId): ListSessionWithItems? = null

    override suspend fun readRecentSessions(
        listDefinitionId: ListDefinitionId,
    ): List<ListSessionWithItems> = emptyList()

    override suspend fun readCurrentItems(listDefinitionId: ListDefinitionId): List<ListItem> {
        readCurrentItemsCalls += listDefinitionId
        return items
    }

    override suspend fun addItem(
        listDefinitionId: ListDefinitionId,
        text: String,
        listSessionId: ListSessionId?,
    ): AddListItemResult {
        addedTexts += text
        addedSessionIds += listSessionId
        addResult?.let { return it }
        val saved = ListItem(
            id = ListItemId("item-${addedTexts.size}"),
            listDefinitionId = listDefinitionId,
            text = text,
            listSessionId = listSessionId,
            createdAt = Instant.parse("2026-09-05T10:01:00Z"),
        )
        items = items + saved
        return AddListItemResult.Saved(saved)
    }

    override suspend fun setItemCompleted(
        listItemId: ListItemId,
        isCompleted: Boolean,
    ): ItemCompletionResult {
        completionCalls += listItemId to isCompleted
        completionResult?.let { return it }
        val existing = items.single { it.id == listItemId }
        val updated = existing.copy(isCompleted = isCompleted)
        items = items.map { if (it.id == listItemId) updated else it }
        return ItemCompletionResult.Updated(updated)
    }

    override suspend fun toggleItemCompleted(listItemId: ListItemId): ItemCompletionResult =
        ItemCompletionResult.Missing
}

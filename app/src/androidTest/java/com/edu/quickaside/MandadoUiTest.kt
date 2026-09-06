package com.edu.quickaside

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
class MandadoUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var store: FakeMandadoListStore

    @Before
    fun setUp() {
        store = FakeMandadoListStore()
    }

    @Test
    fun listasExposesInteractiveMandadoAndComprasWithoutAFifthDestination() {
        setContent(store)
        openLists()

        composeRule.onNodeWithText("Mandado").assertIsDisplayed()
        composeRule.onNodeWithText("Compras").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Abrir Mandado").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Abrir Compras").assertIsDisplayed()
        composeRule.onAllNodesWithText("Inicio").assertCountEquals(1)
        composeRule.onAllNodesWithText("Pendientes").assertCountEquals(1)
        composeRule.onAllNodesWithText("Listas").assertCountEquals(2)
        composeRule.onAllNodesWithText("Memoria").assertCountEquals(1)
        composeRule.onNodeWithText("Captura").assertDoesNotExist()
    }

    @Test
    fun openingMandadoShowsEmptyStateWithoutStartingSession() {
        setContent(store)
        openMandado()

        waitForText("No hay un mandado activo.")
        assertTrue(store.startCalls.isEmpty())
        composeRule.onNodeWithContentDescription("Iniciar mandado").assertIsDisplayed()
    }

    @Test
    fun startingMandadoHandlesCreatedAndDisplaysActiveEmptyState() {
        setContent(store)
        openMandado()

        composeRule.onNodeWithContentDescription("Iniciar mandado").performClick()

        waitForText("Aún no hay productos.")
        assertEquals(listOf(BuiltInListDefinitions.MANDADO.id), store.startCalls)
    }

    @Test
    fun existingSessionIsReusedAndStartFailureIsNotShownAsSuccess() {
        val existing = session("existing-session")
        store.activeSession = null
        store.startResult = SessionStartResult.Existing(existing)
        setContent(store)
        openMandado()

        waitForText("No hay un mandado activo.")
        composeRule.onNodeWithContentDescription("Iniciar mandado").performClick()
        waitForText("Aún no hay productos.")
        assertEquals(listOf(BuiltInListDefinitions.MANDADO.id), store.startCalls)
        assertEquals(0, store.createdSessionCount)
        composeRule.onNodeWithContentDescription("Volver a Listas").performClick()
        composeRule.onNodeWithContentDescription("Abrir Mandado").performClick()
        waitForText("Aún no hay productos.")

        store.activeSession = null
        store.startResult = SessionStartResult.MissingDefinition
        composeRule.onNodeWithContentDescription("Volver a Listas").performClick()
        composeRule.onNodeWithContentDescription("Abrir Mandado").performClick()
        waitForText("No hay un mandado activo.")
        composeRule.onNodeWithContentDescription("Iniciar mandado").performClick()
        waitForText("No se pudo iniciar el mandado.")
        composeRule.onNodeWithText("Aún no hay productos.").assertDoesNotExist()
    }

    @Test
    fun exactItemTextReachesStoreAndSavedItemClearsInput() {
        store.activeSession = session("active-session")
        setContent(store)
        openMandado()
        waitForText("Aún no hay productos.")

        val exactText = "  Chobani  "
        composeRule.onNode(hasSetTextAction()).performTextInput(exactText)
        composeRule.onNodeWithContentDescription("Agregar producto").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { store.addedTexts.size == 1 }
        assertEquals(listOf(exactText), store.addedTexts)
        assertEquals("", editableText())
    }

    @Test
    fun blankItemTextCannotBeSubmitted() {
        store.activeSession = session("active-session")
        setContent(store)
        openMandado()
        waitForText("Aún no hay productos.")

        composeRule.onNode(hasSetTextAction()).performTextInput(" \t\n ")
        composeRule.onNodeWithContentDescription("Agregar producto").assertIsNotEnabled()
        assertTrue(store.addedTexts.isEmpty())
    }

    @Test
    fun addFailureRetainsInputAndShowsError() {
        store.activeSession = session("active-session")
        store.addResult = AddListItemResult.Failed(IllegalStateException("unavailable"))
        setContent(store)
        openMandado()
        waitForText("Aún no hay productos.")

        val entered = "No borrar este producto"
        composeRule.onNode(hasSetTextAction()).performTextInput(entered)
        composeRule.onNodeWithContentDescription("Agregar producto").performClick()

        waitForText("No se pudo agregar el producto.")
        assertEquals(entered, editableText())
    }

    @Test
    fun completionUsesItemIdAndSuccessfulUpdateLeavesCheckedItemVisible() {
        store.activeSession = session("active-session")
        val milk = item("milk", "Leche")
        val bread = item("bread", "Pan")
        store.items = listOf(milk, bread)
        setContent(store)
        openMandado()
        waitForText("Leche")

        composeRule.onNodeWithContentDescription("Marcar Leche como completado").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { store.completionCalls.size == 1 }
        assertEquals(milk.id to true, store.completionCalls.single())
        composeRule.onNodeWithText("Leche").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Desmarcar Leche").assertIsDisplayed()
        composeRule.onNodeWithText("Pan").assertIsDisplayed()
    }

    @Test
    fun completionFailureRetainsVisibleStateAndShowsError() {
        store.activeSession = session("active-session")
        store.items = listOf(item("milk", "Leche"))
        store.completionResult = ItemCompletionResult.Failed(IllegalStateException("unavailable"))
        setContent(store)
        openMandado()
        waitForText("Leche")

        composeRule.onNodeWithContentDescription("Marcar Leche como completado").performClick()

        waitForText("No se pudo actualizar el producto.")
        composeRule.onNodeWithContentDescription("Marcar Leche como completado").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Desmarcar Leche").assertDoesNotExist()
    }

    @Test
    fun finishingRequiresConfirmationCancellationDoesNotFinishAndSuccessReturnsToEmptyState() {
        store.activeSession = session("active-session")
        setContent(store)
        openMandado()
        waitForText("Aún no hay productos.")

        composeRule.onNodeWithContentDescription("Terminar mandado").performClick()
        composeRule.onNodeWithText("¿Terminar este mandado?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.onNodeWithText("¿Terminar este mandado?").assertDoesNotExist()
        assertTrue(store.finishCalls.isEmpty())

        composeRule.onNodeWithContentDescription("Terminar mandado").performClick()
        composeRule.onNodeWithText("Terminar").performClick()

        waitForText("Mandado terminado")
        waitForText("No hay un mandado activo.")
        assertEquals(listOf(BuiltInListDefinitions.MANDADO.id), store.finishCalls)
    }

    @Test
    fun backFromMandadoReturnsToListasRoot() {
        setContent(store)
        openMandado()
        waitForText("No hay un mandado activo.")

        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        waitForText("Gestiona tus listas")
        composeRule.onNodeWithContentDescription("Abrir Mandado").assertIsDisplayed()
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

    private fun openLists() {
        composeRule.onNode(hasText("Listas") and hasClickAction()).performClick()
        waitForText("Gestiona tus listas")
    }

    private fun openMandado() {
        openLists()
        composeRule.onNodeWithContentDescription("Abrir Mandado").performClick()
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

    private fun session(id: String): ListSession = ListSession(
        id = ListSessionId(id),
        listDefinitionId = BuiltInListDefinitions.MANDADO.id,
        startedAt = Instant.parse("2026-09-05T10:00:00Z"),
    )

    private fun item(id: String, text: String, completed: Boolean = false): ListItem = ListItem(
        id = ListItemId(id),
        listDefinitionId = BuiltInListDefinitions.MANDADO.id,
        text = text,
        listSessionId = store.activeSession?.id,
        isCompleted = completed,
        createdAt = Instant.parse("2026-09-05T10:00:00Z"),
    )
}

private class FakeMandadoListStore : ListStore {
    val startCalls = mutableListOf<ListDefinitionId>()
    val finishCalls = mutableListOf<ListDefinitionId>()
    val addedTexts = mutableListOf<String>()
    val completionCalls = mutableListOf<Pair<ListItemId, Boolean>>()
    var activeSession: ListSession? = null
    var items: List<ListItem> = emptyList()
    var startResult: SessionStartResult = SessionStartResult.Created(
        ListSession(
            id = ListSessionId("created-session"),
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            startedAt = Instant.parse("2026-09-05T10:00:00Z"),
        ),
    )
    var addResult: AddListItemResult? = null
    var completionResult: ItemCompletionResult? = null
    var finishResult: SessionFinishResult? = null
    var createdSessionCount = 0

    override suspend fun readBuiltInDefinitions(): List<ListDefinition> =
        BuiltInListDefinitions.ALL

    override suspend fun getActiveSession(listDefinitionId: ListDefinitionId): ListSession? =
        activeSession

    override suspend fun startSession(listDefinitionId: ListDefinitionId): SessionStartResult {
        startCalls += listDefinitionId
        val result = startResult
        if (result is SessionStartResult.Created) {
            activeSession = result.session
            createdSessionCount += 1
        }
        if (result is SessionStartResult.Existing) activeSession = result.session
        return result
    }

    override suspend fun finishActiveSession(listDefinitionId: ListDefinitionId): SessionFinishResult {
        finishCalls += listDefinitionId
        val result = finishResult ?: activeSession?.let { session ->
            SessionFinishResult.Finished(
                session.copy(endedAt = Instant.parse("2026-09-05T11:00:00Z")),
            )
        } ?: SessionFinishResult.NoActiveSession
        if (result is SessionFinishResult.Finished) activeSession = null
        return result
    }

    override suspend fun finishSession(listSessionId: ListSessionId): SessionFinishResult =
        SessionFinishResult.MissingSession

    override suspend fun readSession(listSessionId: ListSessionId): ListSessionWithItems? = null

    override suspend fun readRecentSessions(listDefinitionId: ListDefinitionId): List<ListSessionWithItems> =
        emptyList()

    override suspend fun readCurrentItems(listDefinitionId: ListDefinitionId): List<ListItem> = items

    override suspend fun addItem(
        listDefinitionId: ListDefinitionId,
        text: String,
        listSessionId: ListSessionId?,
    ): AddListItemResult {
        addedTexts += text
        val configuredResult = addResult
        if (configuredResult != null) return configuredResult
        val saved = AddListItemResult.Saved(
            ListItem(
                id = ListItemId("item-${addedTexts.size}"),
                listDefinitionId = listDefinitionId,
                text = text,
                listSessionId = listSessionId,
                createdAt = Instant.parse("2026-09-05T10:01:00Z"),
            ),
        )
        items = items + saved.item
        return saved
    }

    override suspend fun setItemCompleted(
        listItemId: ListItemId,
        isCompleted: Boolean,
    ): ItemCompletionResult {
        completionCalls += listItemId to isCompleted
        val configuredResult = completionResult
        if (configuredResult != null) return configuredResult
        val existing = items.single { it.id == listItemId }
        val updated = existing.copy(isCompleted = isCompleted)
        items = items.map { if (it.id == listItemId) updated else it }
        return ItemCompletionResult.Updated(updated)
    }

    override suspend fun toggleItemCompleted(listItemId: ListItemId): ItemCompletionResult =
        ItemCompletionResult.Missing
}

package com.edu.quickaside

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.ListSessionWithItems
import com.edu.quickaside.application.lists.ListStore
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
import com.edu.quickaside.ui.lists.MandadoHistoryTimestampFormatter
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MandadoHistoryUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val timestampFormatter = MandadoHistoryTimestampFormatter(
        zoneId = ZoneId.of("UTC"),
        locale = Locale.ENGLISH,
    )
    private lateinit var store: FakeMandadoHistoryListStore

    @Before
    fun setUp() {
        store = FakeMandadoHistoryListStore()
    }

    @Test
    fun mandadoExposesHistoryAndHistoryIsReachableWithoutAnActiveSession() {
        setContent()
        openMandado()
        saveScreenshot("mandado-history-affordance.png")
        composeRule.onNodeWithContentDescription("Abrir historial de mandados").performClick()
        waitForText("Historial de mandados")

        waitForText("No hay mandados anteriores.")
        assertEquals(listOf(BuiltInListDefinitions.MANDADO.id), store.readRecentCalls)
        assertNoListWrites()
    }

    @Test
    fun historyIsReachableWhileAnActiveMandadoExistsWithoutEndingOrStartingIt() {
        store.activeSession = session(
            id = "active-session",
            startedAt = "2026-09-06T09:00:00Z",
            endedAt = null,
        )
        setContent()
        openMandado()
        waitForText("Aún no hay productos.")

        composeRule.onNodeWithContentDescription("Abrir historial de mandados").performClick()
        waitForText("No hay mandados anteriores.")

        assertTrue(store.startCalls.isEmpty())
        assertTrue(store.finishActiveCalls.isEmpty())
        assertTrue(store.finishSessionCalls.isEmpty())
        assertNoListWrites()
    }

    @Test
    fun historyFiltersActiveSessionsPreservesSuppliedOrderAndShowsDateAndItemCount() {
        val newer = sessionWithItems(
            id = "completed-newer",
            startedAt = "2026-09-05T18:20:00Z",
            endedAt = "2026-09-05T19:00:00Z",
            items = listOf(
                item("newer-milk", "Leche", "completed-newer", completed = true),
                item("newer-bread", "Pan", "completed-newer"),
            ),
        )
        val active = sessionWithItems(
            id = "active-session",
            startedAt = "2026-09-06T09:00:00Z",
            endedAt = null,
            items = listOf(item("active-item", "Sesión activa", "active-session")),
        )
        val older = sessionWithItems(
            id = "completed-older",
            startedAt = "2026-09-04T10:00:00Z",
            endedAt = "2026-09-04T11:00:00Z",
            items = listOf(item("older-eggs", "Huevos", "completed-older")),
        )
        store.history = listOf(newer, active, older)

        setContent()
        openHistory()

        val newerTimestamp = "5 Sep 2026 · 18:20"
        val olderTimestamp = "4 Sep 2026 · 10:00"
        val activeTimestamp = "6 Sep 2026 · 09:00"
        waitForText(newerTimestamp)
        composeRule.onNodeWithText("2 productos").assertIsDisplayed()
        composeRule.onNodeWithText("1 producto").assertIsDisplayed()
        composeRule.onNodeWithText(olderTimestamp).assertIsDisplayed()
        composeRule.onNodeWithText(activeTimestamp).assertDoesNotExist()

        val newerTop = composeRule.onNodeWithText(newerTimestamp)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val olderTop = composeRule.onNodeWithText(olderTimestamp)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue("Supplied newest-first order must be preserved", newerTop < olderTop)
        saveScreenshot("mandado-history-list.png")
        assertNoListWrites()
    }

    @Test
    fun historyLoadFailureShowsRetryAndRetryCanLoadTheHistory() {
        store.history = listOf(
            sessionWithItems(
                id = "completed-session",
                startedAt = "2026-09-05T18:20:00Z",
                endedAt = "2026-09-05T19:00:00Z",
                items = listOf(item("item", "Leche", "completed-session")),
            ),
        )
        store.failHistoryReads = 1

        setContent()
        openHistory()

        waitForText("No se pudo cargar el historial.")
        composeRule.onNodeWithContentDescription("Reintentar carga del historial")
            .assertIsDisplayed()
            .performClick()
        waitForText("5 Sep 2026 · 18:20")
        assertEquals(2, store.readRecentCalls.size)
        assertNoListWrites()
    }

    @Test
    fun selectingHistoryRowShowsAllItemsAndMixedCompletionStateWithoutMutation() {
        val completedSession = sessionWithItems(
            id = "completed-session",
            startedAt = "2026-09-05T18:20:00Z",
            endedAt = "2026-09-05T19:00:00Z",
            items = listOf(
                item("milk", "Leche", "completed-session", completed = true),
                item("bread", "Pan", "completed-session"),
            ),
        )
        store.history = listOf(completedSession)

        setContent()
        openHistory()
        waitForText("5 Sep 2026 · 18:20")

        composeRule.onNodeWithContentDescription(
            "Abrir mandado del 5 Sep 2026 · 18:20, 2 productos",
        ).performClick()
        waitForText("Mandado terminado")
        composeRule.onNodeWithText("Leche").assertIsDisplayed()
        composeRule.onNodeWithText("Pan").assertIsDisplayed()
        composeRule.onNodeWithText("Completado").assertIsDisplayed()
        composeRule.onNodeWithText("Pendiente").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Leche") and hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(hasText("Pan") and hasClickAction()).assertCountEquals(0)
        saveScreenshot("mandado-history-detail.png")
        assertTrue(store.readSessionCalls.isEmpty())
        assertNoListWrites()
    }

    @Test
    fun backFromDetailReturnsToHistoryAndBackFromHistoryReturnsToMandado() {
        store.history = listOf(
            sessionWithItems(
                id = "completed-session",
                startedAt = "2026-09-05T18:20:00Z",
                endedAt = "2026-09-05T19:00:00Z",
                items = listOf(item("milk", "Leche", "completed-session")),
            ),
        )

        setContent()
        openHistory()
        waitForText("5 Sep 2026 · 18:20")
        composeRule.onNodeWithContentDescription(
            "Abrir mandado del 5 Sep 2026 · 18:20, 1 producto",
        ).performClick()
        waitForText("Mandado terminado")

        pressBack()
        waitForText("Historial de mandados")
        composeRule.onNodeWithText("5 Sep 2026 · 18:20").assertIsDisplayed()

        pressBack()
        waitForText("Mandado actual")
        composeRule.onNodeWithContentDescription("Abrir historial de mandados").assertIsDisplayed()
        assertNoListWrites()
    }

    @Test
    fun bottomNavigationSwitchResetsNestedHistoryRoute() {
        store.history = listOf(
            sessionWithItems(
                id = "completed-session",
                startedAt = "2026-09-05T18:20:00Z",
                endedAt = "2026-09-05T19:00:00Z",
                items = listOf(item("milk", "Leche", "completed-session")),
            ),
        )

        setContent()
        openHistory()
        waitForText("5 Sep 2026 · 18:20")

        composeRule.onNode(hasText("Pendientes") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Listas") and hasClickAction()).performClick()

        waitForText("Gestiona tus listas")
        composeRule.onNodeWithText("Historial de mandados").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Abrir Mandado").assertIsDisplayed()
        assertNoListWrites()
    }

    private fun setContent() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = CaptureSubmission(CaptureWriter { }),
                        captureReader = CaptureReader { emptyList() },
                        listStore = store,
                        mandadoHistoryTimestampFormatter = timestampFormatter,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun openMandado() {
        composeRule.onNode(hasText("Listas") and hasClickAction()).performClick()
        waitForText("Gestiona tus listas")
        composeRule.onNodeWithContentDescription("Abrir Mandado").performClick()
        waitForText("Mandado actual")
    }

    private fun openHistory() {
        openMandado()
        composeRule.onNodeWithContentDescription("Abrir historial de mandados").performClick()
        waitForText("Historial de mandados")
    }

    private fun pressBack() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun assertNoListWrites() {
        assertTrue(store.startCalls.isEmpty())
        assertTrue(store.finishActiveCalls.isEmpty())
        assertTrue(store.finishSessionCalls.isEmpty())
        assertTrue(store.addCalls.isEmpty())
        assertTrue(store.completionCalls.isEmpty())
    }

    private fun saveScreenshot(fileName: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Quick Aside/Change 012",
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

    private fun session(
        id: String,
        startedAt: String,
        endedAt: String?,
    ): ListSession = ListSession(
        id = ListSessionId(id),
        listDefinitionId = BuiltInListDefinitions.MANDADO.id,
        startedAt = Instant.parse(startedAt),
        endedAt = endedAt?.let(Instant::parse),
    )

    private fun sessionWithItems(
        id: String,
        startedAt: String,
        endedAt: String?,
        items: List<ListItem>,
    ): ListSessionWithItems = ListSessionWithItems(
        session = session(id, startedAt, endedAt),
        items = items,
    )

    private fun item(
        id: String,
        text: String,
        sessionId: String,
        completed: Boolean = false,
    ): ListItem = ListItem(
        id = ListItemId(id),
        listDefinitionId = BuiltInListDefinitions.MANDADO.id,
        text = text,
        listSessionId = ListSessionId(sessionId),
        isCompleted = completed,
        createdAt = Instant.parse("2026-09-05T18:21:00Z"),
    )
}

private class FakeMandadoHistoryListStore : ListStore {
    val readRecentCalls = mutableListOf<ListDefinitionId>()
    val readSessionCalls = mutableListOf<ListSessionId>()
    val startCalls = mutableListOf<ListDefinitionId>()
    val finishActiveCalls = mutableListOf<ListDefinitionId>()
    val finishSessionCalls = mutableListOf<ListSessionId>()
    val addCalls = mutableListOf<String>()
    val completionCalls = mutableListOf<ListItemId>()
    var activeSession: ListSession? = null
    var currentItems: List<ListItem> = emptyList()
    var history: List<ListSessionWithItems> = emptyList()
    var failHistoryReads: Int = 0

    override suspend fun readBuiltInDefinitions(): List<ListDefinition> =
        BuiltInListDefinitions.ALL

    override suspend fun getActiveSession(listDefinitionId: ListDefinitionId): ListSession? =
        activeSession

    override suspend fun startSession(listDefinitionId: ListDefinitionId): SessionStartResult {
        startCalls += listDefinitionId
        return SessionStartResult.NotSessionBased
    }

    override suspend fun finishActiveSession(listDefinitionId: ListDefinitionId): SessionFinishResult {
        finishActiveCalls += listDefinitionId
        return SessionFinishResult.NoActiveSession
    }

    override suspend fun finishSession(listSessionId: ListSessionId): SessionFinishResult {
        finishSessionCalls += listSessionId
        return SessionFinishResult.MissingSession
    }

    override suspend fun readSession(listSessionId: ListSessionId): ListSessionWithItems? {
        readSessionCalls += listSessionId
        return history.singleOrNull { it.session.id == listSessionId }
    }

    override suspend fun readRecentSessions(
        listDefinitionId: ListDefinitionId,
    ): List<ListSessionWithItems> {
        readRecentCalls += listDefinitionId
        if (failHistoryReads > 0) {
            failHistoryReads -= 1
            throw IllegalStateException("history unavailable")
        }
        return history
    }

    override suspend fun readCurrentItems(listDefinitionId: ListDefinitionId): List<ListItem> =
        currentItems

    override suspend fun addItem(
        listDefinitionId: ListDefinitionId,
        text: String,
        listSessionId: ListSessionId?,
    ): AddListItemResult {
        addCalls += text
        return AddListItemResult.Failed(UnsupportedOperationException("not used"))
    }

    override suspend fun setItemCompleted(
        listItemId: ListItemId,
        isCompleted: Boolean,
    ): ItemCompletionResult {
        completionCalls += listItemId
        return ItemCompletionResult.Failed(UnsupportedOperationException("not used"))
    }

    override suspend fun toggleItemCompleted(listItemId: ListItemId): ItemCompletionResult =
        ItemCompletionResult.Failed(UnsupportedOperationException("not used"))
}

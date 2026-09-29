package com.edu.quickaside

import android.content.Context
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.capture.CaptureInterpretationResult
import com.edu.quickaside.application.capture.CaptureInterpreter
import com.edu.quickaside.application.capture.CapturePlanListExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanListExecutionResult
import com.edu.quickaside.application.capture.CapturePlanListExecutor
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.MandadoSessionChoice
import com.edu.quickaside.application.capture.UndoCapturePlanListExecutionResult
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListItemIdProvider
import com.edu.quickaside.application.lists.ListSessionIdProvider
import com.edu.quickaside.data.local.ListSessionEntity
import com.edu.quickaside.data.local.QuickAsideDatabase
import com.edu.quickaside.data.local.RoomCapturePlanListExecutor
import com.edu.quickaside.data.local.RoomCaptureReader
import com.edu.quickaside.data.local.RoomCaptureWriter
import com.edu.quickaside.data.local.RoomListStore
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureListAutoExecutionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-028-ui-${UUID.randomUUID()}.db"
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun textSingleExecutionShowsMutationReceiptAndUndo() {
        val executor = RecordingExecutor(executed("ledger-single", "item-single"))
        setContent(submission(listOf(compras("leche")), executor), executor)

        submitText("Compra leche")

        waitForText("Producto agregado")
        composeRule.onNodeWithText("Deshacer").assertIsDisplayed()
        assertEquals(1, executor.executeCalls)
    }

    @Test
    fun textBatchShowsAccurateCountAndForwardsOneExactOrderedUndo() {
        val receipt = executed("ledger-batch", "item-1", "item-2", "item-3")
        val executor = RecordingExecutor(
            executeResult = receipt,
            undoResult = UndoCapturePlanListExecutionResult.Undone(
                receipt.actionLedgerEntryId,
                receipt.items.map(ListItem::id),
            ),
        )
        setContent(
            submission(listOf(compras("uno"), compras("dos"), compras("tres")), executor),
            executor,
        )

        submitText("Guarda tres cosas")
        waitForText("3 elementos guardados")
        composeRule.onAllNodesWithText("Deshacer").assertCountEquals(1)
        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("Cambio deshecho")

        assertEquals(1, executor.executeCalls)
        assertEquals(
            listOf(
                UndoCall(
                    ActionLedgerEntryId("ledger-batch"),
                    listOf(ListItemId("item-1"), ListItemId("item-2"), ListItemId("item-3")),
                ),
            ),
            executor.undoCalls,
        )
    }

    @Test
    fun unsupportedValidPlanKeepsHonestNotAppliedCopyWithoutUndo() {
        val executor = RecordingExecutor(executed("unused", "unused"))
        setContent(
            submission(
                listOf(CapturePlanAction.CreateNote("Pagar luz")),
                executor,
            ),
            executor,
        )

        submitText("Paga la luz")

        waitForText("Captura guardada · interpretación lista, sin aplicar")
        composeRule.onNodeWithText("Producto agregado").assertDoesNotExist()
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()
        assertEquals(0, executor.executeCalls)
    }

    @Test
    fun executorRejectionShowsNonSuccessCopyWithoutUndo() {
        val executor = RecordingExecutor(
            CapturePlanListExecutionResult.Rejected(
                0,
                CapturePlanListExecutionRejectionReason.NO_ACTIVE_SESSION,
            ),
        )
        setContent(submission(listOf(mandado("aguacate")), executor), executor)

        submitText("Agrega aguacate")

        waitForText("Captura guardada · no se pudo aplicar la interpretación")
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()
    }

    @Test
    fun executorFailureShowsNonSuccessCopyWithoutUndo() {
        val executor = RecordingExecutor(
            CapturePlanListExecutionResult.Failed(IllegalStateException("write failed")),
        )
        setContent(submission(listOf(compras("cuerdas")), executor), executor)

        submitText("Compra cuerdas")

        waitForText("Captura guardada · no se pudo aplicar la interpretación")
        composeRule.onNodeWithText("Deshacer").assertDoesNotExist()
    }

    @Test
    fun undoFailureDoesNotClaimSuccessAndShowsGenericError() {
        val executor = RecordingExecutor(
            executeResult = executed("ledger-failed-undo", "item-failed-undo"),
            undoResult = UndoCapturePlanListExecutionResult.Failed(
                IllegalStateException("undo failed"),
            ),
        )
        setContent(submission(listOf(compras("cable")), executor), executor)
        submitText("Compra cable")
        waitForText("Producto agregado")

        composeRule.onNodeWithText("Deshacer").performClick()

        waitForText("No se pudo deshacer.")
        composeRule.onNodeWithText("Cambio deshecho").assertDoesNotExist()
        assertEquals(1, executor.undoCalls.size)
    }

    @Test
    fun voiceUsesSameReceiptExecutesOnceAndClosesCaptureSurface() {
        val executor = RecordingExecutor(executed("voice-ledger", "voice-item"))
        val factory = FakeSpeechTranscriberFactory()
        setContent(
            submission(listOf(compras("leche")), executor),
            executor,
            speechFactory = factory,
        )
        composeRule.onNodeWithContentDescription("Hablar").performClick()
        waitForText("Listo para escuchar…")

        factory.latest().emitFinal("Compra leche")
        factory.latest().emitFinal("No ejecutar dos veces")

        waitForText("Producto agregado")
        composeRule.onNodeWithText("Deshacer").assertIsDisplayed()
        composeRule.onNodeWithText("Captura").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Capturar").assertIsDisplayed()
        assertEquals(1, executor.executeCalls)
    }

    @Test
    fun globalVoiceCaptureRefreshesVisibleComprasAndUndoRemovesTheExactItem() {
        val realExecutor = RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("visible-item")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("visible-ledger")),
            clock = QueueClock(
                listOf(
                    Instant.parse("2026-09-19T21:01:00Z"),
                    Instant.parse("2026-09-19T21:02:00Z"),
                ),
            ),
        )
        val factory = FakeSpeechTranscriberFactory()
        setContent(
            submission(listOf(compras("producto visible")), realExecutor),
            realExecutor,
            speechFactory = factory,
            includeListStore = true,
        )
        composeRule.onNode(hasText("Listas") and hasClickAction()).performClick()
        composeRule.onNodeWithContentDescription("Abrir Compras").performClick()
        waitForText("Aún no hay productos.")

        composeRule.onNodeWithContentDescription("Capturar").performClick()
        waitForText("Listo para escuchar…")
        factory.latest().emitFinal("Compra producto visible")

        waitForText("Producto agregado")
        waitForText("producto visible")
        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("Cambio deshecho")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("producto visible").fetchSemanticsNodes().isEmpty()
        }
        val (items, captures) = runBlocking {
            database.listItemDao()
                .getContinuousByDefinitionId(BuiltInListDefinitions.COMPRAS.id.value) to
                database.captureDao().getRecent(50)
        }
        assertTrue(items.isEmpty())
        assertEquals(1, captures.count { it.id == "ui-capture" })
    }

    @Test
    fun globalVoiceCaptureBootstrapsVisibleMandadoWithoutManualStart() {
        val realExecutor = RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("visible-mandado-item")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("visible-mandado-ledger")),
            clock = QueueClock(listOf(Instant.parse("2026-09-19T21:10:00Z"))),
            sessionIdProvider = QueueSessionIdProvider(listOf("visible-mandado-session")),
        )
        val factory = FakeSpeechTranscriberFactory()
        setContent(
            submission(listOf(mandado("jabón visible")), realExecutor),
            realExecutor,
            speechFactory = factory,
            includeListStore = true,
        )
        composeRule.onNode(hasText("Listas") and hasClickAction()).performClick()
        composeRule.onNodeWithContentDescription("Abrir Mandado").performClick()
        waitForText("No hay un mandado activo.")
        composeRule.onNodeWithContentDescription("Iniciar mandado").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Capturar").performClick()
        waitForText("Listo para escuchar…")
        factory.latest().emitFinal("Agrega jabón visible")

        waitForText("Producto agregado")
        waitForText("jabón visible")
        val (session, items) = runBlocking {
            database.listSessionDao().getActiveByDefinitionId("mandado") to
                database.listItemDao().getBySessionId("visible-mandado-session")
        }
        assertEquals("visible-mandado-session", session?.id)
        assertEquals(listOf("visible-mandado-item"), items.map { it.id })
    }

    @Test
    fun staleMandadoChoiceShowsContinueAndNewThenAppliesOnContinueWithUndo() {
        val requirementTime = Instant.parse("2026-09-19T21:20:00Z")
        val resolvedAt = Instant.parse("2026-09-19T21:21:00Z")
        val undoAt = Instant.parse("2026-09-19T21:21:30Z")
        seedMandadoSession(
            id = "stale-ui-session",
            startedAt = requirementTime.minus(java.time.Duration.ofDays(8)),
        )
        val realExecutor = RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("stale-ui-item")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("stale-ui-ledger")),
            clock = QueueClock(listOf(requirementTime, resolvedAt, undoAt)),
            sessionIdProvider = QueueSessionIdProvider(emptyList()),
        )
        setContent(
            submission(listOf(mandado("jabón stale")), realExecutor),
            realExecutor,
            includeListStore = true,
        )

        submitText("Agrega jabón stale")

        waitForText("Mandado anterior")
        composeRule.onNodeWithText("Continuar").assertIsDisplayed()
        composeRule.onNodeWithText("Nuevo").assertIsDisplayed()
        runBlocking { assertNull(database.listItemDao().getById("stale-ui-item")) }

        composeRule.onNodeWithText("Continuar").performClick()

        waitForText("Producto agregado")
        runBlocking {
            assertEquals(
                "stale-ui-session",
                database.listSessionDao().getActiveByDefinitionId("mandado")?.id,
            )
            assertNotNull(database.listItemDao().getById("stale-ui-item"))
        }

        composeRule.onNodeWithText("Deshacer").performClick()
        waitForText("Cambio deshecho")
        runBlocking {
            assertNull(database.listItemDao().getById("stale-ui-item"))
            assertNotNull(database.listSessionDao().getById("stale-ui-session"))
        }
    }

    @Test
    fun staleMandadoChoiceNewEndsOldSessionAndAppliesToReplacementSession() {
        val requirementTime = Instant.parse("2026-09-19T21:22:00Z")
        val resolvedAt = Instant.parse("2026-09-19T21:23:00Z")
        seedMandadoSession(
            id = "stale-new-old-session",
            startedAt = requirementTime.minus(java.time.Duration.ofDays(8)),
        )
        val realExecutor = RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("stale-new-item")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("stale-new-ledger")),
            clock = QueueClock(listOf(requirementTime, resolvedAt)),
            sessionIdProvider = QueueSessionIdProvider(listOf("stale-new-replacement")),
        )
        setContent(
            submission(listOf(mandado("jabón nuevo")), realExecutor),
            realExecutor,
            includeListStore = true,
        )

        submitText("Agrega jabón nuevo")
        waitForText("Mandado anterior")
        composeRule.onNodeWithText("Nuevo").performClick()

        waitForText("Producto agregado")
        runBlocking {
            assertEquals(
                resolvedAt.toEpochMilli(),
                database.listSessionDao().getById("stale-new-old-session")?.endedAtEpochMillis,
            )
            assertEquals(
                "stale-new-replacement",
                database.listSessionDao().getActiveByDefinitionId("mandado")?.id,
            )
            assertEquals(
                "stale-new-replacement",
                database.listItemDao().getById("stale-new-item")?.listSessionId,
            )
        }
    }

    @Test
    fun dismissingStaleMandadoChoiceLeavesSessionAndCaptureUnchanged() {
        val requirementTime = Instant.parse("2026-09-19T21:24:00Z")
        val staleActivity = requirementTime.minus(java.time.Duration.ofDays(8))
        seedMandadoSession(
            id = "dismissed-stale-session",
            startedAt = staleActivity,
        )
        val realExecutor = RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("dismissed-item")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("dismissed-ledger")),
            clock = QueueClock(listOf(requirementTime)),
            sessionIdProvider = QueueSessionIdProvider(emptyList()),
        )
        setContent(
            submission(listOf(mandado("jabón descartado")), realExecutor),
            realExecutor,
            includeListStore = true,
        )

        submitText("Agrega jabón descartado")
        waitForText("Mandado anterior")

        composeRule.onNodeWithText("Mandado anterior").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Mandado anterior").fetchSemanticsNodes().isEmpty()
        }

        runBlocking {
            assertNotNull(database.captureDao().getById("ui-capture"))
            assertNull(database.listItemDao().getById("dismissed-item"))
            assertEquals(
                staleActivity.toEpochMilli(),
                database.listSessionDao().getById("dismissed-stale-session")
                    ?.lastActivityAtEpochMillis,
            )
            assertNull(
                database.listSessionDao().getById("dismissed-stale-session")?.endedAtEpochMillis,
            )
            assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        }
    }

    private fun seedMandadoSession(
        id: String,
        startedAt: Instant,
        lastActivityAt: Instant = startedAt,
    ) {
        runBlocking {
            database.listSessionDao().insert(
                ListSessionEntity(
                    id = id,
                    listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                    startedAtEpochMillis = startedAt.toEpochMilli(),
                    lastActivityAtEpochMillis = lastActivityAt.toEpochMilli(),
                ),
            )
        }
    }

    private fun submission(
        actions: List<CapturePlanAction>,
        executor: CapturePlanListExecutor,
    ) = CaptureSubmission(
        writer = RoomCaptureWriter(database),
        interpreter = CaptureInterpreter { capture ->
            CaptureInterpretationResult.Success(CapturePlan(capture.id, actions))
        },
        listExecutor = executor,
        idProvider = { CaptureId("ui-capture") },
        capturedAtProvider = { Instant.parse("2026-09-19T21:00:00Z") },
    )

    private fun setContent(
        submission: CaptureSubmission,
        executor: CapturePlanListExecutor,
        speechFactory: FakeSpeechTranscriberFactory = FakeSpeechTranscriberFactory(),
        includeListStore: Boolean = false,
    ) {
        val listStore = if (includeListStore) RoomListStore(database) else null
        val permission = FakeMicrophonePermissionController(granted = true)
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = submission,
                        captureReader = RoomCaptureReader(database),
                        capturePlanListExecutor = executor,
                        listStore = listStore,
                        speechTranscriberFactory = speechFactory,
                        microphonePermissionController = permission,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun submitText(text: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
        composeRule.onNodeWithContentDescription("Enviar captura").performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun compras(text: String) = CapturePlanAction.AddListItem(
        BuiltInListDefinitions.COMPRAS.id,
        text,
    )

    private fun mandado(text: String) = CapturePlanAction.AddListItem(
        BuiltInListDefinitions.MANDADO.id,
        text,
    )

    private fun executed(
        ledgerId: String,
        vararg itemIds: String,
    ) = CapturePlanListExecutionResult.Executed(
        items = itemIds.mapIndexed { index, id ->
            ListItem(
                id = ListItemId(id),
                listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
                text = "item-$index",
                listSessionId = null,
                isCompleted = false,
                createdAt = Instant.parse("2026-09-19T21:00:00Z"),
            )
        },
        actionLedgerEntryId = ActionLedgerEntryId(ledgerId),
    )

    private data class UndoCall(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val itemIds: List<ListItemId>,
    )

    private class RecordingExecutor(
        private val executeResult: CapturePlanListExecutionResult,
        private val undoResult: UndoCapturePlanListExecutionResult =
            UndoCapturePlanListExecutionResult.Failed(IllegalStateException("unused")),
    ) : CapturePlanListExecutor {
        var executeCalls = 0
            private set
        val undoCalls = mutableListOf<UndoCall>()

        override suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult {
            executeCalls += 1
            return executeResult
        }

        override suspend fun resolveMandadoSessionChoice(
            requirement: CapturePlanListExecutionResult.RequiresMandadoSessionChoice,
            choice: MandadoSessionChoice,
        ): CapturePlanListExecutionResult = error("Resolution is not used")

        override suspend fun undoExecution(
            actionLedgerEntryId: ActionLedgerEntryId,
            expectedItemIds: List<ListItemId>,
            autoCreatedMandadoSessionId: ListSessionId?,
        ): UndoCapturePlanListExecutionResult {
            undoCalls += UndoCall(actionLedgerEntryId, expectedItemIds)
            return undoResult
        }
    }

    private class QueueItemIdProvider(ids: List<String>) : ListItemIdProvider {
        private val values = ArrayDeque(ids.map(::ListItemId))
        override fun nextItemId(): ListItemId = values.removeFirst()
    }

    private class QueueEntryIdProvider(ids: List<String>) : ActionLedgerIdProvider {
        private val values = ArrayDeque(ids.map(::ActionLedgerEntryId))
        override fun nextEntryId(): ActionLedgerEntryId = values.removeFirst()
    }

    private class QueueSessionIdProvider(ids: List<String>) : ListSessionIdProvider {
        private val values = ArrayDeque(ids.map(::ListSessionId))
        override fun nextSessionId(): ListSessionId = values.removeFirst()
    }

    private class QueueClock(times: List<Instant>) : ListClock {
        private val values = ArrayDeque(times)
        override fun now(): Instant = values.removeFirst()
    }
}

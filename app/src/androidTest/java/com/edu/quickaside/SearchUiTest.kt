package com.edu.quickaside

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.search.LocalSearch
import com.edu.quickaside.application.search.LocalSearchResult
import com.edu.quickaside.application.search.StructuredLogSearchField
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.CaptureKind
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.memory.NoteTimestampFormatter
import com.edu.quickaside.ui.theme.QuickAsideTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val timestampFormatter = NoteTimestampFormatter(
        zoneId = ZoneId.of("UTC"),
        locale = Locale.ENGLISH,
        clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneId.of("UTC")),
    )
    private lateinit var search: FakeLocalSearch

    @Before
    fun setUp() {
        search = FakeLocalSearch()
    }

    @Test
    fun memoriaDefaultsToHistoryAndPreservesNotesAndStructuredLogs() {
        setContent()
        openMemoria()

        waitForText("Capturas recientes")
        composeRule.onNodeWithText("Notas").assertIsDisplayed()
        composeRule.onNodeWithText("Registros").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Abrir búsqueda en Memoria")
            .assertIsDisplayed()
    }

    @Test
    fun searchAffordanceOpensNestedRouteWithCorrectTitleAndBack() {
        setContent()
        openSearch()

        composeRule.onNodeWithText("Buscar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Volver a Memoria").performClick()
        waitForText("Capturas recientes")
    }

    @Test
    fun androidBackFromSearchReturnsToHistoryWithoutLeavingMemoria() {
        setContent()
        openSearch()

        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        waitForText("Capturas recientes")
        composeRule.onNodeWithContentDescription("Abrir búsqueda en Memoria")
            .assertIsDisplayed()
    }

    @Test
    fun bottomNavigationResetsSearchRouteAndKeepsExactlyFourDestinations() {
        setContent()
        openSearch()

        listOf("Inicio", "Pendientes", "Listas", "Memoria").forEach { label ->
            composeRule.onAllNodes(hasText(label) and hasClickAction()).assertCountEquals(1)
        }
        composeRule.onNodeWithText("Buscar").assertIsDisplayed()
        composeRule.onNode(hasText("Pendientes") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Memoria") and hasClickAction()).performClick()

        waitForText("Capturas recientes")
        composeRule.onNodeWithText("Buscar", substring = false).assertDoesNotExist()
    }

    @Test
    fun searchIsNotABottomDestinationAndGlobalCaptureRemainsAvailable() {
        setContent()
        openSearch()

        composeRule.onNodeWithContentDescription("Capturar").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Buscar") and hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun initialSearchStateMakesNoLocalSearchCall() {
        setContent()
        openSearch()

        composeRule.onNodeWithText("Busca en tus capturas, notas, registros y listas.")
            .assertIsDisplayed()
        assertTrue(search.queries.isEmpty())
    }

    @Test
    fun blankSubmitMakesNoLocalSearchCallAndKeepsInitialState() {
        setContent()
        openSearch()

        composeRule.onNode(hasSetTextAction()).performTextReplacement(" \t\n ")
        composeRule.onNodeWithContentDescription("Buscar").performClick()

        composeRule.onNodeWithText("Busca en tus capturas, notas, registros y listas.")
            .assertIsDisplayed()
        assertTrue(search.queries.isEmpty())
    }

    @Test
    fun exactSubmittedQueryReachesLocalSearchWithoutUiNormalization() {
        search.results = listOf(noteResult("query-result", "Encontrado"))
        setContent()
        openSearch()

        val submittedInput = "  durable token  "
        composeRule.onNode(hasSetTextAction()).performTextReplacement(submittedInput)
        composeRule.onNodeWithContentDescription("Buscar").performClick()

        waitForText("Encontrado")
        assertEquals(listOf(submittedInput), search.queries)
    }

    @Test
    fun loadingStateIsVisibleAndConcurrentVisibleSubmitDoesNotCreateAnotherCall() {
        search.searchGate = CompletableDeferred()
        setContent()
        openSearch()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("primera búsqueda")
        composeRule.onNodeWithContentDescription("Buscar").performClick()
        waitForText("Buscando…")
        assertEquals(1, search.queries.size)
        composeRule.onNodeWithContentDescription("Buscar").assertIsNotEnabled()

        search.searchGate?.complete(emptyList())
        waitForText("No encontramos resultados")
    }

    @Test
    fun imeSearchWhileLoadingDoesNotCreateAnotherCall() {
        search.searchGate = CompletableDeferred()
        setContent()
        openSearch()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("primera búsqueda")
        composeRule.onNodeWithContentDescription("Buscar").performClick()
        waitForText("Buscando…")
        composeRule.onNode(hasSetTextAction()).performImeAction()

        assertEquals(listOf("primera búsqueda"), search.queries)
        search.searchGate?.complete(emptyList())
        waitForText("No encontramos resultados")
    }

    @Test
    fun captureResultRendersDisplayTextKindAndTimestamp() {
        search.results = listOf(
            LocalSearchResult.Capture(
                captureId = CaptureId("capture-result"),
                captureKind = CaptureKind.VOICE,
                displayText = "Mañana revisa el PR",
                capturedAt = Instant.parse("2026-09-06T11:00:00Z"),
            ),
        )
        setContent()
        search("PR")

        waitForText("Mañana revisa el PR")
        composeRule.onNodeWithText("Voz").assertIsDisplayed()
        composeRule.onNodeWithText("Voz · Hoy, 11:00").assertIsDisplayed()
    }

    @Test
    fun noteResultRendersDisplayTextKindAndTimestamp() {
        search.results = listOf(noteResult("note-result", "Llamar al taller"))
        setContent()
        search("taller")

        waitForText("Llamar al taller")
        composeRule.onNodeWithText("Nota").assertIsDisplayed()
        composeRule.onNodeWithText("Nota · Hoy, 12:00").assertIsDisplayed()
    }

    @Test
    fun structuredLogRendersEveryReturnedFieldInSuppliedOrder() {
        search.results = listOf(
            LocalSearchResult.StructuredLog(
                structuredLogId = StructuredLogId("log-result"),
                fields = listOf(
                    StructuredLogSearchField("zeta", "último"),
                    StructuredLogSearchField("alpha", "primero"),
                ),
                sourceCaptureId = null,
                createdAt = Instant.parse("2026-09-05T18:30:00Z"),
            ),
        )
        setContent()
        search("ejercicio")

        waitForText("Registro")
        composeRule.onNodeWithText("zeta").assertIsDisplayed()
        composeRule.onNodeWithText("último").assertIsDisplayed()
        composeRule.onNodeWithText("alpha").assertIsDisplayed()
        composeRule.onNodeWithText("primero").assertIsDisplayed()
        val zetaTop = composeRule.onNodeWithText("zeta").fetchSemanticsNode().boundsInRoot.top
        val alphaTop = composeRule.onNodeWithText("alpha").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Structured Log fields must preserve supplied order", zetaTop < alphaTop)
    }

    @Test
    fun listItemRendersDefinitionCompletionAndHistoricalMandadoContext() {
        search.results = listOf(
            listItemResult(
                id = "mandado-item",
                text = "Detergente",
                isCompleted = true,
                sessionStartedAt = Instant.parse("2026-09-04T09:00:00Z"),
                sessionEndedAt = Instant.parse("2026-09-04T10:00:00Z"),
            ),
        )
        setContent()
        search("Detergente")

        waitForText("Detergente")
        composeRule.onNodeWithText("Mandado").assertIsDisplayed()
        composeRule.onNodeWithText("Completado").assertIsDisplayed()
        composeRule.onNodeWithText("Sesión histórica · Ayer, 09:00").assertIsDisplayed()
    }

    @Test
    fun pendingListItemUsesVisiblePendingState() {
        search.results = listOf(
            listItemResult(
                id = "pending-item",
                text = "Pan",
                isCompleted = false,
                sessionStartedAt = null,
                sessionEndedAt = null,
            ),
        )
        setContent()
        search("Pan")

        waitForText("Pan")
        composeRule.onNodeWithText("Pendiente").assertIsDisplayed()
    }

    @Test
    fun mixedResultsPreserveExactLocalSearchOrder() {
        search.results = listOf(
            LocalSearchResult.Note(
                noteId = NoteId("first"),
                displayText = "Orden primero",
                sourceCaptureId = null,
                createdAt = Instant.parse("2026-09-01T10:00:00Z"),
            ),
            LocalSearchResult.Capture(
                captureId = CaptureId("second"),
                captureKind = CaptureKind.TEXT,
                displayText = "Orden segundo",
                capturedAt = Instant.parse("2026-09-06T10:00:00Z"),
            ),
            listItemResult(
                id = "third",
                text = "Orden tercero",
                isCompleted = false,
                sessionStartedAt = null,
                sessionEndedAt = null,
            ),
        )
        setContent()
        search("orden")

        waitForText("Orden primero")
        val firstTop = composeRule.onNodeWithText("Orden primero").fetchSemanticsNode()
            .boundsInRoot.top
        val secondTop = composeRule.onNodeWithText("Orden segundo").fetchSemanticsNode()
            .boundsInRoot.top
        val thirdTop = composeRule.onNodeWithText("Orden tercero").fetchSemanticsNode()
            .boundsInRoot.top
        assertTrue(firstTop < secondTop)
        assertTrue(secondTop < thirdTop)
    }

    @Test
    fun emptyStateIsQuerySpecific() {
        setContent()
        search("inexistente")

        waitForText("No encontramos resultados para «inexistente».")
    }

    @Test
    fun failureShowsGenericCopyWithoutExceptionDetails() {
        search.failure = IllegalStateException("database detail")
        setContent()
        search("falla")

        waitForText("No se pudo buscar en Memoria.")
        composeRule.onNodeWithText("database detail").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Reintentar búsqueda").assertIsDisplayed()
    }

    @Test
    fun retryUsesOriginalSubmittedInputInsteadOfNewDraft() {
        search.failure = IllegalStateException("temporary")
        setContent()
        search("consulta original")

        waitForText("No se pudo buscar en Memoria.")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("borrador nuevo")
        search.failure = null
        search.results = listOf(noteResult("retry-result", "Resultado del reintento"))
        composeRule.onNodeWithContentDescription("Reintentar búsqueda").performClick()

        waitForText("Resultado del reintento")
        assertEquals(
            listOf("consulta original", "consulta original"),
            search.queries,
        )
    }

    @Test
    fun cancellationIsNotConvertedToFailedState() {
        search.cancel = true
        setContent()
        search("cancelar")

        composeRule.waitUntil(timeoutMillis = 5_000) { search.queries.size == 1 }
        composeRule.onNodeWithText("No se pudo buscar en Memoria.").assertDoesNotExist()
    }

    @Test
    fun resultCardsHaveNoMutationOrOpenActions() {
        search.results = listOf(noteResult("read-only", "Solo lectura"))
        setContent()
        search("lectura")

        waitForText("Solo lectura")
        composeRule.onNodeWithTag("SearchResultCard-0").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Solo lectura") and hasClickAction()).assertCountEquals(0)
        composeRule.onNodeWithText("Editar").assertDoesNotExist()
        composeRule.onNodeWithText("Eliminar").assertDoesNotExist()
        composeRule.onNodeWithText("Abrir").assertDoesNotExist()
    }

    @Test
    fun finalSearchResultCanScrollFullyAboveCaptureFab() {
        search.results = (1..8).map { index ->
            noteResult(
                id = "scroll-note-$index",
                text = if (index == 8) "Resultado final" else "Resultado $index",
            )
        }
        setContent()
        search("resultado")

        waitForText("Resultados para")
        composeRule.onNodeWithTag("SearchResultsList")
            .performScrollToNode(hasTestTag("SearchResultCard-7"))
        composeRule.onNodeWithTag("SearchResultsList")
            .performSemanticsAction(SemanticsActions.ScrollBy) { action ->
                action(0f, 10_000_000f)
            }
        composeRule.waitForIdle()

        val finalCardBottom = composeRule.onNodeWithTag("SearchResultCard-7")
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        val fabTop = composeRule.onNodeWithContentDescription("Capturar")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(
            "The final Search card must scroll above the Capture FAB " +
                "(cardBottom=$finalCardBottom fabTop=$fabTop)",
            finalCardBottom <= fabTop,
        )
    }

    @Test
    fun capturesRepresentativeSearchVisualEvidenceOnDevice() {
        search.results = mixedResults()
        setContent()
        openMemoria()
        waitForText("Capturas recientes")
        saveScreenshot("memoria-search-affordance.png")

        openSearch()
        composeRule.onNodeWithText("Busca en tus capturas, notas, registros y listas.")
            .assertIsDisplayed()
        saveScreenshot("search-initial.png")

        composeRule.onNode(hasSetTextAction()).performTextReplacement("mezcla")
        composeRule.onNodeWithContentDescription("Buscar").performClick()
        waitForText("Registro")
        saveScreenshot("search-results-mixed.png")

        search.results = emptyList()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("sin resultados")
        composeRule.onNodeWithContentDescription("Buscar").performClick()
        waitForText("No encontramos resultados")
        saveScreenshot("search-empty.png")
    }

    @Test
    fun searchFieldExposesLabelPlaceholderAndSubmitAction() {
        setContent()
        openSearch()

        composeRule.onNodeWithText("Buscar en Memoria").assertIsDisplayed()
        composeRule.onNodeWithText("Escribe para buscar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Buscar").assertIsDisplayed()
    }

    @Test
    fun imeSearchSubmitsTheExactDraft() {
        search.results = listOf(noteResult("ime-result", "Resultado por teclado"))
        setContent()
        openSearch()

        val submittedInput = "  consulta por teclado  "
        composeRule.onNode(hasSetTextAction()).performTextReplacement(submittedInput)
        composeRule.onNode(hasSetTextAction()).performImeAction()

        waitForText("Resultado por teclado")
        assertEquals(listOf(submittedInput), search.queries)
    }

    @Test
    fun loadingAllowsDraftEditingWithoutStartingAnotherSearch() {
        search.searchGate = CompletableDeferred()
        setContent()
        openSearch()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("primera")
        composeRule.onNodeWithContentDescription("Buscar").performClick()
        waitForText("Buscando…")

        composeRule.onNode(hasSetTextAction()).performTextReplacement("borrador mientras carga")
        assertEquals(listOf("primera"), search.queries)

        search.searchGate?.complete(emptyList())
        waitForText("No encontramos resultados")
    }

    @Test
    fun blankSubmitAfterResultsKeepsThePreviousResultState() {
        search.results = listOf(noteResult("kept-result", "Resultado conservado"))
        setContent()
        search("primera")

        waitForText("Resultado conservado")
        composeRule.onNode(hasSetTextAction()).performTextReplacement(" \t ")
        composeRule.onNodeWithContentDescription("Buscar").performClick()

        composeRule.onNodeWithText("Resultado conservado").assertIsDisplayed()
        assertEquals(listOf("primera"), search.queries)
    }

    @Test
    fun laterValidSubmissionReplacesThePreviousResultState() {
        search.results = listOf(noteResult("first-result", "Primer resultado"))
        setContent()
        search("primera")

        waitForText("Primer resultado")
        search.results = listOf(noteResult("second-result", "Segundo resultado"))
        composeRule.onNode(hasSetTextAction()).performTextReplacement("segunda")
        composeRule.onNodeWithContentDescription("Buscar").performClick()

        waitForText("Segundo resultado")
        composeRule.onNodeWithText("Primer resultado").assertDoesNotExist()
        assertEquals(listOf("primera", "segunda"), search.queries)
    }

    @Test
    fun retryPreservesExactWhitespaceInTheLastSubmittedQuery() {
        search.failure = IllegalStateException("temporary")
        setContent()
        openSearch()

        val submittedInput = "  exacto  "
        composeRule.onNode(hasSetTextAction()).performTextReplacement(submittedInput)
        composeRule.onNodeWithContentDescription("Buscar").performClick()
        waitForText("No se pudo buscar en Memoria.")

        search.failure = null
        search.results = listOf(noteResult("retry-spaces", "Reintento exacto"))
        composeRule.onNode(hasSetTextAction()).performTextReplacement("otro borrador")
        composeRule.onNodeWithContentDescription("Reintentar búsqueda").performClick()

        waitForText("Reintento exacto")
        assertEquals(listOf(submittedInput, submittedInput), search.queries)
    }

    @Test
    fun currentListSessionContextAndCreatedTimestampAreVisible() {
        search.results = listOf(
            listItemResult(
                id = "current-item",
                text = "Leche",
                isCompleted = false,
                sessionStartedAt = Instant.parse("2026-09-06T09:00:00Z"),
                sessionEndedAt = null,
                definitionName = "Compras",
            ),
        )
        setContent()
        search("leche")

        waitForText("Leche")
        composeRule.onNodeWithText("Compras").assertIsDisplayed()
        composeRule.onNodeWithText("Sesión actual · Hoy, 09:00").assertIsDisplayed()
        composeRule.onNodeWithText("Creado · Hoy, 10:00").assertIsDisplayed()
    }

    @Test
    fun structuredLogWithNoFieldsStillRendersKindAndTimestamp() {
        search.results = listOf(
            LocalSearchResult.StructuredLog(
                structuredLogId = StructuredLogId("empty-fields"),
                fields = emptyList(),
                sourceCaptureId = null,
                createdAt = Instant.parse("2026-09-05T18:30:00Z"),
            ),
        )
        setContent()
        search("registro")

        waitForText("Registro")
        composeRule.onNodeWithText("Registro · Ayer, 18:30").assertIsDisplayed()
    }

    @Test
    fun textCaptureUsesTextKindLabelAndTimestamp() {
        search.results = listOf(
            LocalSearchResult.Capture(
                captureId = CaptureId("text-capture"),
                captureKind = CaptureKind.TEXT,
                displayText = "Captura escrita",
                capturedAt = Instant.parse("2026-09-06T08:15:00Z"),
            ),
        )
        setContent()
        search("escrita")

        waitForText("Captura escrita")
        composeRule.onNodeWithText("Texto").assertIsDisplayed()
        composeRule.onNodeWithText("Texto · Hoy, 08:15").assertIsDisplayed()
    }

    @Test
    fun resultsUseOneFlatListForAllReturnedKinds() {
        search.results = mixedResults()
        setContent()
        search("mezcla")

        waitForText("Resultados para")
        composeRule.onNodeWithTag("SearchResultsList").assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag("SearchResultsList")).assertCountEquals(1)
    }

    @Test
    fun resultCardsDoNotExposeSourceIds() {
        search.results = listOf(noteResult("secret-source-id", "Texto visible"))
        setContent()
        search("visible")

        waitForText("Texto visible")
        composeRule.onNodeWithText("secret-source-id").assertDoesNotExist()
    }

    @Test
    fun listItemCompletionStateIsNotAnInteractiveControl() {
        search.results = listOf(
            listItemResult(
                id = "read-only-item",
                text = "Solo consulta",
                isCompleted = true,
                sessionStartedAt = null,
                sessionEndedAt = null,
            ),
        )
        setContent()
        search("consulta")

        waitForText("Solo consulta")
        composeRule.onAllNodes(hasText("Completado") and hasClickAction()).assertCountEquals(0)
    }

    private fun setContent() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                QuickAsideTheme {
                    QuickAsideApp(
                        captureSubmission = CaptureSubmission(CaptureWriter { }),
                        captureReader = CaptureReader { emptyList() },
                        localSearch = search,
                        noteTimestampFormatter = timestampFormatter,
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

    private fun openSearch() {
        openMemoria()
        composeRule.onNodeWithContentDescription("Abrir búsqueda en Memoria").performClick()
        composeRule.waitForIdle()
    }

    private fun search(query: String) {
        openSearch()
        composeRule.onNode(hasSetTextAction()).performTextReplacement(query)
        composeRule.onNodeWithContentDescription("Buscar").performClick()
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun noteResult(id: String, text: String): LocalSearchResult.Note =
        LocalSearchResult.Note(
            noteId = NoteId(id),
            displayText = text,
            sourceCaptureId = null,
            createdAt = Instant.parse("2026-09-06T12:00:00Z"),
        )

    private fun listItemResult(
        id: String,
        text: String,
        isCompleted: Boolean,
        sessionStartedAt: Instant?,
        sessionEndedAt: Instant?,
        definitionName: String = "Mandado",
    ): LocalSearchResult.ListItem = LocalSearchResult.ListItem(
        listItemId = ListItemId(id),
        displayText = text,
        isCompleted = isCompleted,
        listDefinitionId = ListDefinitionId("mandado"),
        listDefinitionName = definitionName,
        listSessionId = sessionStartedAt?.let { ListSessionId("session-$id") },
        listSessionStartedAt = sessionStartedAt,
        listSessionEndedAt = sessionEndedAt,
        createdAt = Instant.parse("2026-09-06T10:00:00Z"),
    )

    private fun mixedResults(): List<LocalSearchResult> = listOf(
        LocalSearchResult.Capture(
            captureId = CaptureId("visual-capture"),
            captureKind = CaptureKind.TEXT,
            displayText = "Captura de mezcla",
            capturedAt = Instant.parse("2026-09-06T11:00:00Z"),
        ),
        noteResult("visual-note", "Nota de mezcla"),
        LocalSearchResult.StructuredLog(
            structuredLogId = StructuredLogId("visual-log"),
            fields = listOf(
                StructuredLogSearchField("ejercicio", "press inclinado"),
                StructuredLogSearchField("peso", "210 lbs"),
            ),
            sourceCaptureId = null,
            createdAt = Instant.parse("2026-09-05T18:30:00Z"),
        ),
        listItemResult(
            id = "visual-item",
            text = "Detergente histórico",
            isCompleted = true,
            sessionStartedAt = Instant.parse("2026-09-04T09:00:00Z"),
            sessionEndedAt = Instant.parse("2026-09-04T10:00:00Z"),
        ),
    )

    private fun saveScreenshot(fileName: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Quick Aside/Change 017",
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
            } finally {
                screenshot.recycle()
            }
        }
    }
}

private class FakeLocalSearch : LocalSearch {
    val queries = mutableListOf<String>()
    var results: List<LocalSearchResult> = emptyList()
    var searchGate: CompletableDeferred<List<LocalSearchResult>>? = null
    var failure: Throwable? = null
    var cancel: Boolean = false

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<LocalSearchResult> {
        queries += query
        searchGate?.let { return it.await() }
        if (cancel) {
            throw CancellationException("test cancellation")
        }
        failure?.let { throw it }
        return results
    }
}

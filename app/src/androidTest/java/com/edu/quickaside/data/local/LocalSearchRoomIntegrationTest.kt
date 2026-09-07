package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.search.LocalSearchResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSearchRoomIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-016-local-search-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
        context.deleteDatabase(databaseName)
    }

    @Test
    fun capturesUseEffectiveVoiceTextAndSearchBeyondRecentHistory() = runBlocking {
        openDatabase()
        database.captureDao().insert(
            CaptureEntity(
                id = "old-unique",
                kind = "TEXT",
                originalText = "older-history-unique-token",
                capturedAtEpochMillis = 1L,
            ),
        )
        repeat(55) { index ->
            database.captureDao().insert(
                CaptureEntity(
                    id = "newer-${index.toString().padStart(2, '0')}",
                    kind = "TEXT",
                    originalText = "newer-unrelated-$index",
                    capturedAtEpochMillis = 100L + index,
                ),
            )
        }
        database.captureDao().insert(
            CaptureEntity(
                id = "voice-original",
                kind = "VOICE",
                originalText = "voice-original-token",
                capturedAtEpochMillis = 200L,
            ),
        )
        database.captureDao().insert(
            CaptureEntity(
                id = "voice-corrected",
                kind = "VOICE",
                originalText = "superseded-original-token",
                correctedTranscript = "voice-corrected-token",
                capturedAtEpochMillis = 300L,
            ),
        )

        val search = RoomLocalSearch(database)
        assertEquals(
            listOf("old-unique"),
            search.search("older-history-unique-token").map { (it as LocalSearchResult.Capture).captureId.value },
        )
        assertEquals(
            listOf("voice-original"),
            search.search("voice-original-token").map { it.sourceId },
        )
        assertEquals(
            listOf("voice-corrected"),
            search.search("voice-corrected-token").map { it.sourceId },
        )
        assertTrue(search.search("superseded-original-token").isEmpty())
    }

    @Test
    fun notesAndStructuredLogsSearchFieldsOnceAndReconstructEveryField() = runBlocking {
        openDatabase()
        database.noteDao().insert(
            NoteEntity(
                id = "note-match",
                text = "Note with durable search token",
                createdAtEpochMillis = 500L,
            ),
        )
        database.structuredLogDao().insert(
            StructuredLogEntity(
                id = "log-match",
                createdAtEpochMillis = 600L,
            ),
        )
        database.structuredLogFieldDao().insertAll(
            listOf(
                StructuredLogFieldEntity("log-match", "zeta", "value-token"),
                StructuredLogFieldEntity("log-match", "alpha-token", "another-value"),
            ),
        )

        val search = RoomLocalSearch(database)
        val keyMatch = search.search("alpha-token").single() as LocalSearchResult.StructuredLog
        assertEquals(
            listOf("alpha-token", "zeta"),
            keyMatch.fields.map { it.key },
        )
        assertEquals(
            listOf("another-value", "value-token"),
            keyMatch.fields.map { it.value },
        )
        assertEquals("alpha-token: another-value, zeta: value-token", keyMatch.displayText)

        val valueMatch = search.search("value-token").single() as LocalSearchResult.StructuredLog
        assertEquals("log-match", valueMatch.structuredLogId.value)
        assertEquals(2, valueMatch.fields.size)
        assertEquals("log-match", search.search("another-value").single().sourceId)
        assertEquals("note-match", search.search("durable").single().sourceId)
    }

    @Test
    fun allPersistedListItemsSearchWithDefinitionAndSessionContext() = runBlocking {
        openDatabase()
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "active-session",
                listDefinitionId = "mandado",
                startedAtEpochMillis = 10_000L,
            ),
        )
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "finished-session",
                listDefinitionId = "mandado",
                startedAtEpochMillis = 20_000L,
                endedAtEpochMillis = 21_000L,
            ),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "mandado-active",
                listDefinitionId = "mandado",
                listSessionId = "active-session",
                text = "active mandado durable token",
                isCompleted = false,
                createdAtEpochMillis = 100L,
            ),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "mandado-completed",
                listDefinitionId = "mandado",
                listSessionId = "active-session",
                text = "completed mandado durable token",
                isCompleted = true,
                createdAtEpochMillis = 200L,
            ),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "mandado-historical",
                listDefinitionId = "mandado",
                listSessionId = "finished-session",
                text = "historical mandado durable token",
                isCompleted = true,
                createdAtEpochMillis = 300L,
            ),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "compras-continuous",
                listDefinitionId = "compras",
                text = "continuous compras durable token",
                isCompleted = false,
                createdAtEpochMillis = 400L,
            ),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "compras-completed",
                listDefinitionId = "compras",
                text = "completed compras durable token",
                isCompleted = true,
                createdAtEpochMillis = 500L,
            ),
        )

        val results = RoomLocalSearch(database).search("durable token")
            .filterIsInstance<LocalSearchResult.ListItem>()

        assertEquals(
            listOf(
                "compras-completed",
                "compras-continuous",
                "mandado-historical",
                "mandado-completed",
                "mandado-active",
            ),
            results.map { it.listItemId.value },
        )
        val historical = results.first { it.listItemId.value == "mandado-historical" }
        assertEquals("mandado", historical.listDefinitionId.value)
        assertEquals("Mandado", historical.listDefinitionName)
        assertEquals("finished-session", historical.listSessionId?.value)
        assertEquals(Instant.ofEpochMilli(20_000L), historical.listSessionStartedAt)
        assertEquals(Instant.ofEpochMilli(21_000L), historical.listSessionEndedAt)
        assertTrue(historical.isCompleted)
        val continuous = results.first { it.listItemId.value == "compras-continuous" }
        assertEquals("Compras", continuous.listDefinitionName)
        assertEquals(null, continuous.listSessionId)
        assertEquals(null, continuous.listSessionStartedAt)
    }

    @Test
    fun likeWildcardsAreLiteralAndAsciiCaseIsInsensitive() = runBlocking {
        openDatabase()
        database.captureDao().insert(
            CaptureEntity("percent-exact", "TEXT", "100% literal", 1L),
        )
        database.captureDao().insert(
            CaptureEntity("percent-wildcard", "TEXT", "100X wildcard", 2L),
        )
        database.captureDao().insert(
            CaptureEntity("underscore-exact", "TEXT", "a_b literal", 3L),
        )
        database.captureDao().insert(
            CaptureEntity("underscore-wildcard", "TEXT", "axb wildcard", 4L),
        )
        database.captureDao().insert(
            CaptureEntity("backslash-exact", "TEXT", "C:\\temp literal", 5L),
        )
        database.captureDao().insert(
            CaptureEntity("backslash-wildcard", "TEXT", "C:Xtemp wildcard", 6L),
        )
        database.captureDao().insert(
            CaptureEntity("ascii-case", "TEXT", "CaseSensitive", 7L),
        )
        database.captureDao().insert(
            CaptureEntity("spanish-non-ascii", "TEXT", "ñ", 8L),
        )
        database.captureDao().insert(
            CaptureEntity("spanish-accent", "TEXT", "á", 9L),
        )

        val search = RoomLocalSearch(database)
        assertEquals(listOf("percent-exact"), search.search("100%").map { it.sourceId })
        assertEquals(listOf("underscore-exact"), search.search("a_b").map { it.sourceId })
        assertEquals(listOf("backslash-exact"), search.search("C:\\temp").map { it.sourceId })
        assertEquals(listOf("ascii-case"), search.search("casesensitive").map { it.sourceId })
        assertEquals(listOf("spanish-non-ascii"), search.search("ñ").map { it.sourceId })
        assertTrue(search.search("n").none { it.sourceId == "spanish-non-ascii" })
        assertEquals(listOf("spanish-accent"), search.search("á").map { it.sourceId })
        assertTrue(search.search("a").none { it.sourceId == "spanish-accent" })
        assertTrue(search.search("Á").none { it.sourceId == "spanish-accent" })
    }

    @Test
    fun blankQueryDoesNotReturnResultsAndCallerLimitIsCapped() = runBlocking {
        openDatabase()
        repeat(60) { index ->
            database.captureDao().insert(
                CaptureEntity(
                    id = "limit-$index",
                    kind = "TEXT",
                    originalText = "same-limit-token",
                    capturedAtEpochMillis = index.toLong(),
                ),
            )
        }

        val search = RoomLocalSearch(database)
        assertTrue(search.search("  \t\n ").isEmpty())
        assertEquals(3, search.search("same-limit-token", limit = 3).size)
        assertEquals(50, search.search("same-limit-token", limit = 100).size)
        assertTrue(search.search("same-limit-token", limit = 0).isEmpty())
    }

    @Test
    fun globalOrderingUsesTimestampIdAndKindAndListItemCreationTime() = runBlocking {
        openDatabase()
        val timestamp = 1_000L
        database.captureDao().insert(
            CaptureEntity("same-id", "TEXT", "cross-kind-tie", timestamp),
        )
        database.noteDao().insert(
            NoteEntity("same-id", "cross-kind-tie", createdAtEpochMillis = timestamp),
        )
        database.structuredLogDao().insert(
            StructuredLogEntity("same-id", createdAtEpochMillis = timestamp),
        )
        database.structuredLogFieldDao().insertAll(
            listOf(StructuredLogFieldEntity("same-id", "key", "cross-kind-tie")),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "same-id",
                listDefinitionId = "compras",
                text = "cross-kind-tie",
                isCompleted = false,
                createdAtEpochMillis = timestamp,
            ),
        )
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "late-session",
                listDefinitionId = "mandado",
                startedAtEpochMillis = 9_000L,
            ),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "created-time-item",
                listDefinitionId = "mandado",
                listSessionId = "late-session",
                text = "created-time-token",
                isCompleted = false,
                createdAtEpochMillis = 10L,
            ),
        )

        val search = RoomLocalSearch(database)
        val tieResults = search.search("cross-kind-tie")
        assertEquals(
            listOf("CAPTURE", "LIST_ITEM", "NOTE", "STRUCTURED_LOG"),
            tieResults.map { it.resultKind.name },
        )
        assertEquals(
            listOf("created-time-item"),
            search.search("created-time-token").map { it.sourceId },
        )
        assertEquals(Instant.ofEpochMilli(10L), search.search("created-time-token").single().timestamp)
    }

    @Test
    fun searchPreservesSourceDataAcrossCloseAndReopen() = runBlocking {
        openDatabase()
        database.captureDao().insert(
            CaptureEntity(
                id = "durable-voice",
                kind = "VOICE",
                originalText = "original durable",
                correctedTranscript = "corrected durable",
                capturedAtEpochMillis = 1L,
            ),
        )
        database.noteDao().insert(NoteEntity("durable-note", "durable note", createdAtEpochMillis = 2L))
        database.structuredLogDao().insert(StructuredLogEntity("durable-log", createdAtEpochMillis = 3L))
        database.structuredLogFieldDao().insertAll(
            listOf(StructuredLogFieldEntity("durable-log", "key", "durable log")),
        )
        database.listItemDao().insert(
            ListItemEntity(
                id = "durable-item",
                listDefinitionId = "compras",
                text = "durable item",
                isCompleted = true,
                createdAtEpochMillis = 4L,
            ),
        )

        val captureBefore = database.captureDao().getById("durable-voice")
        val noteBefore = database.noteDao().getById("durable-note")
        val logBefore = database.structuredLogDao().getById("durable-log")
        val fieldsBefore = database.structuredLogFieldDao().getByStructuredLogId("durable-log")
        val itemBefore = database.listItemDao().getById("durable-item")

        val search = RoomLocalSearch(database)
        assertEquals(4, search.search("durable").size)

        assertEquals(captureBefore, database.captureDao().getById("durable-voice"))
        assertEquals(noteBefore, database.noteDao().getById("durable-note"))
        assertEquals(logBefore, database.structuredLogDao().getById("durable-log"))
        assertEquals(fieldsBefore, database.structuredLogFieldDao().getByStructuredLogId("durable-log"))
        assertEquals(itemBefore, database.listItemDao().getById("durable-item"))

        database.close()
        openDatabase()

        val reopenedResults = RoomLocalSearch(database).search("corrected durable")
        assertEquals(listOf("durable-voice"), reopenedResults.map { it.sourceId })
        assertEquals(captureBefore, database.captureDao().getById("durable-voice"))
        assertEquals(noteBefore, database.noteDao().getById("durable-note"))
        assertEquals(logBefore, database.structuredLogDao().getById("durable-log"))
        assertEquals(fieldsBefore, database.structuredLogFieldDao().getByStructuredLogId("durable-log"))
        assertEquals(itemBefore, database.listItemDao().getById("durable-item"))
        assertTrue(database.listItemDao().getById("durable-item")?.isCompleted == true)
        assertFalse(database.captureDao().getById("durable-voice")?.originalText == "corrected durable")
    }

    private fun openDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }
}

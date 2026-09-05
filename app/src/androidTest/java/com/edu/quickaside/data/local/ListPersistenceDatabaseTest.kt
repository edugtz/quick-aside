package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureSubmissionResult
import com.edu.quickaside.application.capture.CaptureTranscriptCorrectionResult
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListIdProvider
import com.edu.quickaside.application.lists.SessionFinishResult
import com.edu.quickaside.application.lists.SessionStartResult
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@RunWith(AndroidJUnit4::class)
class ListPersistenceDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "list-persistence-${UUID.randomUUID()}.db"
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
    fun freshV3DatabaseContainsExactlyTheStableBuiltIns() = runBlocking {
        openFreshDatabase()

        assertEquals(BuiltInListDefinitions.ALL, RoomListStore(database).readBuiltInDefinitions())
        assertEquals(
            listOf("compras", "mandado"),
            database.listDefinitionDao().getAll().map { it.id },
        )

        database.close()
        assertEquals(3L, readUserVersion())
        assertTrue(readTables().containsAll(listOf("captures", "list_definitions", "list_sessions", "list_items")))
    }

    @Test
    fun realV2DatabaseMigratesCapturesAndSeedsBuiltInsWithoutChangingCaptureValues() = runBlocking {
        val rows = listOf(
            V2CaptureRow(
                id = "legacy-text",
                kind = "TEXT",
                originalText = "  Texto legado  ",
                capturedAtEpochMillis = 1788438896789,
                correctedTranscript = null,
            ),
            V2CaptureRow(
                id = "legacy-corrected-voice",
                kind = "VOICE",
                originalText = "comprar leche manana",
                capturedAtEpochMillis = 1788438900123,
                correctedTranscript = "Comprar leche mañana",
            ),
        )
        createVersion2Fixture(rows)

        openProductionDatabase()

        assertEquals(BuiltInListDefinitions.ALL, RoomListStore(database).readBuiltInDefinitions())
        assertEquals(2, database.listDefinitionDao().getAll().size)
        rows.forEach { row -> assertCaptureRow(row) }
        assertEquals(
            listOf("id", "kind", "original_text", "captured_at_epoch_millis", "corrected_transcript"),
            readColumns("captures"),
        )

        database.close()
        assertEquals(3L, readUserVersion())
        assertTrue(readTables().containsAll(listOf("captures", "list_definitions", "list_sessions", "list_items")))

        openProductionDatabase()
        rows.forEach { row -> assertCaptureRow(row) }
        assertEquals(BuiltInListDefinitions.ALL, RoomListStore(database).readBuiltInDefinitions())
    }

    @Test
    fun mandadoSessionLifecyclePreservesHistoryAndItems() = runBlocking {
        openFreshDatabase()
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(
                sessionIds = listOf("session-first", "session-second"),
                itemIds = listOf("item-first"),
            ),
            clock = QueueListClock(
                Instant.parse("2026-09-03T10:00:00Z"),
                Instant.parse("2026-09-03T10:01:00Z"),
                Instant.parse("2026-09-03T11:00:00Z"),
                Instant.parse("2026-09-03T12:00:00Z"),
            ),
        )

        val created = store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created
        val repeated = store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Existing
        assertEquals(created.session, repeated.session)

        val saved = store.addItem(
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "  Jabón  ",
        ) as AddListItemResult.Saved
        assertEquals(BuiltInListDefinitions.MANDADO.id, saved.item.listDefinitionId)
        assertEquals(created.session.id, saved.item.listSessionId)
        assertEquals("  Jabón  ", saved.item.text)
        assertFalse(saved.item.isCompleted)

        val completed = store.setItemCompleted(saved.item.id, true) as ItemCompletionResult.Updated
        assertTrue(completed.item.isCompleted)
        val toggled = store.toggleItemCompleted(saved.item.id) as ItemCompletionResult.Updated
        assertFalse(toggled.item.isCompleted)
        val completedAgain = store.setItemCompleted(saved.item.id, true) as ItemCompletionResult.Updated
        assertTrue(completedAgain.item.isCompleted)

        val finished = store.finishActiveSession(BuiltInListDefinitions.MANDADO.id)
            as SessionFinishResult.Finished
        assertEquals(Instant.parse("2026-09-03T11:00:00Z"), finished.session.endedAt)
        assertEquals(
            listOf(completed.item),
            store.readSession(created.session.id)?.items,
        )
        assertEquals(SessionFinishResult.AlreadyEnded, store.finishSession(created.session.id))
        assertEquals(
            SessionFinishResult.MissingSession,
            store.finishSession(ListSessionId("missing-session")),
        )
        assertEquals(
            SessionFinishResult.NoActiveSession,
            store.finishActiveSession(BuiltInListDefinitions.MANDADO.id),
        )

        val next = store.startSession(BuiltInListDefinitions.MANDADO.id) as SessionStartResult.Created
        assertNotEquals(created.session.id, next.session.id)
        assertEquals(
            listOf(next.session.id, created.session.id),
            store.readRecentSessions(BuiltInListDefinitions.MANDADO.id).map { it.session.id },
        )
    }

    @Test
    fun continuousItemsHaveNoSessionAndContinuousListsDoNotStartSessions() = runBlocking {
        openFreshDatabase()
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(itemIds = listOf("continuous-item")),
            clock = ListClock { Instant.parse("2026-09-03T12:00:00Z") },
        )

        assertEquals(
            SessionStartResult.NotSessionBased,
            store.startSession(BuiltInListDefinitions.COMPRAS.id),
        )
        val saved = store.addItem(BuiltInListDefinitions.COMPRAS.id, "  Cuerdas  ")
            as AddListItemResult.Saved
        assertEquals(BuiltInListDefinitions.COMPRAS.id, saved.item.listDefinitionId)
        assertNull(saved.item.listSessionId)
        assertEquals("  Cuerdas  ", saved.item.text)
        assertEquals(listOf(saved.item), store.readCurrentItems(BuiltInListDefinitions.COMPRAS.id))
        assertEquals(
            AddListItemResult.SessionNotAllowed,
            store.addItem(
                BuiltInListDefinitions.COMPRAS.id,
                "Invalid session attachment",
                ListSessionId("not-allowed"),
            ),
        )
        assertTrue(database.listSessionDao().getByDefinitionId("compras").isEmpty())
    }

    @Test
    fun sessionItemsRequireAnActiveSessionFromTheSameDefinition() = runBlocking {
        openFreshDatabase()
        database.listDefinitionDao().insert(
            ListDefinitionEntity("other", "Otra", "SESSION_BASED"),
        )
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "other-session",
                listDefinitionId = "other",
                startedAtEpochMillis = 1788436800000,
            ),
        )
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(
                sessionIds = listOf("mandado-session"),
                itemIds = listOf("unused"),
            ),
            clock = ListClock { Instant.parse("2026-09-03T12:00:00Z") },
        )

        assertEquals(
            AddListItemResult.SessionDefinitionMismatch,
            store.addItem(
                listDefinitionId = BuiltInListDefinitions.MANDADO.id,
                text = "Wrong session",
                listSessionId = ListSessionId("other-session"),
            ),
        )
        val started = (store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created).session
        assertTrue(store.finishSession(started.id) is SessionFinishResult.Finished)
        assertEquals(
            AddListItemResult.SessionNotActive,
            store.addItem(
                listDefinitionId = BuiltInListDefinitions.MANDADO.id,
                text = "Ended session",
                listSessionId = started.id,
            ),
        )
    }

    @Test
    fun blankItemTextCreatesNothingAndMandadoRequiresAnActiveSession() = runBlocking {
        openFreshDatabase()
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(itemIds = listOf("unused")),
            clock = ListClock { Instant.parse("2026-09-03T12:00:00Z") },
        )

        assertEquals(
            AddListItemResult.BlankText,
            store.addItem(BuiltInListDefinitions.COMPRAS.id, " \t\n "),
        )
        assertEquals(
            AddListItemResult.NoActiveSession,
            store.addItem(BuiltInListDefinitions.MANDADO.id, "Sin sesión"),
        )
        assertTrue(database.listItemDao().getContinuousByDefinitionId("compras").isEmpty())
    }

    @Test
    fun currentItemsUseCreatedTimeThenIdOrderingAndSessionHistoryUsesNewestStartFirst() = runBlocking {
        openFreshDatabase()
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(
                sessionIds = listOf("history-a", "history-b"),
                itemIds = listOf("item-b", "item-a"),
            ),
            clock = QueueListClock(
                Instant.parse("2026-09-03T10:00:00Z"),
                Instant.parse("2026-09-03T12:01:00Z"),
                Instant.parse("2026-09-03T12:00:00Z"),
                Instant.parse("2026-09-03T13:00:00Z"),
                Instant.parse("2026-09-03T11:00:00Z"),
            ),
        )

        val firstSession = (store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created).session
        val firstItem = store.addItem(BuiltInListDefinitions.MANDADO.id, "later")
            as AddListItemResult.Saved
        val secondItem = store.addItem(BuiltInListDefinitions.MANDADO.id, "earlier")
            as AddListItemResult.Saved
        assertEquals(
            listOf(secondItem.item.id, firstItem.item.id),
            store.readCurrentItems(BuiltInListDefinitions.MANDADO.id).map(ListItem::id),
        )

        assertTrue(store.finishActiveSession(BuiltInListDefinitions.MANDADO.id) is SessionFinishResult.Finished)
        val secondSession = (store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created).session
        assertEquals(
            listOf(secondSession.id, firstSession.id),
            store.readRecentSessions(BuiltInListDefinitions.MANDADO.id).map { it.session.id },
        )
    }

    @Test
    fun completedStateSurvivesCloseAndReopen() = runBlocking {
        openFreshDatabase()
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(itemIds = listOf("reopen-item")),
            clock = ListClock { Instant.parse("2026-09-03T12:00:00Z") },
        )
        val saved = store.addItem(BuiltInListDefinitions.COMPRAS.id, "Persisted checkbox")
            as AddListItemResult.Saved
        assertTrue(store.setItemCompleted(saved.item.id, true) is ItemCompletionResult.Updated)

        database.close()
        openProductionDatabase()

        val restored = RoomListStore(database).readCurrentItems(BuiltInListDefinitions.COMPRAS.id)
        assertEquals(true, restored.single().isCompleted)
        assertEquals(saved.item.id, restored.single().id)
        assertEquals(saved.item.text, restored.single().text)
    }

    @Test
    fun missingCompletionUpdateIsNotReportedAsSuccess() = runBlocking {
        openFreshDatabase()
        val result = RoomListStore(database).setItemCompleted(ListItemId("missing"), true)

        assertEquals(ItemCompletionResult.Missing, result)
        assertFalse(result is ItemCompletionResult.Updated)
    }

    @Test
    fun concurrentMandadoStartsShareOneActiveSession() = runBlocking {
        openFreshDatabase()
        val store = RoomListStore(
            database = database,
            idProvider = ConcurrentListIdProvider(),
            clock = ListClock { Instant.parse("2026-09-03T12:00:00Z") },
        )

        val results = coroutineScope {
            (0 until 8).map {
                async(Dispatchers.Default) {
                    store.startSession(BuiltInListDefinitions.MANDADO.id)
                }
            }.awaitAll()
        }

        val sessions = database.listSessionDao()
            .getByDefinitionId(BuiltInListDefinitions.MANDADO.id.value)
            .filter { it.endedAtEpochMillis == null }
        assertEquals(1, sessions.size)
        assertEquals(1, results.count { it is SessionStartResult.Created })
        assertEquals(
            setOf(sessions.single().id),
            results.map { result ->
                when (result) {
                    is SessionStartResult.Created -> result.session.id.value
                    is SessionStartResult.Existing -> result.session.id.value
                    else -> error("Concurrent start unexpectedly failed: $result")
                }
            }.toSet(),
        )
    }

    @Test
    fun capturesAndTranscriptCorrectionStillWorkOnVersion3() = runBlocking {
        openFreshDatabase()
        val submission = CaptureSubmission(
            writer = RoomCaptureWriter(database),
            idProvider = object : () -> CaptureId {
                private var nextId = 0

                override fun invoke(): CaptureId = CaptureId("v3-capture-${++nextId}")
            },
            capturedAtProvider = { Instant.parse("2026-09-03T12:00:00Z") },
        )

        val textResult = submission.submit("Texto v3") as CaptureSubmissionResult.Saved
        val voiceResult = submission.submitVoice("voz original") as CaptureSubmissionResult.Saved
        val correction = RoomCaptureTranscriptCorrector(database.captureDao()).correct(
            voiceResult.capture.id,
            "voz corregida",
        ) as CaptureTranscriptCorrectionResult.Saved

        assertEquals(CaptureInput.Text("Texto v3"), textResult.capture.originalInput)
        assertEquals(CaptureInput.Voice("voz original"), voiceResult.capture.originalInput)
        assertEquals("voz original", (correction.capture.originalInput as CaptureInput.Voice).originalTranscript)
        assertEquals("voz corregida", correction.capture.transcriptCorrection)
        assertEquals("voz corregida", correction.capture.effectiveTranscript)
        assertEquals(2, database.captureDao().getRecent(50).size)
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun openProductionDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private suspend fun assertCaptureRow(row: V2CaptureRow) {
        val entity = database.captureDao().getById(row.id)
        assertNotNull("Capture ${row.id} must survive migration", entity)
        assertEquals(row.id, entity?.id)
        assertEquals(row.kind, entity?.kind)
        assertEquals(row.originalText, entity?.originalText)
        assertEquals(row.capturedAtEpochMillis, entity?.capturedAtEpochMillis)
        assertEquals(row.correctedTranscript, entity?.correctedTranscript)
        val capture = entity?.toDomain()
        assertEquals(row.kind, capture?.kind?.name)
        assertEquals(row.originalText, capture?.originalTextValue())
        assertEquals(row.correctedTranscript, capture?.transcriptCorrection)
    }

    private fun com.edu.quickaside.domain.capture.Capture.originalTextValue(): String =
        when (val input = originalInput) {
            is CaptureInput.Text -> input.originalText
            is CaptureInput.Voice -> input.originalTranscript
        }

    private fun createVersion2Fixture(rows: List<V2CaptureRow>) {
        val path = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }.absolutePath
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execute(
                "CREATE TABLE IF NOT EXISTS captures " +
                    "(id TEXT NOT NULL, kind TEXT NOT NULL, original_text TEXT NOT NULL, " +
                    "captured_at_epoch_millis INTEGER NOT NULL, corrected_transcript TEXT, " +
                    "PRIMARY KEY(id))",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY,identity_hash TEXT)",
            )
            connection.execute(
                "INSERT OR REPLACE INTO room_master_table " +
                    "(id,identity_hash) VALUES(42, '$V2_IDENTITY_HASH')",
            )
            connection.execute("PRAGMA user_version = 2")
            rows.forEach { row -> connection.insertV2Row(row) }
        }
    }

    private fun SQLiteConnection.insertV2Row(row: V2CaptureRow) {
        prepare(
            "INSERT INTO captures " +
                "(id, kind, original_text, captured_at_epoch_millis, corrected_transcript) " +
                "VALUES (?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.bindText(1, row.id)
            statement.bindText(2, row.kind)
            statement.bindText(3, row.originalText)
            statement.bindLong(4, row.capturedAtEpochMillis)
            if (row.correctedTranscript == null) {
                statement.bindNull(5)
            } else {
                statement.bindText(5, row.correctedTranscript)
            }
            statement.step()
        }
    }

    private fun readUserVersion(): Long = BundledSQLiteDriver().open(
        context.getDatabasePath(databaseName).absolutePath,
    ).use { connection ->
        connection.prepare("PRAGMA user_version").use { statement ->
            assertTrue(statement.step())
            statement.getLong(0)
        }
    }

    private fun readTables(): List<String> = BundledSQLiteDriver().open(
        context.getDatabasePath(databaseName).absolutePath,
    ).use { connection ->
        connection.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "ORDER BY name",
        ).use { statement ->
            buildList {
                while (statement.step()) {
                    add(statement.getText(0))
                }
            }
        }
    }

    private fun readColumns(tableName: String): List<String> = BundledSQLiteDriver().open(
        context.getDatabasePath(databaseName).absolutePath,
    ).use { connection ->
        connection.prepare("PRAGMA table_info(`$tableName`)").use { statement ->
            buildList {
                while (statement.step()) {
                    add(statement.getText(1))
                }
            }
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private class QueueListIdProvider(
        sessionIds: List<String> = emptyList(),
        itemIds: List<String> = emptyList(),
    ) : ListIdProvider {
        private val sessionIds = ArrayDeque(sessionIds)
        private val itemIds = ArrayDeque(itemIds)

        override fun nextSessionId(): ListSessionId = ListSessionId(sessionIds.removeFirst())

        override fun nextItemId(): ListItemId = ListItemId(itemIds.removeFirst())
    }

    private class QueueListClock(times: List<Instant>) : ListClock {
        private val times = ArrayDeque(times)

        constructor(vararg times: Instant) : this(times.toList())

        override fun now(): Instant = times.removeFirst()
    }

    private class ConcurrentListIdProvider : ListIdProvider {
        private val nextSession = AtomicInteger()
        private val nextItem = AtomicInteger()

        override fun nextSessionId(): ListSessionId =
            ListSessionId("concurrent-session-${nextSession.incrementAndGet()}")

        override fun nextItemId(): ListItemId =
            ListItemId("concurrent-item-${nextItem.incrementAndGet()}")
    }

    private data class V2CaptureRow(
        val id: String,
        val kind: String,
        val originalText: String,
        val capturedAtEpochMillis: Long,
        val correctedTranscript: String?,
    )

    private companion object {
        const val V2_IDENTITY_HASH = "9e3a42d3422358073becf06e272aec90"
    }
}

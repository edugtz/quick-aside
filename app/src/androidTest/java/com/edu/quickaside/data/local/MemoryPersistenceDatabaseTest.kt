package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureSubmissionResult
import com.edu.quickaside.application.capture.CaptureTranscriptCorrectionResult
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListIdProvider
import com.edu.quickaside.application.lists.SessionFinishResult
import com.edu.quickaside.application.lists.SessionStartResult
import com.edu.quickaside.application.memory.MemoryClock
import com.edu.quickaside.application.memory.MemoryIdProvider
import com.edu.quickaside.application.memory.NoteCreationResult
import com.edu.quickaside.application.memory.StructuredLogCreationResult
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryPersistenceDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-013-memory-${UUID.randomUUID()}.db"
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
    fun notePersistsExactTextCreatedAtAndSourceCapture() = runBlocking {
        openFreshDatabase()
        val source = capture("note-source", CaptureInput.Text("captured note"))
        database.captureDao().insert(source.toEntity())
        val store = memoryStore(
            noteIds = listOf("note-1"),
            times = listOf(FIXED_NOTE_TIME),
        )

        val result = store.createNote("  Llamar al taller  ", source.id)

        val saved = result as NoteCreationResult.Saved
        assertEquals("  Llamar al taller  ", saved.note.text)
        assertEquals(FIXED_NOTE_TIME, saved.note.createdAt)
        assertEquals(source.id, saved.note.sourceCaptureId)
        assertEquals(saved.note, store.getNote(saved.note.id))
        assertEquals(saved.note, database.noteDao().getById("note-1")?.toDomain())
        assertEquals(source, database.captureDao().getById(source.id.value)?.toDomain())
    }

    @Test
    fun blankNoteIsRejectedWithoutCreatingARow() = runBlocking {
        openFreshDatabase()
        val result = memoryStore().createNote(" \t\n ")

        assertEquals(NoteCreationResult.BlankText, result)
        assertTrue(database.noteDao().getRecent(50).isEmpty())
    }

    @Test
    fun noteWithoutSourceCaptureIsSaved() = runBlocking {
        openFreshDatabase()
        val result = memoryStore(noteIds = listOf("note-no-source")).createNote("Sin captura")

        val saved = result as NoteCreationResult.Saved
        assertNull(saved.note.sourceCaptureId)
        assertEquals(saved.note, database.noteDao().getById("note-no-source")?.toDomain())
    }

    @Test
    fun noteWithMissingSourceCaptureReturnsDeterministicFailureAndCreatesNothing() = runBlocking {
        openFreshDatabase()
        val result = memoryStore(noteIds = listOf("must-not-be-used")).createNote(
            text = "No guardar",
            sourceCaptureId = CaptureId("missing-capture"),
        )

        assertEquals(NoteCreationResult.MissingSourceCapture, result)
        assertTrue(database.noteDao().getRecent(50).isEmpty())
    }

    @Test
    fun recentNotesAreNewestFirstWithIdTieBreak() = runBlocking {
        openFreshDatabase()
        val tie = Instant.parse("2026-09-05T12:00:00Z")
        val store = memoryStore(
            noteIds = listOf("older", "tie-a", "newest", "tie-b"),
            times = listOf(
                Instant.parse("2026-09-04T12:00:00Z"),
                tie,
                Instant.parse("2026-09-06T12:00:00Z"),
                tie,
            ),
        )
        listOf("older", "tie-a", "newest", "tie-b").forEach { id ->
            store.createNote(id)
        }

        assertEquals(
            listOf("newest", "tie-b", "tie-a", "older"),
            store.readRecentNotes().map { it.id.value },
        )
    }

    @Test
    fun structuredLogRequiresAtLeastOneField() = runBlocking {
        openFreshDatabase()

        assertEquals(
            StructuredLogCreationResult.EmptyFields,
            memoryStore().createStructuredLog(emptyMap()),
        )
        assertTrue(database.structuredLogDao().getRecent(50).isEmpty())
    }

    @Test
    fun blankStructuredLogFieldKeyIsRejected() = runBlocking {
        openFreshDatabase()

        assertEquals(
            StructuredLogCreationResult.BlankFieldKey,
            memoryStore().createStructuredLog(mapOf(" \t" to "value")),
        )
        assertTrue(database.structuredLogDao().getRecent(50).isEmpty())
    }

    @Test
    fun blankStructuredLogFieldValueIsRejected() = runBlocking {
        openFreshDatabase()

        assertEquals(
            StructuredLogCreationResult.BlankFieldValue,
            memoryStore().createStructuredLog(mapOf("key" to " \t")),
        )
        assertTrue(database.structuredLogDao().getRecent(50).isEmpty())
    }

    @Test
    fun structuredLogPreservesExactFieldsAndUsesDeterministicFieldReadOrder() = runBlocking {
        openFreshDatabase()
        val fields = linkedMapOf(
            "weight" to " 210 lbs ",
            "exercise" to "press inclinado",
        )
        val store = memoryStore(
            logIds = listOf("log-exact"),
            times = listOf(FIXED_LOG_TIME),
        )

        val saved = store.createStructuredLog(fields) as StructuredLogCreationResult.Saved

        assertEquals(fields, saved.log.fields)
        assertEquals(FIXED_LOG_TIME, saved.log.createdAt)
        assertEquals(fields, store.getStructuredLog(saved.log.id)?.fields)
        assertEquals(
            listOf("exercise", "weight"),
            database.structuredLogFieldDao()
                .getByStructuredLogId("log-exact")
                .map(StructuredLogFieldEntity::fieldKey),
        )
    }

    @Test
    fun structuredLogWithoutSourceCaptureIsSaved() = runBlocking {
        openFreshDatabase()
        val result = memoryStore(logIds = listOf("log-no-source")).createStructuredLog(
            mapOf("key" to "value"),
        )

        val saved = result as StructuredLogCreationResult.Saved
        assertNull(saved.log.sourceCaptureId)
        assertEquals(saved.log, database.structuredLogDao().getById("log-no-source")?.let { entity ->
            entity.toDomain(database.structuredLogFieldDao().getByStructuredLogId(entity.id))
        })
    }

    @Test
    fun structuredLogWithExistingSourceCaptureIsSaved() = runBlocking {
        openFreshDatabase()
        val source = capture("log-source", CaptureInput.Voice("registro original"))
        database.captureDao().insert(source.toEntity())

        val result = memoryStore(logIds = listOf("log-with-source")).createStructuredLog(
            fields = mapOf("key" to "value"),
            sourceCaptureId = source.id,
        )

        val saved = result as StructuredLogCreationResult.Saved
        assertEquals(source.id, saved.log.sourceCaptureId)
        assertEquals(saved.log, memoryStore().getStructuredLog(saved.log.id))
        assertEquals(source, database.captureDao().getById(source.id.value)?.toDomain())
    }

    @Test
    fun structuredLogWithMissingSourceCaptureReturnsDeterministicFailureAndCreatesNothing() = runBlocking {
        openFreshDatabase()
        val result = memoryStore(logIds = listOf("must-not-be-used")).createStructuredLog(
            fields = mapOf("key" to "value"),
            sourceCaptureId = CaptureId("missing-capture"),
        )

        assertEquals(StructuredLogCreationResult.MissingSourceCapture, result)
        assertTrue(database.structuredLogDao().getRecent(50).isEmpty())
        assertTrue(database.structuredLogFieldDao().getByStructuredLogId("must-not-be-used").isEmpty())
    }

    @Test
    fun structuredLogParentAndFieldsCreationIsAtomic() = runBlocking {
        openFreshDatabase()

        // Force Room to open/create the physical v4 database before
        // installing the raw SQLite trigger.
        assertTrue(database.structuredLogDao().getRecent(1).isEmpty())

        database.close()
        addFailingStructuredLogFieldTrigger()
        openProductionDatabase()

        val result = memoryStore(logIds = listOf("atomic-failure")).createStructuredLog(
            mapOf("field" to "force-field-insert-failure"),
        )

        assertTrue(result is StructuredLogCreationResult.Failed)
        assertNull(database.structuredLogDao().getById("atomic-failure"))
        assertTrue(database.structuredLogFieldDao().getByStructuredLogId("atomic-failure").isEmpty())
    }

    @Test
    fun structuredLogFieldsSurviveCloseAndReopen() = runBlocking {
        openFreshDatabase()
        val store = memoryStore(logIds = listOf("reopen-log"))
        val saved = store.createStructuredLog(
            linkedMapOf("b" to "  segundo  ", "a" to "primero"),
        ) as StructuredLogCreationResult.Saved
        database.close()
        openProductionDatabase()

        assertEquals(saved.log, RoomMemoryStore(database).getStructuredLog(saved.log.id))
        assertEquals(
            listOf("a", "b"),
            database.structuredLogFieldDao().getByStructuredLogId("reopen-log")
                .map(StructuredLogFieldEntity::fieldKey),
        )
    }

    @Test
    fun recentStructuredLogsAreNewestFirstWithIdTieBreak() = runBlocking {
        openFreshDatabase()
        val tie = Instant.parse("2026-09-05T12:00:00Z")
        val store = memoryStore(
            logIds = listOf("older", "tie-a", "newest", "tie-b"),
            times = listOf(
                Instant.parse("2026-09-04T12:00:00Z"),
                tie,
                Instant.parse("2026-09-06T12:00:00Z"),
                tie,
            ),
        )
        listOf("older", "tie-a", "newest", "tie-b").forEach { id ->
            store.createStructuredLog(mapOf("id" to id))
        }

        assertEquals(
            listOf("newest", "tie-b", "tie-a", "older"),
            store.readRecentStructuredLogs().map { it.id.value },
        )
    }

    @Test
    fun realV3DatabaseMigratesOldCaptureAndListDataAndLeavesMemoryEmpty() = runBlocking {
        createVersion3Fixture()
        openProductionDatabase()

        assertCaptureRow(LEGACY_TEXT_CAPTURE)
        assertCaptureRow(LEGACY_CORRECTED_VOICE_CAPTURE)
        assertEquals(
            BuiltInListDefinitions.ALL,
            RoomListStore(database).readBuiltInDefinitions(),
        )
        val mandadoHistory = RoomListStore(database)
            .readRecentSessions(BuiltInListDefinitions.MANDADO.id)
            .single()
        assertEquals("legacy-mandado-session", mandadoHistory.session.id.value)
        assertEquals(listOf("  Jabón legado  "), mandadoHistory.items.map { it.text })
        assertTrue(mandadoHistory.items.single().isCompleted)
        assertEquals(
            listOf("  Leche compras  "),
            RoomListStore(database)
                .readCurrentItems(BuiltInListDefinitions.COMPRAS.id)
                .map { it.text },
        )
        assertTrue(RoomMemoryStore(database).readRecentNotes().isEmpty())
        assertTrue(RoomMemoryStore(database).readRecentStructuredLogs().isEmpty())

        database.close()
        assertEquals(4L, readUserVersion())
        assertTrue(
            readTables().containsAll(
                listOf(
                    "captures",
                    "list_definitions",
                    "list_sessions",
                    "list_items",
                    "notes",
                    "structured_logs",
                    "structured_log_fields",
                ),
            ),
        )

        openProductionDatabase()
        assertCaptureRow(LEGACY_TEXT_CAPTURE)
        assertCaptureRow(LEGACY_CORRECTED_VOICE_CAPTURE)
        assertEquals(
            listOf("legacy-mandado-session"),
            RoomListStore(database)
                .readRecentSessions(BuiltInListDefinitions.MANDADO.id)
                .map { it.session.id.value },
        )
        assertEquals(
            listOf("  Leche compras  "),
            RoomListStore(database)
                .readCurrentItems(BuiltInListDefinitions.COMPRAS.id)
                .map { it.text },
        )
        assertTrue(RoomMemoryStore(database).readRecentNotes().isEmpty())
        assertTrue(RoomMemoryStore(database).readRecentStructuredLogs().isEmpty())
    }

    @Test
    fun captureReadWriteAndTranscriptCorrectionStillWorkOnV4() = runBlocking {
        openFreshDatabase()
        val submission = CaptureSubmission(
            writer = RoomCaptureWriter(database),
            idProvider = object : () -> CaptureId {
                private var nextId = 0

                override fun invoke(): CaptureId = CaptureId("v4-capture-${++nextId}")
            },
            capturedAtProvider = { FIXED_CAPTURE_TIME },
        )

        val text = submission.submit("Texto v4") as CaptureSubmissionResult.Saved
        val voice = submission.submitVoice("voz original") as CaptureSubmissionResult.Saved
        val corrected = RoomCaptureTranscriptCorrector(database.captureDao()).correct(
            voice.capture.id,
            "voz corregida",
        ) as CaptureTranscriptCorrectionResult.Saved

        assertEquals(CaptureInput.Text("Texto v4"), text.capture.originalInput)
        assertEquals("voz original", (corrected.capture.originalInput as CaptureInput.Voice).originalTranscript)
        assertEquals("voz corregida", corrected.capture.effectiveTranscript)
        assertEquals(
            setOf("v4-capture-1", "v4-capture-2"),
            RoomCaptureReader(database).readRecent().map { it.id.value }.toSet(),
        )
    }

    @Test
    fun mandadoLifecycleHistoryAndComprasContinuousItemsStillWorkOnV4() = runBlocking {
        openFreshDatabase()
        val store = RoomListStore(
            database = database,
            idProvider = RegressionListIdProvider(),
            clock = ListClock { FIXED_CAPTURE_TIME },
        )

        val session = (store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created).session
        val mandadoItem = store.addItem(
            BuiltInListDefinitions.MANDADO.id,
            "Mandado v4",
            session.id,
        ) as AddListItemResult.Saved
        val comprasItem = store.addItem(BuiltInListDefinitions.COMPRAS.id, "Compras v4")
            as AddListItemResult.Saved
        assertTrue(store.finishSession(session.id) is SessionFinishResult.Finished)

        assertEquals(
            listOf(mandadoItem.item.id),
            store.readSession(session.id)?.items?.map { it.id },
        )
        assertEquals(
            listOf(comprasItem.item.id),
            store.readCurrentItems(BuiltInListDefinitions.COMPRAS.id).map { it.id },
        )
        assertEquals(null, comprasItem.item.listSessionId)
        assertEquals(SessionFinishResult.NoActiveSession, store.finishActiveSession(BuiltInListDefinitions.MANDADO.id))
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun openProductionDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun memoryStore(
        noteIds: List<String> = emptyList(),
        logIds: List<String> = emptyList(),
        times: List<Instant> = emptyList(),
    ): RoomMemoryStore = RoomMemoryStore(
        database = database,
        idProvider = QueueMemoryIdProvider(noteIds, logIds),
        clock = QueueMemoryClock(times),
    )

    private fun capture(id: String, input: CaptureInput): Capture = Capture(
        id = CaptureId(id),
        originalInput = input,
        capturedAt = FIXED_CAPTURE_TIME,
    )

    private suspend fun assertCaptureRow(row: LegacyCaptureRow) {
        val entity = database.captureDao().getById(row.id)
        assertNotNull("Capture ${row.id} must survive migration", entity)
        assertEquals(row.id, entity?.id)
        assertEquals(row.kind, entity?.kind)
        assertEquals(row.originalText, entity?.originalText)
        assertEquals(row.capturedAtEpochMillis, entity?.capturedAtEpochMillis)
        assertEquals(row.correctedTranscript, entity?.correctedTranscript)
    }

    private fun createVersion3Fixture() {
        val path = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }.absolutePath
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execute(
                "CREATE TABLE IF NOT EXISTS captures " +
                    "(id TEXT NOT NULL, kind TEXT NOT NULL, original_text TEXT NOT NULL, " +
                    "captured_at_epoch_millis INTEGER NOT NULL, corrected_transcript TEXT, " +
                    "PRIMARY KEY(id))",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS list_definitions " +
                    "(id TEXT NOT NULL, name TEXT NOT NULL, behavior TEXT NOT NULL, PRIMARY KEY(id))",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS list_sessions " +
                    "(id TEXT NOT NULL, list_definition_id TEXT NOT NULL, " +
                    "started_at_epoch_millis INTEGER NOT NULL, ended_at_epoch_millis INTEGER, " +
                    "PRIMARY KEY(id), FOREIGN KEY(list_definition_id) REFERENCES list_definitions(id) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION)",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS list_items " +
                    "(id TEXT NOT NULL, list_definition_id TEXT NOT NULL, list_session_id TEXT, " +
                    "text TEXT NOT NULL, is_completed INTEGER NOT NULL, " +
                    "created_at_epoch_millis INTEGER NOT NULL, PRIMARY KEY(id), " +
                    "FOREIGN KEY(list_definition_id) REFERENCES list_definitions(id) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                    "FOREIGN KEY(list_session_id) REFERENCES list_sessions(id) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION)",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS index_list_sessions_list_definition_id " +
                    "ON list_sessions (list_definition_id)",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS index_list_items_list_definition_id " +
                    "ON list_items (list_definition_id)",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS index_list_items_list_session_id " +
                    "ON list_items (list_session_id)",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY,identity_hash TEXT)",
            )
            connection.execute(
                "INSERT OR REPLACE INTO room_master_table " +
                    "(id,identity_hash) VALUES(42, '$V3_IDENTITY_HASH')",
            )
            connection.execute("PRAGMA user_version = 3")
            connection.insertCapture(LEGACY_TEXT_CAPTURE)
            connection.insertCapture(LEGACY_CORRECTED_VOICE_CAPTURE)
            connection.execute("INSERT INTO list_definitions VALUES ('mandado', 'Mandado', 'SESSION_BASED')")
            connection.execute("INSERT INTO list_definitions VALUES ('compras', 'Compras', 'CONTINUOUS')")
            connection.execute(
                "INSERT INTO list_sessions VALUES " +
                    "('legacy-mandado-session', 'mandado', 1788436800000, 1788436860000)",
            )
            connection.execute(
                "INSERT INTO list_items VALUES " +
                    "('legacy-mandado-item', 'mandado', 'legacy-mandado-session', " +
                    "'  Jabón legado  ', 1, 1788436810000)",
            )
            connection.execute(
                "INSERT INTO list_items VALUES " +
                    "('legacy-compras-item', 'compras', NULL, '  Leche compras  ', 0, 1788436820000)",
            )
        }
    }

    private fun SQLiteConnection.insertCapture(row: LegacyCaptureRow) {
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

    private fun addFailingStructuredLogFieldTrigger() {
        val path = context.getDatabasePath(databaseName).absolutePath
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_structured_log_field_insert " +
                    "BEFORE INSERT ON structured_log_fields " +
                    "WHEN NEW.field_value = 'force-field-insert-failure' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced field insert failure'); END",
            )
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

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private class QueueMemoryIdProvider(
        noteIds: List<String>,
        logIds: List<String>,
    ) : MemoryIdProvider {
        private val noteIds = ArrayDeque(noteIds)
        private val logIds = ArrayDeque(logIds)

        override fun nextNoteId(): NoteId = NoteId(noteIds.removeFirst())

        override fun nextStructuredLogId(): StructuredLogId = StructuredLogId(logIds.removeFirst())
    }

    private class QueueMemoryClock(times: List<Instant>) : MemoryClock {
        private val times = ArrayDeque(times)

        override fun now(): Instant = if (times.isEmpty()) FIXED_NOTE_TIME else times.removeFirst()
    }

    private class RegressionListIdProvider : ListIdProvider {
        override fun nextSessionId(): ListSessionId = ListSessionId("v4-session")

        private var nextItem = 0

        override fun nextItemId(): ListItemId = ListItemId("v4-item-${++nextItem}")
    }

    private data class LegacyCaptureRow(
        val id: String,
        val kind: String,
        val originalText: String,
        val capturedAtEpochMillis: Long,
        val correctedTranscript: String?,
    )

    private companion object {
        const val V3_IDENTITY_HASH = "8d6a0299578b7b099a29a8ede03881bc"
        val FIXED_CAPTURE_TIME = Instant.parse("2026-09-05T12:00:00Z")
        val FIXED_NOTE_TIME = Instant.parse("2026-09-05T12:01:00Z")
        val FIXED_LOG_TIME = Instant.parse("2026-09-05T12:02:00Z")
        val LEGACY_TEXT_CAPTURE = LegacyCaptureRow(
            id = "legacy-text",
            kind = "TEXT",
            originalText = "  Texto legado  ",
            capturedAtEpochMillis = 1788436800123,
            correctedTranscript = null,
        )
        val LEGACY_CORRECTED_VOICE_CAPTURE = LegacyCaptureRow(
            id = "legacy-corrected-voice",
            kind = "VOICE",
            originalText = "comprar leche manana",
            capturedAtEpochMillis = 1788436800456,
            correctedTranscript = "Comprar leche mañana",
        )
    }
}

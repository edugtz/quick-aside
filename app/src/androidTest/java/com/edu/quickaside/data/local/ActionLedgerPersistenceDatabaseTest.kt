package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.actions.ActionLedgerMarkUndoneResult
import com.edu.quickaside.application.actions.ActionLedgerMutationInput
import com.edu.quickaside.application.actions.ActionLedgerMutationValidationReason
import com.edu.quickaside.application.actions.ActionLedgerRecordResult
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
class ActionLedgerPersistenceDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-018-action-ledger-${UUID.randomUUID()}.db"
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
    fun singleAndMultiMutationBatchesRoundTripExactValuesAndOrder() = runBlocking {
        openFreshDatabase()
        val source = capture(
            id = "ledger-source",
            input = CaptureInput.Voice("  voz original  "),
        )
        database.captureDao().insert(source.toEntity())
        val store = ledgerStore(
            entryIds = listOf("entry-single", "entry-batch"),
            times = listOf(
                Instant.parse("2026-09-07T10:00:00Z"),
                Instant.parse("2026-09-07T10:01:00Z"),
            ),
        )

        val single = store.record(
            mutations = listOf(
                mutation(
                    operation = ActionLedgerOperation.CREATE,
                    targetType = "list_item",
                    targetId = "item-single",
                    payloadVersion = 1,
                    afterState = "  {opaque: true}  ",
                ),
            ),
            sourceCaptureId = source.id,
        ) as ActionLedgerRecordResult.Saved
        assertEquals(source.id, single.entry.sourceCaptureId)
        assertEquals(null, single.entry.undoneAt)
        assertEquals(single.entry, store.getEntry(single.entry.id))

        val batch = store.record(
            mutations = listOf(
                mutation(
                    operation = ActionLedgerOperation.CREATE,
                    targetType = "list_item",
                    targetId = "item-1",
                    payloadVersion = 1,
                    afterState = "after-1",
                ),
                mutation(
                    operation = ActionLedgerOperation.UPDATE,
                    targetType = "list_item",
                    targetId = "item-2",
                    payloadVersion = 2,
                    beforeState = "before-2",
                    afterState = null,
                ),
                mutation(
                    operation = ActionLedgerOperation.DELETE,
                    targetType = "future_target",
                    targetId = "target-3",
                    payloadVersion = 3,
                    beforeState = "  before-3  ",
                    afterState = "",
                ),
            ),
        ) as ActionLedgerRecordResult.Saved

        assertEquals(
            listOf("item-1", "item-2", "target-3"),
            batch.entry.mutations.map { it.targetId },
        )
        assertEquals(
            listOf(0, 1, 2),
            database.actionLedgerMutationDao().getByEntryId("entry-batch")
                .map(ActionLedgerMutationEntity::position),
        )
        assertEquals(
            listOf("CREATE", "UPDATE", "DELETE"),
            database.actionLedgerMutationDao().getByEntryId("entry-batch")
                .map(ActionLedgerMutationEntity::operation),
        )
        assertEquals("  before-3  ", batch.entry.mutations[2].beforeState)
        assertEquals("", batch.entry.mutations[2].afterState)
        assertEquals(
            listOf("entry-batch", "entry-single"),
            store.readRecentEntries().map { it.id.value },
        )
    }

    @Test
    fun readsRestorePositionOrderWhenChildRowsWereInsertedOutOfOrder() = runBlocking {
        openFreshDatabase()
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = "manual-order-entry",
                occurredAtEpochMillis = Instant.parse("2026-09-07T10:30:00Z").toEpochMilli(),
            ),
        )
        database.actionLedgerMutationDao().insertAll(
            listOf(
                ActionLedgerMutationEntity(
                    actionLedgerEntryId = "manual-order-entry",
                    position = 2,
                    operation = "DELETE",
                    targetType = "target",
                    targetId = "position-2",
                    payloadVersion = 1,
                ),
                ActionLedgerMutationEntity(
                    actionLedgerEntryId = "manual-order-entry",
                    position = 0,
                    operation = "CREATE",
                    targetType = "target",
                    targetId = "position-0",
                    payloadVersion = 1,
                ),
                ActionLedgerMutationEntity(
                    actionLedgerEntryId = "manual-order-entry",
                    position = 1,
                    operation = "UPDATE",
                    targetType = "target",
                    targetId = "position-1",
                    payloadVersion = 1,
                ),
            ),
        )

        val restored = RoomActionLedgerStore(database)
            .getEntry(ActionLedgerEntryId("manual-order-entry"))

        assertEquals(
            listOf("position-0", "position-1", "position-2"),
            restored?.mutations?.map { it.targetId },
        )
    }

    @Test
    fun missingSourceCaptureCreatesNoParentOrChildRows() = runBlocking {
        openFreshDatabase()
        val store = ledgerStore()

        val result = store.record(
            mutations = listOf(mutation(targetId = "not-saved")),
            sourceCaptureId = CaptureId("missing-capture"),
        )

        assertEquals(ActionLedgerRecordResult.MissingSourceCapture, result)
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("not-saved").isEmpty())
    }

    @Test
    fun recentAndLatestUndoableReadsUseDeterministicOrdering() = runBlocking {
        openFreshDatabase()
        val tie = Instant.parse("2026-09-07T11:00:00Z")
        val store = ledgerStore(
            entryIds = listOf("older", "tie-a", "tie-z", "newest"),
            times = listOf(
                Instant.parse("2026-09-07T10:00:00Z"),
                tie,
                tie,
                Instant.parse("2026-09-07T12:00:00Z"),
                Instant.parse("2026-09-07T13:00:00Z"),
            ),
        )
        listOf("older", "tie-a", "tie-z", "newest").forEach { id ->
            store.record(listOf(mutation(targetId = id)))
        }

        assertEquals(
            listOf("newest", "tie-z", "tie-a", "older"),
            store.readRecentEntries().map { it.id.value },
        )
        assertEquals("newest", store.readLatestUndoable()?.id?.value)
        assertTrue(store.markUndone(ActionLedgerEntryId("newest")) is ActionLedgerMarkUndoneResult.MarkedUndone)
        assertEquals("tie-z", store.readLatestUndoable()?.id?.value)
        assertEquals(
            listOf("newest", "tie-z", "tie-a", "older"),
            store.readRecentEntries().map { it.id.value },
        )
    }

    @Test
    fun markUndoneIsOneWayAndSurvivesCloseAndReopen() = runBlocking {
        openFreshDatabase()
        val occurredAt = Instant.parse("2026-09-07T14:00:00Z")
        val undoneAt = Instant.parse("2026-09-07T14:01:00Z")
        val store = ledgerStore(
            entryIds = listOf("reopen-entry"),
            times = listOf(occurredAt, undoneAt),
        )
        assertEquals(
            ActionLedgerMarkUndoneResult.MissingEntry,
            store.markUndone(ActionLedgerEntryId("missing-entry")),
        )
        val saved = store.record(listOf(mutation(targetId = "reopen-target")))
            as ActionLedgerRecordResult.Saved

        val marked = store.markUndone(saved.entry.id) as ActionLedgerMarkUndoneResult.MarkedUndone
        assertEquals(undoneAt, marked.entry.undoneAt)
        assertEquals(ActionLedgerMarkUndoneResult.AlreadyUndone, store.markUndone(saved.entry.id))

        database.close()
        openProductionDatabase()
        val reopened = RoomActionLedgerStore(database).getEntry(saved.entry.id)
        assertNotNull(reopened)
        assertEquals(undoneAt, reopened?.undoneAt)
        assertEquals(ActionLedgerMarkUndoneResult.AlreadyUndone, RoomActionLedgerStore(database).markUndone(saved.entry.id))
    }

    @Test
    fun invalidBatchesAreRejectedBeforeProvidersAndCreateNoRows() = runBlocking {
        openFreshDatabase()
        val store = ledgerStore()

        assertEquals(ActionLedgerRecordResult.EmptyMutationBatch, store.record(emptyList()))
        assertEquals(
            ActionLedgerRecordResult.InvalidMutation(
                position = 1,
                reason = ActionLedgerMutationValidationReason.BLANK_TARGET_TYPE,
            ),
            store.record(
                listOf(
                    mutation(targetId = "valid-but-not-written"),
                    mutation(targetType = " \t\n ", targetId = "invalid-type"),
                ),
            ),
        )
        assertEquals(
            ActionLedgerRecordResult.InvalidMutation(
                position = 0,
                reason = ActionLedgerMutationValidationReason.BLANK_TARGET_ID,
            ),
            store.record(listOf(mutation(targetId = " \t\n "))),
        )
        assertEquals(
            ActionLedgerRecordResult.InvalidMutation(
                position = 0,
                reason = ActionLedgerMutationValidationReason.NON_POSITIVE_PAYLOAD_VERSION,
            ),
            store.record(listOf(mutation(payloadVersion = 0))),
        )
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("valid-but-not-written").isEmpty())
    }

    @Test
    fun childInsertFailureRollsBackParentAndAllChildren() = runBlocking {
        openFreshDatabase()
        assertTrue(database.actionLedgerEntryDao().getRecent(1).isEmpty())
        database.close()
        addFailingMutationTrigger()
        openProductionDatabase()
        val store = ledgerStore(
            entryIds = listOf("atomic-entry"),
            times = listOf(Instant.parse("2026-09-07T15:00:00Z")),
        )

        val result = store.record(
            listOf(
                mutation(targetId = "first-child"),
                mutation(targetId = "force-child-insert-failure"),
            ),
        )

        assertTrue(result is ActionLedgerRecordResult.Failed)
        assertNull(database.actionLedgerEntryDao().getById("atomic-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("atomic-entry").isEmpty())
    }

    @Test
    fun closedDatabaseReturnsFailedForWrite() = runBlocking {
        openFreshDatabase()
        val store = ledgerStore(
            entryIds = listOf("closed-entry"),
            times = listOf(Instant.parse("2026-09-07T16:00:00Z")),
        )
        database.close()

        val result = store.record(listOf(mutation(targetId = "closed-target")))

        assertTrue(result is ActionLedgerRecordResult.Failed)
    }

    @Test
    fun cancellationIsRethrown() = runBlocking {
        openFreshDatabase()
        val store = RoomActionLedgerStore(
            database = database,
            idProvider = QueueActionLedgerIdProvider(listOf("cancelled-entry")),
            clock = ActionLedgerClock { throw CancellationException("forced cancellation") },
        )

        var caught: CancellationException? = null
        try {
            store.record(listOf(mutation(targetId = "cancelled-target")))
        } catch (cancellation: CancellationException) {
            caught = cancellation
        }

        assertNotNull(caught)
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
    }

    @Test
    fun realV4FixtureMigratesWithoutChangingLegacySchemaOrValues() = runBlocking {
        createVersion4Fixture()
        val schemaBefore = readLegacySchemaObjects()

        openProductionDatabase()

        val text = database.captureDao().getById(LEGACY_TEXT_CAPTURE.id)?.toDomain()
        assertEquals("  Texto legado  ", (text?.originalInput as CaptureInput.Text).originalText)
        val voice = database.captureDao().getById(LEGACY_CORRECTED_VOICE_CAPTURE.id)?.toDomain()
        assertEquals("comprar leche manana", (voice?.originalInput as CaptureInput.Voice).originalTranscript)
        assertEquals("Comprar leche mañana", voice?.transcriptCorrection)
        assertEquals(BuiltInListDefinitions.ALL, RoomListStore(database).readBuiltInDefinitions())
        assertEquals(
            "  Jabón legado  ",
            database.listItemDao().getById("legacy-mandado-item")?.toDomain()?.text,
        )
        assertEquals(
            true,
            database.listItemDao().getById("legacy-mandado-item")?.toDomain()?.isCompleted,
        )
        assertEquals(
            ListSession(
                id = ListSessionId("legacy-mandado-session"),
                listDefinitionId = BuiltInListDefinitions.MANDADO.id,
                startedAt = Instant.ofEpochMilli(1788436800000),
                endedAt = Instant.ofEpochMilli(1788436860000),
            ),
            database.listSessionDao().getById("legacy-mandado-session")?.toDomain(),
        )
        assertEquals(
            ListItem(
                id = ListItemId("legacy-mandado-item"),
                listDefinitionId = BuiltInListDefinitions.MANDADO.id,
                listSessionId = ListSessionId("legacy-mandado-session"),
                text = "  Jabón legado  ",
                isCompleted = true,
                createdAt = Instant.ofEpochMilli(1788436810000),
            ),
            database.listItemDao().getById("legacy-mandado-item")?.toDomain(),
        )
        assertEquals(
            ListItem(
                id = ListItemId("legacy-compras-item"),
                listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
                text = "  Leche compras  ",
                isCompleted = false,
                createdAt = Instant.ofEpochMilli(1788436820000),
            ),
            database.listItemDao().getById("legacy-compras-item")?.toDomain(),
        )
        assertEquals(
            "  Nota legada  ",
            database.noteDao().getById("legacy-note")?.toDomain()?.text,
        )
        assertEquals(
            CaptureId(LEGACY_TEXT_CAPTURE.id),
            database.noteDao().getById("legacy-note")?.toDomain()?.sourceCaptureId,
        )
        assertEquals(
            Note(
                id = NoteId("legacy-note"),
                text = "  Nota legada  ",
                sourceCaptureId = CaptureId(LEGACY_TEXT_CAPTURE.id),
                createdAt = Instant.ofEpochMilli(1788436830000),
            ),
            database.noteDao().getById("legacy-note")?.toDomain(),
        )
        assertEquals(
            linkedMapOf("estado" to "  listo  ", "cantidad" to "3"),
            database.structuredLogDao().getById("legacy-log")?.let { log ->
                log.toDomain(database.structuredLogFieldDao().getByStructuredLogId(log.id)).fields
            },
        )
        assertEquals(
            StructuredLog(
                id = StructuredLogId("legacy-log"),
                fields = linkedMapOf("cantidad" to "3", "estado" to "  listo  "),
                sourceCaptureId = CaptureId(LEGACY_CORRECTED_VOICE_CAPTURE.id),
                createdAt = Instant.ofEpochMilli(1788436840000),
            ),
            database.structuredLogDao().getById("legacy-log")?.let { log ->
                log.toDomain(database.structuredLogFieldDao().getByStructuredLogId(log.id))
            },
        )
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("legacy-entry").isEmpty())
        assertEquals(6L, readUserVersion())
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
                    "action_ledger_entries",
                    "action_ledger_mutations",
                ),
            ),
        )
        assertEquals(schemaBefore, readLegacySchemaObjects())

        database.close()
        openProductionDatabase()
        val store = ledgerStore(
            entryIds = listOf("post-migration-entry"),
            times = listOf(Instant.parse("2026-09-07T17:00:00Z")),
        )
        val saved = store.record(listOf(mutation(targetId = "post-migration-target")))
            as ActionLedgerRecordResult.Saved
        assertEquals(saved.entry, store.getEntry(saved.entry.id))
        assertEquals(6L, readUserVersion())
        assertEquals(schemaBefore, readLegacySchemaObjects())
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun openProductionDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun ledgerStore(
        entryIds: List<String> = emptyList(),
        times: List<Instant> = emptyList(),
    ): RoomActionLedgerStore = RoomActionLedgerStore(
        database = database,
        idProvider = QueueActionLedgerIdProvider(entryIds),
        clock = QueueActionLedgerClock(times),
    )

    private fun mutation(
        operation: ActionLedgerOperation = ActionLedgerOperation.CREATE,
        targetType: String = "list_item",
        targetId: String = "target",
        payloadVersion: Int = 1,
        beforeState: String? = null,
        afterState: String? = null,
    ) = ActionLedgerMutationInput(
        operation = operation,
        targetType = targetType,
        targetId = targetId,
        payloadVersion = payloadVersion,
        beforeState = beforeState,
        afterState = afterState,
    )

    private fun capture(id: String, input: CaptureInput): Capture = Capture(
        id = CaptureId(id),
        originalInput = input,
        capturedAt = FIXED_CAPTURE_TIME,
    )

    private fun createVersion4Fixture() {
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
                "CREATE TABLE IF NOT EXISTS notes " +
                    "(id TEXT NOT NULL, text TEXT NOT NULL, source_capture_id TEXT, " +
                    "created_at_epoch_millis INTEGER NOT NULL, PRIMARY KEY(id), " +
                    "FOREIGN KEY(source_capture_id) REFERENCES captures(id) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION)",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS index_notes_source_capture_id " +
                    "ON notes (source_capture_id)",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS structured_logs " +
                    "(id TEXT NOT NULL, source_capture_id TEXT, created_at_epoch_millis INTEGER NOT NULL, " +
                    "PRIMARY KEY(id), FOREIGN KEY(source_capture_id) REFERENCES captures(id) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION)",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS index_structured_logs_source_capture_id " +
                    "ON structured_logs (source_capture_id)",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS structured_log_fields " +
                    "(structured_log_id TEXT NOT NULL, field_key TEXT NOT NULL, field_value TEXT NOT NULL, " +
                    "PRIMARY KEY(structured_log_id, field_key), FOREIGN KEY(structured_log_id) " +
                    "REFERENCES structured_logs(id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY,identity_hash TEXT)",
            )
            connection.execute(
                "INSERT OR REPLACE INTO room_master_table " +
                    "(id,identity_hash) VALUES(42, '$V4_IDENTITY_HASH')",
            )
            connection.execute("PRAGMA user_version = 4")
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
            connection.execute(
                "INSERT INTO notes VALUES " +
                    "('legacy-note', '  Nota legada  ', 'legacy-text', 1788436830000)",
            )
            connection.execute(
                "INSERT INTO structured_logs VALUES " +
                    "('legacy-log', 'legacy-corrected-voice', 1788436840000)",
            )
            connection.execute(
                "INSERT INTO structured_log_fields VALUES " +
                    "('legacy-log', 'cantidad', '3')",
            )
            connection.execute(
                "INSERT INTO structured_log_fields VALUES " +
                    "('legacy-log', 'estado', '  listo  ')",
            )
        }
    }

    private fun addFailingMutationTrigger() {
        val path = context.getDatabasePath(databaseName).absolutePath
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_action_ledger_mutation_insert " +
                    "BEFORE INSERT ON action_ledger_mutations " +
                    "WHEN NEW.target_id = 'force-child-insert-failure' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced action ledger child insert failure'); END",
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

    private fun readLegacySchemaObjects(): List<SqliteObject> = BundledSQLiteDriver().open(
        context.getDatabasePath(databaseName).absolutePath,
    ).use { connection ->
        connection.prepare(
            "SELECT type, name, sql FROM sqlite_master " +
                "WHERE type IN ('table', 'index') ORDER BY type, name",
        ).use { statement ->
            buildList {
                while (statement.step()) {
                    val name = statement.getText(1)
                    if (name in LEGACY_SCHEMA_OBJECT_NAMES) {
                        add(
                            SqliteObject(
                                type = statement.getText(0),
                                name = name,
                                sql = statement.getText(2),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private class QueueActionLedgerIdProvider(ids: List<String>) : ActionLedgerIdProvider {
        private val ids = ArrayDeque(ids)

        override fun nextEntryId(): ActionLedgerEntryId = ActionLedgerEntryId(ids.removeFirst())
    }

    private class QueueActionLedgerClock(times: List<Instant>) : ActionLedgerClock {
        private val times = ArrayDeque(times)

        override fun now(): Instant = times.removeFirst()
    }

    private data class LegacyCaptureRow(
        val id: String,
        val kind: String,
        val originalText: String,
        val capturedAtEpochMillis: Long,
        val correctedTranscript: String?,
    )

    private data class SqliteObject(
        val type: String,
        val name: String,
        val sql: String,
    )

    private companion object {
        const val V4_IDENTITY_HASH = "3a960e6f3c2ef27e4f9f13848f69861f"
        val FIXED_CAPTURE_TIME = Instant.parse("2026-09-07T09:00:00Z")
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
        val LEGACY_SCHEMA_OBJECT_NAMES = setOf(
            "captures",
            "list_definitions",
            "list_sessions",
            "list_items",
            "notes",
            "structured_logs",
            "structured_log_fields",
            "index_list_sessions_list_definition_id",
            "index_list_items_list_definition_id",
            "index_list_items_list_session_id",
            "index_notes_source_capture_id",
            "index_structured_logs_source_capture_id",
        )
    }
}

package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.actions.ActionLedgerTargetType
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskPersistenceDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-022-task-${UUID.randomUUID()}.db"
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
    fun freshV6DatabaseRoundTripsBothSpacesDatesNullAndStableIdUpsert() = runBlocking {
        openFreshDatabase()
        val personal = Task(
            id = TaskId("task-01-personal"),
            title = "  Pagar Totalplay  ",
            space = TaskSpace.PERSONAL,
        )
        val trabajo = Task(
            id = TaskId("task-02-trabajo"),
            title = "Revisar integración",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 9, 17),
        )
        val store = RoomTaskStore(database)

        store.save(trabajo)
        store.save(personal)

        assertEquals(personal, store.getById(personal.id))
        assertEquals(trabajo, store.getById(trabajo.id))
        assertEquals(listOf(personal, trabajo), store.readAll())
        assertNull(database.taskDao().getById(personal.id.value)?.dueDate)
        assertEquals("2026-09-17", database.taskDao().getById(trabajo.id.value)?.dueDate)

        val updatedPersonal = personal.copy(
            title = "Pagar Totalplay actualizado",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 9, 18),
        )
        store.save(updatedPersonal)

        assertEquals(updatedPersonal, store.getById(personal.id))
        assertEquals(listOf(updatedPersonal, trabajo), store.readAll())
        assertEquals(2, database.taskDao().getAll().size)

        database.close()
        openProductionDatabase()

        assertEquals(updatedPersonal, RoomTaskStore(database).getById(personal.id))
        assertEquals(listOf(updatedPersonal, trabajo), RoomTaskStore(database).readAll())
        database.close()
        assertEquals(6L, readUserVersion())
    }

    @Test
    fun missingTaskReadReturnsNull() = runBlocking {
        openFreshDatabase()

        assertNull(RoomTaskStore(database).getById(TaskId("missing-task")))
    }

    @Test
    fun closedDatabaseSavePropagatesOrdinaryPersistenceFailure() = runBlocking {
        openFreshDatabase()
        val store = RoomTaskStore(database)
        database.close()

        var failure: Exception? = null
        try {
            store.save(task("closed-database"))
        } catch (caught: Exception) {
            failure = caught
        }

        assertNotNull(failure)
        assertFalse(failure is CancellationException)
    }

    @Test
    fun cancelledSavePropagatesCancellation() = runBlocking {
        openFreshDatabase()
        val store = RoomTaskStore(database)
        val cancelledJob = Job().apply { cancel() }
        var cancellation: CancellationException? = null

        try {
            withContext(cancelledJob) {
                store.save(task("cancelled"))
            }
        } catch (caught: CancellationException) {
            cancellation = caught
        }

        assertNotNull(cancellation)
        assertNull(database.taskDao().getById("cancelled"))
    }

    @Test
    fun realV5FixtureMigratesPreservingAllLegacyFamiliesAndSupportsTasks() = runBlocking {
        createVersion5Fixture()
        assertEquals(5L, readUserVersion())
        val schemaBefore = readLegacySchemaObjects()

        openProductionDatabase()

        assertTrue(database.taskDao().getAll().isEmpty())
        assertLegacyData()

        database.close()
        assertEquals(6L, readUserVersion())
        assertEquals(
            listOf(
                ColumnInfo("id", "TEXT", true, 1),
                ColumnInfo("title", "TEXT", true, 0),
                ColumnInfo("space", "TEXT", true, 0),
                ColumnInfo("due_date", "TEXT", false, 0),
            ),
            readColumns("tasks"),
        )
        assertTrue(readTables().contains("tasks"))
        assertEquals(schemaBefore, readLegacySchemaObjects())

        openProductionDatabase()
        assertTrue(database.taskDao().getAll().isEmpty())
        assertLegacyData()
        val postMigrationTask = task(
            id = "post-migration-task",
            title = "  Persistida después de migrar  ",
            space = TaskSpace.PERSONAL,
            dueDate = null,
        )
        RoomTaskStore(database).save(postMigrationTask)
        assertEquals(postMigrationTask, RoomTaskStore(database).getById(postMigrationTask.id))

        database.close()
        assertEquals(6L, readUserVersion())
        assertEquals(schemaBefore, readLegacySchemaObjects())

        openProductionDatabase()
        assertLegacyData()
        assertEquals(postMigrationTask, RoomTaskStore(database).getById(postMigrationTask.id))
        assertEquals(listOf(postMigrationTask), RoomTaskStore(database).readAll())
    }

    private fun task(
        id: String,
        title: String = "Task $id",
        space: TaskSpace = TaskSpace.PERSONAL,
        dueDate: LocalDate? = LocalDate.of(2026, 9, 20),
    ): Task = Task(
        id = TaskId(id),
        title = title,
        space = space,
        dueDate = dueDate,
    )

    private suspend fun assertLegacyData() {
        assertEquals(LEGACY_TEXT_CAPTURE, database.captureDao().getById(LEGACY_TEXT_CAPTURE.id))
        assertEquals(
            LEGACY_TEXT_CAPTURE_DOMAIN,
            database.captureDao().getById(LEGACY_TEXT_CAPTURE.id)?.toDomain(),
        )
        assertEquals(LEGACY_CORRECTED_VOICE_CAPTURE, database.captureDao().getById(LEGACY_CORRECTED_VOICE_CAPTURE.id))
        assertEquals(
            LEGACY_CORRECTED_VOICE_CAPTURE_DOMAIN,
            database.captureDao().getById(LEGACY_CORRECTED_VOICE_CAPTURE.id)?.toDomain(),
        )
        assertEquals(BuiltInListDefinitions.ALL, RoomListStore(database).readBuiltInDefinitions())
        assertEquals(
            LEGACY_MANDADO_SESSION,
            database.listSessionDao().getById(LEGACY_MANDADO_SESSION.id.value)?.toDomain(),
        )
        assertEquals(
            LEGACY_MANDADO_ITEM,
            database.listItemDao().getById(LEGACY_MANDADO_ITEM.id.value)?.toDomain(),
        )
        assertEquals(
            LEGACY_COMPRAS_ITEM,
            database.listItemDao().getById(LEGACY_COMPRAS_ITEM.id.value)?.toDomain(),
        )
        assertEquals(
            LEGACY_NOTE,
            database.noteDao().getById(LEGACY_NOTE.id.value)?.toDomain(),
        )
        val logEntity = database.structuredLogDao().getById(LEGACY_LOG.id.value)
        assertNotNull(logEntity)
        assertEquals(
            LEGACY_LOG,
            logEntity?.toDomain(database.structuredLogFieldDao().getByStructuredLogId(LEGACY_LOG.id.value)),
        )
        assertEquals(
            LEGACY_LEDGER_ENTRY,
            RoomActionLedgerStore(database).getEntry(LEGACY_LEDGER_ENTRY.id),
        )
        assertEquals(
            LEGACY_LEDGER_ENTRY.mutations,
            database.actionLedgerMutationDao().getByEntryId(LEGACY_LEDGER_ENTRY.id.value)
                .map { it.toDomainMutation() },
        )
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun openProductionDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun createVersion5Fixture() {
        val path = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }.absolutePath
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execute("PRAGMA foreign_keys = ON")
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `captures` (
                    `id` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `original_text` TEXT NOT NULL,
                    `captured_at_epoch_millis` INTEGER NOT NULL,
                    `corrected_transcript` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `list_definitions` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `behavior` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `list_sessions` (
                    `id` TEXT NOT NULL,
                    `list_definition_id` TEXT NOT NULL,
                    `started_at_epoch_millis` INTEGER NOT NULL,
                    `ended_at_epoch_millis` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`list_definition_id`) REFERENCES `list_definitions`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `list_items` (
                    `id` TEXT NOT NULL,
                    `list_definition_id` TEXT NOT NULL,
                    `list_session_id` TEXT,
                    `text` TEXT NOT NULL,
                    `is_completed` INTEGER NOT NULL,
                    `created_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`list_definition_id`) REFERENCES `list_definitions`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION,
                    FOREIGN KEY(`list_session_id`) REFERENCES `list_sessions`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `notes` (
                    `id` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `source_capture_id` TEXT,
                    `created_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`source_capture_id`) REFERENCES `captures`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `structured_logs` (
                    `id` TEXT NOT NULL,
                    `source_capture_id` TEXT,
                    `created_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`source_capture_id`) REFERENCES `captures`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `structured_log_fields` (
                    `structured_log_id` TEXT NOT NULL,
                    `field_key` TEXT NOT NULL,
                    `field_value` TEXT NOT NULL,
                    PRIMARY KEY(`structured_log_id`, `field_key`),
                    FOREIGN KEY(`structured_log_id`) REFERENCES `structured_logs`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `action_ledger_entries` (
                    `id` TEXT NOT NULL,
                    `occurred_at_epoch_millis` INTEGER NOT NULL,
                    `source_capture_id` TEXT,
                    `undone_at_epoch_millis` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`source_capture_id`) REFERENCES `captures`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS `action_ledger_mutations` (
                    `action_ledger_entry_id` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `operation` TEXT NOT NULL,
                    `target_type` TEXT NOT NULL,
                    `target_id` TEXT NOT NULL,
                    `payload_version` INTEGER NOT NULL,
                    `before_state` TEXT,
                    `after_state` TEXT,
                    PRIMARY KEY(`action_ledger_entry_id`, `position`),
                    FOREIGN KEY(`action_ledger_entry_id`) REFERENCES `action_ledger_entries`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS `index_list_sessions_list_definition_id` " +
                    "ON `list_sessions` (`list_definition_id`) ",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS `index_list_items_list_definition_id` " +
                    "ON `list_items` (`list_definition_id`) ",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS `index_list_items_list_session_id` " +
                    "ON `list_items` (`list_session_id`) ",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS `index_notes_source_capture_id` " +
                    "ON `notes` (`source_capture_id`) ",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS `index_structured_logs_source_capture_id` " +
                    "ON `structured_logs` (`source_capture_id`) ",
            )
            connection.execute(
                "CREATE INDEX IF NOT EXISTS `index_action_ledger_entries_source_capture_id` " +
                    "ON `action_ledger_entries` (`source_capture_id`) ",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS `room_master_table` " +
                    "(id INTEGER PRIMARY KEY,identity_hash TEXT)",
            )
            connection.execute(
                "INSERT OR REPLACE INTO `room_master_table` " +
                    "(id,identity_hash) VALUES(42, '$V5_IDENTITY_HASH')",
            )
            connection.execute("PRAGMA user_version = 5")

            connection.insertCapture(LEGACY_TEXT_CAPTURE)
            connection.insertCapture(LEGACY_CORRECTED_VOICE_CAPTURE)
            connection.execute("INSERT INTO `list_definitions` VALUES ('mandado', 'Mandado', 'SESSION_BASED')")
            connection.execute("INSERT INTO `list_definitions` VALUES ('compras', 'Compras', 'CONTINUOUS')")
            connection.execute(
                "INSERT INTO `list_sessions` VALUES " +
                    "('legacy-mandado-session', 'mandado', 1788436800000, 1788436860000)",
            )
            connection.execute(
                "INSERT INTO `list_items` VALUES " +
                    "('legacy-mandado-item', 'mandado', 'legacy-mandado-session', " +
                    "'  Jabón legado  ', 1, 1788436810000)",
            )
            connection.execute(
                "INSERT INTO `list_items` VALUES " +
                    "('legacy-compras-item', 'compras', NULL, '  Leche compras  ', 0, 1788436820000)",
            )
            connection.execute(
                "INSERT INTO `notes` VALUES " +
                    "('legacy-note', '  Nota legada  ', 'legacy-text', 1788436830000)",
            )
            connection.execute(
                "INSERT INTO `structured_logs` VALUES " +
                    "('legacy-log', 'legacy-corrected-voice', 1788436840000)",
            )
            connection.execute(
                "INSERT INTO `structured_log_fields` VALUES " +
                    "('legacy-log', 'cantidad', '3')",
            )
            connection.execute(
                "INSERT INTO `structured_log_fields` VALUES " +
                    "('legacy-log', 'estado', '  listo  ')",
            )
            connection.execute(
                "INSERT INTO `action_ledger_entries` VALUES " +
                    "('legacy-entry', 1788436850000, 'legacy-text', NULL)",
            )
            connection.execute(
                "INSERT INTO `action_ledger_mutations` VALUES " +
                    "('legacy-entry', 0, 'CREATE', 'task', 'legacy-task', 1, NULL, 'after legacy task')",
            )
        }
    }

    private fun SQLiteConnection.insertCapture(entity: CaptureEntity) {
        prepare(
            "INSERT INTO `captures` " +
                "(`id`, `kind`, `original_text`, `captured_at_epoch_millis`, `corrected_transcript`) " +
                "VALUES (?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.bindText(1, entity.id)
            statement.bindText(2, entity.kind)
            statement.bindText(3, entity.originalText)
            statement.bindLong(4, entity.capturedAtEpochMillis)
            if (entity.correctedTranscript == null) {
                statement.bindNull(5)
            } else {
                statement.bindText(5, entity.correctedTranscript)
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

    private fun readColumns(tableName: String): List<ColumnInfo> = BundledSQLiteDriver().open(
        context.getDatabasePath(databaseName).absolutePath,
    ).use { connection ->
        connection.prepare("PRAGMA table_info(`$tableName`)").use { statement ->
            buildList {
                while (statement.step()) {
                    add(
                        ColumnInfo(
                            name = statement.getText(1),
                            type = statement.getText(2),
                            notNull = statement.getLong(3) != 0L,
                            primaryKeyPosition = statement.getLong(5).toInt(),
                        ),
                    )
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

    private fun ActionLedgerMutationEntity.toDomainMutation(): ActionLedgerMutation = ActionLedgerMutation(
        operation = ActionLedgerOperation.valueOf(operation),
        targetType = ActionLedgerTargetType(targetType),
        targetId = targetId,
        payloadVersion = payloadVersion,
        beforeState = beforeState,
        afterState = afterState,
    )

    private data class ColumnInfo(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val primaryKeyPosition: Int,
    )

    private data class SqliteObject(
        val type: String,
        val name: String,
        val sql: String?,
    )

    private companion object {
        const val V5_IDENTITY_HASH = "a48a06990e50fcd1d1571535174d0622"
        val LEGACY_TEXT_CAPTURE = CaptureEntity(
            id = "legacy-text",
            kind = "TEXT",
            originalText = "  Texto legado  ",
            capturedAtEpochMillis = 1788436800123,
            correctedTranscript = null,
        )
        val LEGACY_TEXT_CAPTURE_DOMAIN = Capture(
            id = CaptureId("legacy-text"),
            originalInput = CaptureInput.Text("  Texto legado  "),
            capturedAt = Instant.ofEpochMilli(1788436800123),
        )
        val LEGACY_CORRECTED_VOICE_CAPTURE = CaptureEntity(
            id = "legacy-corrected-voice",
            kind = "VOICE",
            originalText = "comprar leche manana",
            capturedAtEpochMillis = 1788436800456,
            correctedTranscript = "Comprar leche mañana",
        )
        val LEGACY_CORRECTED_VOICE_CAPTURE_DOMAIN = Capture(
            id = CaptureId("legacy-corrected-voice"),
            originalInput = CaptureInput.Voice("comprar leche manana"),
            capturedAt = Instant.ofEpochMilli(1788436800456),
            transcriptCorrection = "Comprar leche mañana",
        )
        val LEGACY_MANDADO_SESSION = ListSession(
            id = ListSessionId("legacy-mandado-session"),
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            startedAt = Instant.ofEpochMilli(1788436800000),
            endedAt = Instant.ofEpochMilli(1788436860000),
        )
        val LEGACY_MANDADO_ITEM = ListItem(
            id = ListItemId("legacy-mandado-item"),
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            listSessionId = LEGACY_MANDADO_SESSION.id,
            text = "  Jabón legado  ",
            isCompleted = true,
            createdAt = Instant.ofEpochMilli(1788436810000),
        )
        val LEGACY_COMPRAS_ITEM = ListItem(
            id = ListItemId("legacy-compras-item"),
            listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
            listSessionId = null,
            text = "  Leche compras  ",
            isCompleted = false,
            createdAt = Instant.ofEpochMilli(1788436820000),
        )
        val LEGACY_NOTE = Note(
            id = NoteId("legacy-note"),
            text = "  Nota legada  ",
            sourceCaptureId = CaptureId("legacy-text"),
            createdAt = Instant.ofEpochMilli(1788436830000),
        )
        val LEGACY_LOG = StructuredLog(
            id = StructuredLogId("legacy-log"),
            fields = linkedMapOf(
                "cantidad" to "3",
                "estado" to "  listo  ",
            ),
            sourceCaptureId = CaptureId("legacy-corrected-voice"),
            createdAt = Instant.ofEpochMilli(1788436840000),
        )
        val LEGACY_LEDGER_ENTRY = ActionLedgerEntry(
            id = ActionLedgerEntryId("legacy-entry"),
            occurredAt = Instant.ofEpochMilli(1788436850000),
            sourceCaptureId = CaptureId("legacy-text"),
            mutations = listOf(
                ActionLedgerMutation(
                    operation = ActionLedgerOperation.CREATE,
                    targetType = ActionLedgerTargetType("task"),
                    targetId = "legacy-task",
                    payloadVersion = 1,
                    afterState = "after legacy task",
                ),
            ),
        )
        val LEGACY_SCHEMA_OBJECT_NAMES = setOf(
            "captures",
            "list_definitions",
            "list_sessions",
            "list_items",
            "notes",
            "structured_logs",
            "structured_log_fields",
            "action_ledger_entries",
            "action_ledger_mutations",
            "index_list_sessions_list_definition_id",
            "index_list_items_list_definition_id",
            "index_list_items_list_session_id",
            "index_notes_source_capture_id",
            "index_structured_logs_source_capture_id",
            "index_action_ledger_entries_source_capture_id",
        )
    }
}

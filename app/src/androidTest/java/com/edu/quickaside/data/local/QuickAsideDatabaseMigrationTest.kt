package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAsideDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "migration-7-8-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun version7BackfillsActivityFromStartedAtOrLatestDurableItemCreation() = runBlocking {
        createVersion7Fixture()

        database = QuickAsideDatabase.create(context, databaseName)

        assertEquals(
            1_000L,
            database.listSessionDao().getById("session-no-items")?.lastActivityAtEpochMillis,
        )
        assertEquals(
            2_000L,
            database.listSessionDao().getById("session-with-older-items")?.lastActivityAtEpochMillis,
        )
        assertEquals(
            5_000L,
            database.listSessionDao().getById("session-with-later-item")?.lastActivityAtEpochMillis,
        )
        assertEquals(
            9_000L,
            database.listSessionDao().getById("session-started-after-items")?.lastActivityAtEpochMillis,
        )
        assertEquals(
            BuiltInListDefinitions.ALL,
            RoomListStore(database).readBuiltInDefinitions(),
        )
        database.close()
        assertEquals(8L, readUserVersion())
    }

    @Test
    fun version8SchemaIsExportedAndMatchesTheValidatedProductionDatabase() = runBlocking {
        database = QuickAsideDatabase.create(context, databaseName)
        // Any read forces Room to open and validate the freshly created v8 schema.
        assertTrue(
            database.listSessionDao()
                .getByDefinitionId(BuiltInListDefinitions.MANDADO.id.value)
                .isEmpty(),
        )

        val exported = JSONObject(readAsset(SCHEMA_8_ASSET)).getJSONObject("database")
        assertEquals(8, exported.getInt("version"))
        assertEquals(8L, readUserVersion())
        assertEquals(exported.getString("identityHash"), readIdentityHash())
        val entities = exported.getJSONArray("entities")
        val listSessions = (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .single { it.getString("tableName") == "list_sessions" }
        val fields = listSessions.getJSONArray("fields")
        val columnNames = (0 until fields.length())
            .map { fields.getJSONObject(it).getString("columnName") }
        assertTrue(columnNames.contains("last_activity_at_epoch_millis"))
    }

    private fun createVersion7Fixture() {
        val path = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }.absolutePath
        BundledSQLiteDriver().open(path).use { connection ->
            val root = JSONObject(readAsset(SCHEMA_7_ASSET)).getJSONObject("database")
            val entities = root.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val tableName = entity.getString("tableName")
                connection.execute(entity.getString("createSql").tableName(tableName))
                val indices = entity.optJSONArray("indices") ?: continue
                for (indexIndex in 0 until indices.length()) {
                    val indexJson = indices.getJSONObject(indexIndex)
                    connection.execute(indexJson.getString("createSql").tableName(tableName))
                }
            }
            connection.execute("PRAGMA foreign_keys = OFF")
            connection.execute("INSERT INTO list_definitions VALUES ('mandado', 'Mandado', 'SESSION_BASED')")
            connection.execute("INSERT INTO list_definitions VALUES ('compras', 'Compras', 'CONTINUOUS')")
            connection.execute(
                "INSERT INTO list_sessions VALUES " +
                    "('session-no-items', 'mandado', 1000, NULL)",
            )
            connection.execute(
                "INSERT INTO list_sessions VALUES " +
                    "('session-with-older-items', 'mandado', 2000, NULL)",
            )
            connection.execute(
                "INSERT INTO list_sessions VALUES " +
                    "('session-with-later-item', 'mandado', 3000, NULL)",
            )
            connection.execute(
                "INSERT INTO list_sessions VALUES " +
                    "('session-started-after-items', 'mandado', 9000, NULL)",
            )
            connection.insertFixtureItem("item-older", "session-with-older-items", 1500)
            connection.insertFixtureItem("item-later-1", "session-with-later-item", 4000)
            connection.insertFixtureItem("item-later-2", "session-with-later-item", 5000)
            connection.insertFixtureItem("item-before-start", "session-started-after-items", 8000)
            connection.execute("PRAGMA user_version = 7")
        }
    }

    private fun SQLiteConnection.insertFixtureItem(
        id: String,
        sessionId: String,
        createdAtEpochMillis: Long,
    ) {
        prepare(
            "INSERT INTO list_items " +
                "(id, list_definition_id, list_session_id, text, is_completed, " +
                "created_at_epoch_millis) VALUES (?, 'mandado', ?, ?, 0, ?)",
        ).use { statement ->
            statement.bindText(1, id)
            statement.bindText(2, sessionId)
            statement.bindText(3, "fixture $id")
            statement.bindLong(4, createdAtEpochMillis)
            statement.step()
        }
    }

    private fun readAsset(path: String): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .context.assets.open(path).bufferedReader().use { it.readText() }

    private fun readUserVersion(): Long = BundledSQLiteDriver().open(
        context.getDatabasePath(databaseName).absolutePath,
    ).use { connection ->
        connection.prepare("PRAGMA user_version").use { statement ->
            assertTrue(statement.step())
            statement.getLong(0)
        }
    }

    private fun readIdentityHash(): String = BundledSQLiteDriver().open(
        context.getDatabasePath(databaseName).absolutePath,
    ).use { connection ->
        connection.prepare(
            "SELECT identity_hash FROM room_master_table WHERE id = 42",
        ).use { statement ->
            assertTrue(statement.step())
            statement.getText(0)
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private fun String.tableName(tableName: String): String =
        replace("\${TABLE_NAME}", tableName)

    private companion object {
        const val SCHEMA_ASSETS_ROOT = "com.edu.quickaside.data.local.QuickAsideDatabase"
        const val SCHEMA_7_ASSET = "$SCHEMA_ASSETS_ROOT/7.json"
        const val SCHEMA_8_ASSET = "$SCHEMA_ASSETS_ROOT/8.json"
    }
}

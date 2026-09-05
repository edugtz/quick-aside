package com.edu.quickaside.data.local

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import com.edu.quickaside.domain.lists.BuiltInListDefinitions

/** Owns the two built-in rows for both fresh creation and migration paths. */
internal object BuiltInListDefinitionBootstrapper : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        seed(connection)
    }

    fun seed(connection: SQLiteConnection) {
        BuiltInListDefinitions.ALL.forEach { definition ->
            connection.prepare(
                "INSERT OR IGNORE INTO list_definitions (id, name, behavior) VALUES (?, ?, ?)",
            ).use { statement ->
                statement.bindText(1, definition.id.value)
                statement.bindText(2, definition.name)
                statement.bindText(3, definition.behavior.name)
                statement.step()
            }
        }
    }
}

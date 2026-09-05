package com.edu.quickaside.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [
        CaptureEntity::class,
        ListDefinitionEntity::class,
        ListSessionEntity::class,
        ListItemEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class QuickAsideDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    abstract fun listDefinitionDao(): ListDefinitionDao

    abstract fun listSessionDao(): ListSessionDao

    abstract fun listItemDao(): ListItemDao

    companion object {
        const val DATABASE_NAME = "quick_aside.db"

        fun create(
            context: Context,
            databaseName: String = DATABASE_NAME,
        ): QuickAsideDatabase = Room.databaseBuilder(
            context,
            QuickAsideDatabase::class.java,
            databaseName,
        )
            .setDriver(BundledSQLiteDriver())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(BuiltInListDefinitionBootstrapper)
            .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    "ALTER TABLE captures ADD COLUMN corrected_transcript TEXT",
                ).use { statement ->
                    statement.step()
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    """
                    CREATE TABLE IF NOT EXISTS `list_definitions` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `behavior` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                ).use { statement -> statement.step() }
                connection.prepare(
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
                ).use { statement -> statement.step() }
                connection.prepare(
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
                ).use { statement -> statement.step() }
                connection.prepare(
                    "CREATE INDEX IF NOT EXISTS `index_list_sessions_list_definition_id` " +
                        "ON `list_sessions` (`list_definition_id`) ",
                ).use { statement -> statement.step() }
                connection.prepare(
                    "CREATE INDEX IF NOT EXISTS `index_list_items_list_definition_id` " +
                        "ON `list_items` (`list_definition_id`) ",
                ).use { statement -> statement.step() }
                connection.prepare(
                    "CREATE INDEX IF NOT EXISTS `index_list_items_list_session_id` " +
                        "ON `list_items` (`list_session_id`) ",
                ).use { statement -> statement.step() }
                BuiltInListDefinitionBootstrapper.seed(connection)
            }
        }
    }
}

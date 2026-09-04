package com.edu.quickaside.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [CaptureEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class QuickAsideDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

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
            .addMigrations(MIGRATION_1_2)
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
    }
}

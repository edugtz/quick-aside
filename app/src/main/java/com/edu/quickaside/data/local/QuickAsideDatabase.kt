package com.edu.quickaside.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [CaptureEntity::class],
    version = 1,
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
            .build()
    }
}

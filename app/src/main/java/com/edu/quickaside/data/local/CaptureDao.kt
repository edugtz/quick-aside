package com.edu.quickaside.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(capture: CaptureEntity)

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun getById(id: String): CaptureEntity?
}

package com.edu.quickaside.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface TaskDao {
    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAll(): List<TaskEntity>
}

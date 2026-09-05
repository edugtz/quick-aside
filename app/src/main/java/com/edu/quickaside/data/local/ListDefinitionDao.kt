package com.edu.quickaside.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface ListDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(definition: ListDefinitionEntity)

    @Query("SELECT * FROM list_definitions WHERE id = :id")
    suspend fun getById(id: String): ListDefinitionEntity?

    @Query("SELECT * FROM list_definitions ORDER BY id ASC")
    suspend fun getAll(): List<ListDefinitionEntity>
}

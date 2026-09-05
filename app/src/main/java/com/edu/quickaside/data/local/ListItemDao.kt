package com.edu.quickaside.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface ListItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ListItemEntity)

    @Query("SELECT * FROM list_items WHERE id = :id")
    suspend fun getById(id: String): ListItemEntity?

    @Query(
        """
        SELECT * FROM list_items
        WHERE list_definition_id = :listDefinitionId
          AND list_session_id IS NULL
        ORDER BY created_at_epoch_millis ASC, id ASC
        """,
    )
    suspend fun getContinuousByDefinitionId(listDefinitionId: String): List<ListItemEntity>

    @Query(
        """
        SELECT * FROM list_items
        WHERE list_session_id = :listSessionId
        ORDER BY created_at_epoch_millis ASC, id ASC
        """,
    )
    suspend fun getBySessionId(listSessionId: String): List<ListItemEntity>

    @Query(
        """
        UPDATE list_items
        SET is_completed = :isCompleted
        WHERE id = :id
        """,
    )
    suspend fun setCompleted(id: String, isCompleted: Boolean): Int
}

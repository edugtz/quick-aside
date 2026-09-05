package com.edu.quickaside.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface ListSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ListSessionEntity)

    @Query("SELECT * FROM list_sessions WHERE id = :id")
    suspend fun getById(id: String): ListSessionEntity?

    @Query(
        """
        SELECT * FROM list_sessions
        WHERE list_definition_id = :listDefinitionId
          AND ended_at_epoch_millis IS NULL
        ORDER BY started_at_epoch_millis DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getActiveByDefinitionId(listDefinitionId: String): ListSessionEntity?

    @Query(
        """
        SELECT * FROM list_sessions
        WHERE list_definition_id = :listDefinitionId
        ORDER BY started_at_epoch_millis DESC, id DESC
        """,
    )
    suspend fun getByDefinitionId(listDefinitionId: String): List<ListSessionEntity>

    @Query(
        """
        UPDATE list_sessions
        SET ended_at_epoch_millis = :endedAtEpochMillis
        WHERE id = :id AND ended_at_epoch_millis IS NULL
        """,
    )
    suspend fun finishActive(id: String, endedAtEpochMillis: Long): Int
}

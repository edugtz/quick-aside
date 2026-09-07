package com.edu.quickaside.data.local

import androidx.room3.Dao
import androidx.room3.ColumnInfo
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
        SELECT
            items.id,
            items.list_definition_id,
            items.list_session_id,
            items.text,
            items.is_completed,
            items.created_at_epoch_millis,
            definitions.name AS list_definition_name,
            sessions.started_at_epoch_millis AS session_started_at_epoch_millis,
            sessions.ended_at_epoch_millis AS session_ended_at_epoch_millis
        FROM list_items AS items
        INNER JOIN list_definitions AS definitions
            ON definitions.id = items.list_definition_id
        LEFT JOIN list_sessions AS sessions
            ON sessions.id = items.list_session_id
        WHERE items.text COLLATE NOCASE LIKE :pattern ESCAPE '\'
        ORDER BY items.created_at_epoch_millis DESC, items.id DESC
        LIMIT :limit
        """,
    )
    suspend fun search(pattern: String, limit: Int): List<ListItemSearchRow>

    @Query(
        """
        UPDATE list_items
        SET is_completed = :isCompleted
        WHERE id = :id
        """,
    )
    suspend fun setCompleted(id: String, isCompleted: Boolean): Int
}

data class ListItemSearchRow(
    val id: String,
    @ColumnInfo(name = "list_definition_id")
    val listDefinitionId: String,
    @ColumnInfo(name = "list_session_id")
    val listSessionId: String?,
    val text: String,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "list_definition_name")
    val listDefinitionName: String,
    @ColumnInfo(name = "session_started_at_epoch_millis")
    val sessionStartedAtEpochMillis: Long?,
    @ColumnInfo(name = "session_ended_at_epoch_millis")
    val sessionEndedAtEpochMillis: Long?,
)

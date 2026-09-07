package com.edu.quickaside.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        ORDER BY created_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecent(limit: Int): List<NoteEntity>

    @Query(
        """
        SELECT * FROM notes
        WHERE text COLLATE NOCASE LIKE :pattern ESCAPE '\'
        ORDER BY created_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun search(pattern: String, limit: Int): List<NoteEntity>
}

@Dao
interface StructuredLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: StructuredLogEntity)

    @Query("SELECT * FROM structured_logs WHERE id = :id")
    suspend fun getById(id: String): StructuredLogEntity?

    @Query(
        """
        SELECT * FROM structured_logs
        ORDER BY created_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecent(limit: Int): List<StructuredLogEntity>

    @Query(
        """
        SELECT logs.* FROM structured_logs AS logs
        WHERE EXISTS (
            SELECT 1
            FROM structured_log_fields AS fields
            WHERE fields.structured_log_id = logs.id
              AND (
                  fields.field_key COLLATE NOCASE LIKE :pattern ESCAPE '\'
                  OR fields.field_value COLLATE NOCASE LIKE :pattern ESCAPE '\'
              )
        )
        ORDER BY logs.created_at_epoch_millis DESC, logs.id DESC
        LIMIT :limit
        """,
    )
    suspend fun search(pattern: String, limit: Int): List<StructuredLogEntity>
}

@Dao
interface StructuredLogFieldDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(fields: List<StructuredLogFieldEntity>)

    @Query(
        """
        SELECT * FROM structured_log_fields
        WHERE structured_log_id = :structuredLogId
        ORDER BY field_key ASC
        """,
    )
    suspend fun getByStructuredLogId(structuredLogId: String): List<StructuredLogFieldEntity>
}

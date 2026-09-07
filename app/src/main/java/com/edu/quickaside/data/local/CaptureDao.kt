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

    @Query(
        """
        UPDATE captures
        SET corrected_transcript = :correctedTranscript
        WHERE id = :id AND kind = 'VOICE'
        """,
    )
    suspend fun updateCorrectedTranscript(id: String, correctedTranscript: String): Int

    @Query(
        """
        SELECT * FROM captures
        ORDER BY captured_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecent(limit: Int): List<CaptureEntity>

    @Query(
        """
        SELECT * FROM captures
        WHERE (
            (kind = 'TEXT' AND original_text COLLATE NOCASE LIKE :pattern ESCAPE '\')
            OR
            (kind = 'VOICE' AND COALESCE(corrected_transcript, original_text)
                COLLATE NOCASE LIKE :pattern ESCAPE '\')
        )
        ORDER BY captured_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun search(pattern: String, limit: Int): List<CaptureEntity>
}

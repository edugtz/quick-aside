package com.edu.quickaside.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface ActionLedgerEntryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ActionLedgerEntryEntity)

    @Query("SELECT * FROM action_ledger_entries WHERE id = :id")
    suspend fun getById(id: String): ActionLedgerEntryEntity?

    @Query(
        """
        SELECT * FROM action_ledger_entries
        ORDER BY occurred_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecent(limit: Int): List<ActionLedgerEntryEntity>

    @Query(
        """
        SELECT * FROM action_ledger_entries
        WHERE undone_at_epoch_millis IS NULL
        ORDER BY occurred_at_epoch_millis DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestUndoable(): ActionLedgerEntryEntity?

    @Query(
        """
        UPDATE action_ledger_entries
        SET undone_at_epoch_millis = :undoneAtEpochMillis
        WHERE id = :id AND undone_at_epoch_millis IS NULL
        """,
    )
    suspend fun markUndone(id: String, undoneAtEpochMillis: Long): Int
}

@Dao
interface ActionLedgerMutationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(mutations: List<ActionLedgerMutationEntity>)

    @Query(
        """
        SELECT * FROM action_ledger_mutations
        WHERE action_ledger_entry_id = :entryId
        ORDER BY position ASC
        """,
    )
    suspend fun getByEntryId(entryId: String): List<ActionLedgerMutationEntity>
}

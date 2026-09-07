package com.edu.quickaside.data.local

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.actions.ActionLedgerMarkUndoneResult
import com.edu.quickaside.application.actions.ActionLedgerMutationInput
import com.edu.quickaside.application.actions.ActionLedgerMutationValidationReason
import com.edu.quickaside.application.actions.ActionLedgerRecordResult
import com.edu.quickaside.application.actions.ActionLedgerStore
import com.edu.quickaside.application.actions.RandomActionLedgerIdProvider
import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerTargetType
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import kotlinx.coroutines.CancellationException

class RoomActionLedgerStore(
    private val database: QuickAsideDatabase,
    private val idProvider: ActionLedgerIdProvider = RandomActionLedgerIdProvider(),
    private val clock: ActionLedgerClock = ActionLedgerClock { Instant.now() },
) : ActionLedgerStore {
    override suspend fun record(
        mutations: List<ActionLedgerMutationInput>,
        sourceCaptureId: CaptureId?,
    ): ActionLedgerRecordResult {
        if (mutations.isEmpty()) {
            return ActionLedgerRecordResult.EmptyMutationBatch
        }

        val validatedMutations = buildList {
            mutations.forEachIndexed { position, mutation ->
                when {
                    mutation.targetType.isBlank() ->
                        return ActionLedgerRecordResult.InvalidMutation(
                            position = position,
                            reason = ActionLedgerMutationValidationReason.BLANK_TARGET_TYPE,
                        )

                    mutation.targetId.isBlank() ->
                        return ActionLedgerRecordResult.InvalidMutation(
                            position = position,
                            reason = ActionLedgerMutationValidationReason.BLANK_TARGET_ID,
                        )

                    mutation.payloadVersion <= 0 ->
                        return ActionLedgerRecordResult.InvalidMutation(
                            position = position,
                            reason = ActionLedgerMutationValidationReason.NON_POSITIVE_PAYLOAD_VERSION,
                        )

                    else -> add(
                        ActionLedgerMutation(
                            operation = mutation.operation,
                            targetType = ActionLedgerTargetType(mutation.targetType),
                            targetId = mutation.targetId,
                            payloadVersion = mutation.payloadVersion,
                            beforeState = mutation.beforeState,
                            afterState = mutation.afterState,
                        ),
                    )
                }
            }
        }

        return try {
            database.withWriteTransaction {
                if (sourceCaptureId != null && database.captureDao().getById(sourceCaptureId.value) == null) {
                    return@withWriteTransaction ActionLedgerRecordResult.MissingSourceCapture
                }
                val entry = ActionLedgerEntry(
                    id = idProvider.nextEntryId(),
                    occurredAt = clock.now(),
                    sourceCaptureId = sourceCaptureId,
                    mutations = validatedMutations,
                )
                database.actionLedgerEntryDao().insert(entry.toEntity())
                database.actionLedgerMutationDao().insertAll(entry.toMutationEntities())
                ActionLedgerRecordResult.Saved(entry)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            ActionLedgerRecordResult.Failed(failure)
        }
    }

    override suspend fun getEntry(id: ActionLedgerEntryId): ActionLedgerEntry? =
        database.withReadTransaction {
            val entity = database.actionLedgerEntryDao().getById(id.value)
            if (entity == null) null else loadEntry(entity)
        }

    override suspend fun readRecentEntries(limit: Int): List<ActionLedgerEntry> =
        database.withReadTransaction {
            database.actionLedgerEntryDao().getRecent(limit).map { loadEntry(it) }
        }

    override suspend fun readLatestUndoable(): ActionLedgerEntry? =
        database.withReadTransaction {
            val entity = database.actionLedgerEntryDao().getLatestUndoable()
            if (entity == null) null else loadEntry(entity)
        }

    override suspend fun markUndone(id: ActionLedgerEntryId): ActionLedgerMarkUndoneResult = try {
        database.withWriteTransaction {
            val existing = database.actionLedgerEntryDao().getById(id.value)
                ?: return@withWriteTransaction ActionLedgerMarkUndoneResult.MissingEntry
            if (existing.undoneAtEpochMillis != null) {
                return@withWriteTransaction ActionLedgerMarkUndoneResult.AlreadyUndone
            }

            val updated = database.actionLedgerEntryDao().markUndone(
                id = id.value,
                undoneAtEpochMillis = clock.now().toEpochMilli(),
            )
            if (updated != 1) {
                val current = database.actionLedgerEntryDao().getById(id.value)
                when {
                    current == null -> ActionLedgerMarkUndoneResult.MissingEntry
                    current.undoneAtEpochMillis != null -> ActionLedgerMarkUndoneResult.AlreadyUndone
                    else -> error("Action Ledger entry was not marked undone")
                }
            } else {
                val marked = database.actionLedgerEntryDao().getById(id.value)
                    ?: error("Marked Action Ledger entry disappeared after update")
                ActionLedgerMarkUndoneResult.MarkedUndone(loadEntry(marked))
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        ActionLedgerMarkUndoneResult.Failed(failure)
    }

    private suspend fun loadEntry(entity: ActionLedgerEntryEntity): ActionLedgerEntry =
        entity.toDomain(database.actionLedgerMutationDao().getByEntryId(entity.id))
}

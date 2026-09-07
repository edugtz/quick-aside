package com.edu.quickaside.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.actions.ActionLedgerTargetType
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant

@Entity(
    tableName = "action_ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_capture_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["source_capture_id"]),
    ],
)
data class ActionLedgerEntryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "occurred_at_epoch_millis")
    val occurredAtEpochMillis: Long,
    @ColumnInfo(name = "source_capture_id")
    val sourceCaptureId: String? = null,
    @ColumnInfo(name = "undone_at_epoch_millis")
    val undoneAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "action_ledger_mutations",
    primaryKeys = ["action_ledger_entry_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ActionLedgerEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["action_ledger_entry_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
)
data class ActionLedgerMutationEntity(
    @ColumnInfo(name = "action_ledger_entry_id")
    val actionLedgerEntryId: String,
    val position: Int,
    val operation: String,
    @ColumnInfo(name = "target_type")
    val targetType: String,
    @ColumnInfo(name = "target_id")
    val targetId: String,
    @ColumnInfo(name = "payload_version")
    val payloadVersion: Int,
    @ColumnInfo(name = "before_state")
    val beforeState: String? = null,
    @ColumnInfo(name = "after_state")
    val afterState: String? = null,
)

fun ActionLedgerEntry.toEntity(): ActionLedgerEntryEntity = ActionLedgerEntryEntity(
    id = id.value,
    occurredAtEpochMillis = occurredAt.toEpochMilli(),
    sourceCaptureId = sourceCaptureId?.value,
    undoneAtEpochMillis = undoneAt?.toEpochMilli(),
)

fun ActionLedgerEntry.toMutationEntities(): List<ActionLedgerMutationEntity> = mutations.mapIndexed { position, mutation ->
    ActionLedgerMutationEntity(
        actionLedgerEntryId = id.value,
        position = position,
        operation = mutation.operation.name,
        targetType = mutation.targetType.value,
        targetId = mutation.targetId,
        payloadVersion = mutation.payloadVersion,
        beforeState = mutation.beforeState,
        afterState = mutation.afterState,
    )
}

fun ActionLedgerEntryEntity.toDomain(
    mutations: List<ActionLedgerMutationEntity>,
): ActionLedgerEntry {
    check(mutations.isNotEmpty()) {
        "Action Ledger entry must have at least one persisted mutation"
    }
    check(mutations.map(ActionLedgerMutationEntity::actionLedgerEntryId).all { it == id }) {
        "Action Ledger mutations contain a mismatched parent ID"
    }
    check(mutations.map(ActionLedgerMutationEntity::position) == mutations.indices.toList()) {
        "Action Ledger mutation positions must be contiguous and ordered"
    }

    return ActionLedgerEntry(
        id = ActionLedgerEntryId(id),
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
        sourceCaptureId = sourceCaptureId?.let(::CaptureId),
        mutations = mutations.map { mutation ->
            ActionLedgerMutation(
                operation = ActionLedgerOperation.valueOf(mutation.operation),
                targetType = ActionLedgerTargetType(mutation.targetType),
                targetId = mutation.targetId,
                payloadVersion = mutation.payloadVersion,
                beforeState = mutation.beforeState,
                afterState = mutation.afterState,
            )
        },
        undoneAt = undoneAtEpochMillis?.let(Instant::ofEpochMilli),
    )
}

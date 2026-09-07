package com.edu.quickaside.application.actions

import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant

interface ActionLedgerStore {
    suspend fun record(
        mutations: List<ActionLedgerMutationInput>,
        sourceCaptureId: CaptureId? = null,
    ): ActionLedgerRecordResult

    suspend fun getEntry(id: ActionLedgerEntryId): ActionLedgerEntry?

    suspend fun readRecentEntries(
        limit: Int = RECENT_ACTION_LEDGER_LIMIT,
    ): List<ActionLedgerEntry>

    suspend fun readLatestUndoable(): ActionLedgerEntry?

    suspend fun markUndone(id: ActionLedgerEntryId): ActionLedgerMarkUndoneResult
}

data class ActionLedgerMutationInput(
    val operation: ActionLedgerOperation,
    val targetType: String,
    val targetId: String,
    val payloadVersion: Int,
    val beforeState: String? = null,
    val afterState: String? = null,
) {
    companion object {
        fun fromDomain(mutation: ActionLedgerMutation): ActionLedgerMutationInput =
            ActionLedgerMutationInput(
                operation = mutation.operation,
                targetType = mutation.targetType.value,
                targetId = mutation.targetId,
                payloadVersion = mutation.payloadVersion,
                beforeState = mutation.beforeState,
                afterState = mutation.afterState,
            )
    }
}

fun interface ActionLedgerClock {
    fun now(): Instant
}

interface ActionLedgerIdProvider {
    fun nextEntryId(): ActionLedgerEntryId
}

class RandomActionLedgerIdProvider : ActionLedgerIdProvider {
    override fun nextEntryId(): ActionLedgerEntryId =
        ActionLedgerEntryId(java.util.UUID.randomUUID().toString())
}

enum class ActionLedgerMutationValidationReason {
    BLANK_TARGET_TYPE,
    BLANK_TARGET_ID,
    NON_POSITIVE_PAYLOAD_VERSION,
}

sealed interface ActionLedgerRecordResult {
    data class Saved(val entry: ActionLedgerEntry) : ActionLedgerRecordResult

    data object EmptyMutationBatch : ActionLedgerRecordResult

    data class InvalidMutation(
        val position: Int,
        val reason: ActionLedgerMutationValidationReason,
    ) : ActionLedgerRecordResult

    data object MissingSourceCapture : ActionLedgerRecordResult

    data class Failed(val cause: Exception) : ActionLedgerRecordResult
}

sealed interface ActionLedgerMarkUndoneResult {
    data class MarkedUndone(val entry: ActionLedgerEntry) : ActionLedgerMarkUndoneResult

    data object MissingEntry : ActionLedgerMarkUndoneResult

    data object AlreadyUndone : ActionLedgerMarkUndoneResult

    data class Failed(val cause: Exception) : ActionLedgerMarkUndoneResult
}

const val RECENT_ACTION_LEDGER_LIMIT = 50

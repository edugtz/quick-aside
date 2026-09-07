package com.edu.quickaside.domain.actions

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant

enum class ActionLedgerOperation {
    CREATE,
    UPDATE,
    DELETE,
}

@JvmInline
value class ActionLedgerTargetType(val value: String) {
    init {
        require(value.isNotBlank()) { "Action Ledger target type must not be blank" }
    }
}

data class ActionLedgerMutation(
    val operation: ActionLedgerOperation,
    val targetType: ActionLedgerTargetType,
    val targetId: String,
    val payloadVersion: Int,
    val beforeState: String? = null,
    val afterState: String? = null,
) {
    init {
        require(targetId.isNotBlank()) { "Action Ledger target ID must not be blank" }
        require(payloadVersion > 0) { "Action Ledger payload version must be positive" }
    }
}

data class ActionLedgerEntry(
    val id: ActionLedgerEntryId,
    val occurredAt: Instant,
    val sourceCaptureId: CaptureId? = null,
    val mutations: List<ActionLedgerMutation>,
    val undoneAt: Instant? = null,
) {
    init {
        require(mutations.isNotEmpty()) { "Action Ledger entry must contain at least one mutation" }
    }
}

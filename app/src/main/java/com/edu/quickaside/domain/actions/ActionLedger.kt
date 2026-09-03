package com.edu.quickaside.domain.actions

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import java.time.Instant

data class ActionLedgerEntry(
    val id: ActionLedgerEntryId,
    val occurredAt: Instant,
)


package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.lists.ListItem

/** Executes and reverses only validated CapturePlans made entirely of list-item adds. */
interface CapturePlanListExecutor {
    suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult

    suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedItemIds: List<ListItemId>,
    ): UndoCapturePlanListExecutionResult
}

sealed interface CapturePlanListExecutionResult {
    data class Executed(
        val items: List<ListItem>,
        val actionLedgerEntryId: ActionLedgerEntryId,
    ) : CapturePlanListExecutionResult {
        init {
            require(items.isNotEmpty()) { "An executed CapturePlan must create at least one item" }
        }
    }

    data class UnsupportedAction(
        val actionIndex: Int,
    ) : CapturePlanListExecutionResult {
        init {
            require(actionIndex >= 0) { "CapturePlan action index must not be negative" }
        }
    }

    data class Rejected(
        val actionIndex: Int,
        val reason: CapturePlanListExecutionRejectionReason,
    ) : CapturePlanListExecutionResult {
        init {
            require(actionIndex >= 0) { "CapturePlan action index must not be negative" }
        }
    }

    data object MissingSourceCapture : CapturePlanListExecutionResult

    data class Failed(val cause: Exception) : CapturePlanListExecutionResult
}

enum class CapturePlanListExecutionRejectionReason {
    BLANK_TEXT,
    UNSUPPORTED_LIST_DEFINITION_ID,
    LIST_DEFINITION_CONTRACT_MISMATCH,
    MISSING_DEFINITION,
    NO_ACTIVE_SESSION,
    MISSING_SESSION,
    SESSION_NOT_ACTIVE,
    SESSION_DEFINITION_MISMATCH,
    SESSION_NOT_ALLOWED,
}

sealed interface UndoCapturePlanListExecutionResult {
    data class Undone(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val itemIds: List<ListItemId>,
    ) : UndoCapturePlanListExecutionResult {
        init {
            require(itemIds.isNotEmpty()) { "An executed CapturePlan must contain at least one item" }
            require(itemIds.distinct().size == itemIds.size) {
                "CapturePlan execution item IDs must be unique"
            }
        }
    }

    data object MissingLedgerEntry : UndoCapturePlanListExecutionResult

    data object AlreadyUndone : UndoCapturePlanListExecutionResult

    data class UnsupportedAction(
        val mutationIndex: Int,
    ) : UndoCapturePlanListExecutionResult {
        init {
            require(mutationIndex >= 0) { "Action Ledger mutation index must not be negative" }
        }
    }

    data object UnsupportedLedgerShape : UndoCapturePlanListExecutionResult

    data object TargetMismatch : UndoCapturePlanListExecutionResult

    data class TargetMissing(
        val itemIndex: Int,
    ) : UndoCapturePlanListExecutionResult {
        init {
            require(itemIndex >= 0) { "CapturePlan item index must not be negative" }
        }
    }

    data class Failed(val cause: Exception) : UndoCapturePlanListExecutionResult
}

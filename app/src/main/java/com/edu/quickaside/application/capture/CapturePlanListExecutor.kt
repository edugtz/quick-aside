package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import java.time.Instant

/** Executes and reverses only validated CapturePlans made entirely of list-item adds. */
interface CapturePlanListExecutor {
    suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult

    /**
     * Applies the pending plan captured by a
     * [CapturePlanListExecutionResult.RequiresMandadoSessionChoice] once the user
     * explicitly chose [MandadoSessionChoice.CONTINUE] or
     * [MandadoSessionChoice.NEW]. Resolution re-reads the active Mandado session
     * and safely refuses obsolete choices instead of switching them onto another
     * session.
     */
    suspend fun resolveMandadoSessionChoice(
        requirement: CapturePlanListExecutionResult.RequiresMandadoSessionChoice,
        choice: MandadoSessionChoice,
    ): CapturePlanListExecutionResult

    suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedItemIds: List<ListItemId>,
        autoCreatedMandadoSessionId: ListSessionId? = null,
    ): UndoCapturePlanListExecutionResult
}

/** The focused lifecycle decision for a stale active Mandado session. */
enum class MandadoSessionChoice {
    CONTINUE,
    NEW,
}

sealed interface CapturePlanListExecutionResult {
    data class Executed(
        val items: List<ListItem>,
        val actionLedgerEntryId: ActionLedgerEntryId,
        /**
         * Present only when this execution created the Mandado session. Undo uses
         * it to remove an unused auto-created session without ever touching a
         * session the user already had.
         */
        val autoCreatedMandadoSessionId: ListSessionId? = null,
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

    /**
     * The active Mandado is stale. Nothing was mutated; the same plan must be
     * resolved through [CapturePlanListExecutor.resolveMandadoSessionChoice].
     */
    data class RequiresMandadoSessionChoice(
        val expectedActiveSessionId: ListSessionId,
        val observedLastActivityAt: Instant,
        val plan: CapturePlan,
    ) : CapturePlanListExecutionResult {
        init {
            require(
                plan.actions.any { action ->
                    action is CapturePlanAction.AddListItem &&
                        action.listDefinitionId == BuiltInListDefinitions.MANDADO.id
                },
            ) {
                "A Mandado session choice requirement must contain a Mandado action"
            }
        }
    }

    /**
     * The active session changed between the stale prompt and the user's choice.
     * The obsolete decision must not mutate anything.
     */
    data class MandadoSessionChanged(
        val expectedActiveSessionId: ListSessionId,
    ) : CapturePlanListExecutionResult

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

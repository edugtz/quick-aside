package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task

/** Executes and reverses only validated CapturePlans made entirely of Task creates. */
interface CapturePlanTaskExecutor {
    suspend fun execute(plan: CapturePlan): CapturePlanTaskExecutionResult

    suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskIds: List<TaskId>,
    ): UndoCapturePlanTaskExecutionResult
}

sealed interface CapturePlanTaskExecutionResult {
    data class Executed(
        val tasks: List<Task>,
        val actionLedgerEntryId: ActionLedgerEntryId,
    ) : CapturePlanTaskExecutionResult {
        init {
            require(tasks.isNotEmpty()) { "An executed CapturePlan must create at least one Task" }
            require(tasks.map(Task::id).distinct().size == tasks.size) {
                "CapturePlan execution Task IDs must be unique"
            }
        }
    }

    data class UnsupportedAction(
        val actionIndex: Int,
    ) : CapturePlanTaskExecutionResult {
        init {
            require(actionIndex >= 0) { "CapturePlan action index must not be negative" }
        }
    }

    data class Rejected(
        val actionIndex: Int,
        val reason: CapturePlanTaskExecutionRejectionReason,
    ) : CapturePlanTaskExecutionResult {
        init {
            require(actionIndex >= 0) { "CapturePlan action index must not be negative" }
        }
    }

    data class RejectedPlan(
        val reason: CapturePlanTaskPlanRejectionReason,
    ) : CapturePlanTaskExecutionResult

    data object MissingSourceCapture : CapturePlanTaskExecutionResult

    data class Failed(val cause: Exception) : CapturePlanTaskExecutionResult
}

enum class CapturePlanTaskExecutionRejectionReason {
    BLANK_TITLE,
    TASK_TITLE_TOO_LONG,
}

enum class CapturePlanTaskPlanRejectionReason {
    EMPTY_ACTIONS,
    TOO_MANY_ACTIONS,
}

sealed interface UndoCapturePlanTaskExecutionResult {
    data class Undone(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val taskIds: List<TaskId>,
    ) : UndoCapturePlanTaskExecutionResult {
        init {
            require(taskIds.isNotEmpty()) { "An executed CapturePlan must contain at least one Task" }
            require(taskIds.distinct().size == taskIds.size) {
                "CapturePlan execution Task IDs must be unique"
            }
        }
    }

    data object MissingLedgerEntry : UndoCapturePlanTaskExecutionResult

    data object AlreadyUndone : UndoCapturePlanTaskExecutionResult

    data class UnsupportedAction(
        val mutationIndex: Int,
    ) : UndoCapturePlanTaskExecutionResult {
        init {
            require(mutationIndex >= 0) { "Action Ledger mutation index must not be negative" }
        }
    }

    data object UnsupportedLedgerShape : UndoCapturePlanTaskExecutionResult

    data object TargetMismatch : UndoCapturePlanTaskExecutionResult

    data class TargetMissing(
        val taskIndex: Int,
    ) : UndoCapturePlanTaskExecutionResult {
        init {
            require(taskIndex >= 0) { "CapturePlan Task index must not be negative" }
        }
    }

    data class Failed(val cause: Exception) : UndoCapturePlanTaskExecutionResult
}

package com.edu.quickaside.application.tasks

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.LocalDate

/** The provider-independent application boundary for reversible Task actions. */
interface ReversibleTaskActions {
    suspend fun create(
        title: String,
        space: TaskSpace,
        dueDate: LocalDate? = null,
    ): CreateTaskActionResult

    suspend fun undoCreate(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskId: TaskId,
    ): UndoTaskCreateResult

    suspend fun complete(taskId: TaskId): TaskCompletionActionResult

    suspend fun reopen(taskId: TaskId): TaskCompletionActionResult

    suspend fun undoCompletionChange(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskId: TaskId,
    ): UndoTaskCompletionChangeResult
}

fun interface TaskIdProvider {
    fun nextTaskId(): TaskId
}

class RandomTaskIdProvider : TaskIdProvider {
    override fun nextTaskId(): TaskId = TaskId(java.util.UUID.randomUUID().toString())
}

sealed interface CreateTaskActionResult {
    data class Saved(
        val task: Task,
        val actionLedgerEntryId: ActionLedgerEntryId,
    ) : CreateTaskActionResult

    data object BlankTitle : CreateTaskActionResult

    data class Failed(val cause: Exception) : CreateTaskActionResult
}

sealed interface UndoTaskCreateResult {
    data class Undone(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val taskId: TaskId,
    ) : UndoTaskCreateResult

    data object MissingLedgerEntry : UndoTaskCreateResult

    data object AlreadyUndone : UndoTaskCreateResult

    data object UnsupportedAction : UndoTaskCreateResult

    data object UnsupportedLedgerShape : UndoTaskCreateResult

    data object TargetMismatch : UndoTaskCreateResult

    data object TargetMissing : UndoTaskCreateResult

    data class Failed(val cause: Exception) : UndoTaskCreateResult
}

sealed interface TaskCompletionActionResult {
    data class Changed(
        val task: Task,
        val actionLedgerEntryId: ActionLedgerEntryId,
    ) : TaskCompletionActionResult

    data object MissingTask : TaskCompletionActionResult

    data object AlreadyInRequestedState : TaskCompletionActionResult

    data class Failed(val cause: Exception) : TaskCompletionActionResult
}

sealed interface UndoTaskCompletionChangeResult {
    data class Undone(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val taskId: TaskId,
    ) : UndoTaskCompletionChangeResult

    data object MissingLedgerEntry : UndoTaskCompletionChangeResult

    data object AlreadyUndone : UndoTaskCompletionChangeResult

    data object UnsupportedAction : UndoTaskCompletionChangeResult

    data object UnsupportedLedgerShape : UndoTaskCompletionChangeResult

    data object TargetMismatch : UndoTaskCompletionChangeResult

    data object TargetMissing : UndoTaskCompletionChangeResult

    data object TargetStateMismatch : UndoTaskCompletionChangeResult

    data class Failed(val cause: Exception) : UndoTaskCompletionChangeResult
}

const val TASK_ACTION_LEDGER_TARGET_TYPE = "task"
const val TASK_CREATE_ACTION_PAYLOAD_VERSION = 1
const val TASK_COMPLETION_UPDATE_PAYLOAD_VERSION = 1

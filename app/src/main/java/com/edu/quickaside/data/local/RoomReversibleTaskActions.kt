package com.edu.quickaside.data.local

import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.actions.RandomActionLedgerIdProvider
import com.edu.quickaside.application.tasks.CreateTaskActionResult
import com.edu.quickaside.application.tasks.RandomTaskIdProvider
import com.edu.quickaside.application.tasks.ReversibleTaskActions
import com.edu.quickaside.application.tasks.TASK_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.tasks.TASK_COMPLETION_UPDATE_PAYLOAD_VERSION
import com.edu.quickaside.application.tasks.TASK_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.tasks.TaskCompletionActionResult
import com.edu.quickaside.application.tasks.TaskCompletionLedgerPayloadCodec
import com.edu.quickaside.application.tasks.TaskCompletionLedgerState
import com.edu.quickaside.application.tasks.TaskIdProvider
import com.edu.quickaside.application.tasks.UndoTaskCreateResult
import com.edu.quickaside.application.tasks.UndoTaskCompletionChangeResult
import com.edu.quickaside.application.tasks.epochMillisOrNull
import com.edu.quickaside.application.tasks.isValidTaskCompletionTransition
import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.actions.ActionLedgerTargetType
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

class RoomReversibleTaskActions(
    private val database: QuickAsideDatabase,
    private val taskIdProvider: TaskIdProvider = RandomTaskIdProvider(),
    private val actionLedgerIdProvider: ActionLedgerIdProvider = RandomActionLedgerIdProvider(),
    private val clock: ActionLedgerClock = ActionLedgerClock { Instant.now() },
) : ReversibleTaskActions {
    override suspend fun create(
        title: String,
        space: TaskSpace,
        dueDate: LocalDate?,
    ): CreateTaskActionResult {
        if (title.isBlank()) {
            return CreateTaskActionResult.BlankTitle
        }

        return try {
            database.withWriteTransaction {
                val task = Task(
                    id = taskIdProvider.nextTaskId(),
                    title = title,
                    space = space,
                    dueDate = dueDate,
                    completedAt = null,
                )
                val entry = ActionLedgerEntry(
                    id = actionLedgerIdProvider.nextEntryId(),
                    occurredAt = clock.now(),
                    sourceCaptureId = null,
                    mutations = listOf(
                        ActionLedgerMutation(
                            operation = ActionLedgerOperation.CREATE,
                            targetType = ActionLedgerTargetType(TASK_ACTION_LEDGER_TARGET_TYPE),
                            targetId = task.id.value,
                            payloadVersion = TASK_CREATE_ACTION_PAYLOAD_VERSION,
                        ),
                    ),
                )

                database.taskDao().insertStrict(task.toEntity())
                database.actionLedgerEntryDao().insert(entry.toEntity())
                database.actionLedgerMutationDao().insertAll(entry.toMutationEntities())

                CreateTaskActionResult.Saved(
                    task = task,
                    actionLedgerEntryId = entry.id,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            CreateTaskActionResult.Failed(failure)
        }
    }

    override suspend fun undoCreate(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskId: TaskId,
    ): UndoTaskCreateResult = try {
        database.withWriteTransaction {
            val entry = database.actionLedgerEntryDao().getById(actionLedgerEntryId.value)
                ?: return@withWriteTransaction UndoTaskCreateResult.MissingLedgerEntry
            if (entry.undoneAtEpochMillis != null) {
                return@withWriteTransaction UndoTaskCreateResult.AlreadyUndone
            }

            val mutations = database.actionLedgerMutationDao()
                .getByEntryId(actionLedgerEntryId.value)
            if (mutations.size != 1) {
                return@withWriteTransaction UndoTaskCreateResult.UnsupportedLedgerShape
            }
            val mutation = mutations.single()
            if (
                mutation.operation != ActionLedgerOperation.CREATE.name ||
                mutation.targetType != TASK_ACTION_LEDGER_TARGET_TYPE
            ) {
                return@withWriteTransaction UndoTaskCreateResult.UnsupportedAction
            }
            if (mutation.payloadVersion != TASK_CREATE_ACTION_PAYLOAD_VERSION) {
                return@withWriteTransaction UndoTaskCreateResult.UnsupportedLedgerShape
            }
            if (mutation.targetId != expectedTaskId.value) {
                return@withWriteTransaction UndoTaskCreateResult.TargetMismatch
            }
            if (database.taskDao().getById(expectedTaskId.value) == null) {
                return@withWriteTransaction UndoTaskCreateResult.TargetMissing
            }

            check(database.taskDao().deleteById(expectedTaskId.value) == 1) {
                "Expected exactly one Task delete during Undo"
            }
            check(
                database.actionLedgerEntryDao().markUndone(
                    id = actionLedgerEntryId.value,
                    undoneAtEpochMillis = clock.now().toEpochMilli(),
                ) == 1,
            ) {
                "Expected exactly one Action Ledger entry update during Undo"
            }
            UndoTaskCreateResult.Undone(
                actionLedgerEntryId = actionLedgerEntryId,
                taskId = expectedTaskId,
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        UndoTaskCreateResult.Failed(failure)
    }

    override suspend fun complete(taskId: TaskId): TaskCompletionActionResult =
        changeCompletion(taskId = taskId, requestedCompleted = true)

    override suspend fun reopen(taskId: TaskId): TaskCompletionActionResult =
        changeCompletion(taskId = taskId, requestedCompleted = false)

    private suspend fun changeCompletion(
        taskId: TaskId,
        requestedCompleted: Boolean,
    ): TaskCompletionActionResult = try {
        database.withWriteTransaction {
            val existing = database.taskDao().getById(taskId.value)
                ?: return@withWriteTransaction TaskCompletionActionResult.MissingTask
            val currentCompletedAt = existing.completedAtEpochMillis
            if ((requestedCompleted && currentCompletedAt != null) ||
                (!requestedCompleted && currentCompletedAt == null)
            ) {
                return@withWriteTransaction TaskCompletionActionResult.AlreadyInRequestedState
            }

            val occurredAt = canonicalActionInstant()
            val nextCompletedAt = if (requestedCompleted) occurredAt.toEpochMilli() else null
            val beforeState = currentCompletedAt.toCompletionState()
            val afterState = nextCompletedAt.toCompletionState()
            val entry = completionChangeEntry(
                taskId = taskId,
                occurredAt = occurredAt,
                beforeState = beforeState,
                afterState = afterState,
            )

            check(
                database.taskDao().updateCompletedAtIfMatches(
                    id = taskId.value,
                    expectedCompletedAtEpochMillis = currentCompletedAt,
                    newCompletedAtEpochMillis = nextCompletedAt,
                ) == 1,
            ) {
                "Expected exactly one Task completion update"
            }
            val persisted = database.taskDao().getById(taskId.value)
                ?: error("Changed Task disappeared after completion update")
            database.actionLedgerEntryDao().insert(entry.toEntity())
            database.actionLedgerMutationDao().insertAll(entry.toMutationEntities())

            TaskCompletionActionResult.Changed(
                task = persisted.toDomain(),
                actionLedgerEntryId = entry.id,
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        TaskCompletionActionResult.Failed(failure)
    }

    override suspend fun undoCompletionChange(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskId: TaskId,
    ): UndoTaskCompletionChangeResult = try {
        database.withWriteTransaction {
            val entry = database.actionLedgerEntryDao().getById(actionLedgerEntryId.value)
                ?: return@withWriteTransaction UndoTaskCompletionChangeResult.MissingLedgerEntry
            if (entry.undoneAtEpochMillis != null) {
                return@withWriteTransaction UndoTaskCompletionChangeResult.AlreadyUndone
            }

            val mutations = database.actionLedgerMutationDao()
                .getByEntryId(actionLedgerEntryId.value)
            if (mutations.size != 1) {
                return@withWriteTransaction UndoTaskCompletionChangeResult.UnsupportedLedgerShape
            }
            val mutation = mutations.single()
            if (
                mutation.operation != ActionLedgerOperation.UPDATE.name ||
                mutation.targetType != TASK_ACTION_LEDGER_TARGET_TYPE
            ) {
                return@withWriteTransaction UndoTaskCompletionChangeResult.UnsupportedAction
            }
            if (
                mutation.position != 0 ||
                mutation.payloadVersion != TASK_COMPLETION_UPDATE_PAYLOAD_VERSION ||
                mutation.beforeState == null ||
                mutation.afterState == null
            ) {
                return@withWriteTransaction UndoTaskCompletionChangeResult.UnsupportedLedgerShape
            }
            if (mutation.targetId != expectedTaskId.value) {
                return@withWriteTransaction UndoTaskCompletionChangeResult.TargetMismatch
            }

            val beforeState = TaskCompletionLedgerPayloadCodec.decode(mutation.beforeState)
                ?: return@withWriteTransaction UndoTaskCompletionChangeResult.UnsupportedLedgerShape
            val afterState = TaskCompletionLedgerPayloadCodec.decode(mutation.afterState)
                ?: return@withWriteTransaction UndoTaskCompletionChangeResult.UnsupportedLedgerShape
            if (!isValidTaskCompletionTransition(beforeState, afterState)) {
                return@withWriteTransaction UndoTaskCompletionChangeResult.UnsupportedLedgerShape
            }

            val task = database.taskDao().getById(expectedTaskId.value)
                ?: return@withWriteTransaction UndoTaskCompletionChangeResult.TargetMissing
            if (task.completedAtEpochMillis.toCompletionState() != afterState) {
                return@withWriteTransaction UndoTaskCompletionChangeResult.TargetStateMismatch
            }

            check(
                database.taskDao().updateCompletedAtIfMatches(
                    id = expectedTaskId.value,
                    expectedCompletedAtEpochMillis = afterState.epochMillisOrNull(),
                    newCompletedAtEpochMillis = beforeState.epochMillisOrNull(),
                ) == 1,
            ) {
                "Expected exactly one Task completion restoration"
            }
            check(
                database.actionLedgerEntryDao().markUndone(
                    id = actionLedgerEntryId.value,
                    undoneAtEpochMillis = clock.now().toEpochMilli(),
                ) == 1,
            ) {
                "Expected exactly one Action Ledger entry update during completion Undo"
            }
            UndoTaskCompletionChangeResult.Undone(
                actionLedgerEntryId = actionLedgerEntryId,
                taskId = expectedTaskId,
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        UndoTaskCompletionChangeResult.Failed(failure)
    }

    private fun canonicalActionInstant(): Instant =
        Instant.ofEpochMilli(clock.now().toEpochMilli())

    private fun completionChangeEntry(
        taskId: TaskId,
        occurredAt: Instant,
        beforeState: TaskCompletionLedgerState,
        afterState: TaskCompletionLedgerState,
    ): ActionLedgerEntry = ActionLedgerEntry(
        id = actionLedgerIdProvider.nextEntryId(),
        occurredAt = occurredAt,
        sourceCaptureId = null,
        mutations = listOf(
            ActionLedgerMutation(
                operation = ActionLedgerOperation.UPDATE,
                targetType = ActionLedgerTargetType(TASK_ACTION_LEDGER_TARGET_TYPE),
                targetId = taskId.value,
                payloadVersion = TASK_COMPLETION_UPDATE_PAYLOAD_VERSION,
                beforeState = TaskCompletionLedgerPayloadCodec.encode(beforeState),
                afterState = TaskCompletionLedgerPayloadCodec.encode(afterState),
            ),
        ),
    )

    private fun Long?.toCompletionState(): TaskCompletionLedgerState =
        this?.let(TaskCompletionLedgerState::Completed) ?: TaskCompletionLedgerState.Pending
}

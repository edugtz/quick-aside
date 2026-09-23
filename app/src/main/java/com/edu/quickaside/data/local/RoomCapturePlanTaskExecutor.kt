package com.edu.quickaside.data.local

import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.actions.RandomActionLedgerIdProvider
import com.edu.quickaside.application.capture.CapturePlanTaskExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanTaskExecutionResult
import com.edu.quickaside.application.capture.CapturePlanTaskExecutor
import com.edu.quickaside.application.capture.CapturePlanTaskPlanRejectionReason
import com.edu.quickaside.application.capture.UndoCapturePlanTaskExecutionResult
import com.edu.quickaside.application.tasks.RandomTaskIdProvider
import com.edu.quickaside.application.tasks.TASK_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.tasks.TASK_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.tasks.TaskIdProvider
import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.actions.ActionLedgerTargetType
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.capture.CapturePlanContract
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import java.time.Instant
import kotlinx.coroutines.CancellationException

class RoomCapturePlanTaskExecutor(
    private val database: QuickAsideDatabase,
    private val taskIdProvider: TaskIdProvider = RandomTaskIdProvider(),
    private val actionLedgerIdProvider: ActionLedgerIdProvider = RandomActionLedgerIdProvider(),
    private val clock: ActionLedgerClock = ActionLedgerClock { Instant.now() },
) : CapturePlanTaskExecutor {
    override suspend fun execute(plan: CapturePlan): CapturePlanTaskExecutionResult {
        val actions = plan.actions.toList()
        if (actions.isEmpty()) {
            return CapturePlanTaskExecutionResult.RejectedPlan(
                CapturePlanTaskPlanRejectionReason.EMPTY_ACTIONS,
            )
        }

        val taskActions = ArrayList<CapturePlanAction.CreateTask>(actions.size)
        for ((index, action) in actions.withIndex()) {
            val taskAction = action as? CapturePlanAction.CreateTask
                ?: return CapturePlanTaskExecutionResult.UnsupportedAction(index)
            taskActions += taskAction
        }

        if (actions.size > CapturePlanContract.MAX_ACTIONS) {
            return CapturePlanTaskExecutionResult.RejectedPlan(
                CapturePlanTaskPlanRejectionReason.TOO_MANY_ACTIONS,
            )
        }

        for ((index, action) in taskActions.withIndex()) {
            when {
                action.title.isBlank() -> return CapturePlanTaskExecutionResult.Rejected(
                    actionIndex = index,
                    reason = CapturePlanTaskExecutionRejectionReason.BLANK_TITLE,
                )

                !CapturePlanContract.isWithinCharacterLimit(
                    action.title,
                    CapturePlanContract.MAX_TASK_TITLE_CHARS,
                ) -> return CapturePlanTaskExecutionResult.Rejected(
                    actionIndex = index,
                    reason = CapturePlanTaskExecutionRejectionReason.TASK_TITLE_TOO_LONG,
                )
            }
        }

        return try {
            database.withWriteTransaction {
                if (database.captureDao().getById(plan.sourceCaptureId.value) == null) {
                    return@withWriteTransaction CapturePlanTaskExecutionResult.MissingSourceCapture
                }

                val taskIds = List(taskActions.size) { taskIdProvider.nextTaskId() }
                check(taskIds.all { it.value.isNotBlank() }) {
                    "Task ID provider returned a blank ID"
                }
                check(taskIds.distinct().size == taskIds.size) {
                    "Task ID provider returned duplicate IDs"
                }
                val entryId = actionLedgerIdProvider.nextEntryId()
                check(entryId.value.isNotBlank()) {
                    "Action Ledger ID provider returned a blank ID"
                }

                val tasks = taskActions.mapIndexed { index, action ->
                    Task(
                        id = taskIds[index],
                        title = action.title,
                        space = action.space,
                        dueDate = action.dueDate,
                        completedAt = null,
                    )
                }
                for (task in tasks) {
                    database.taskDao().insertStrict(task.toEntity())
                }

                // Read the clock after Task inserts so cancellation still rolls
                // back writes already made in this Room transaction.
                val occurredAt = clock.now()
                val entry = ActionLedgerEntry(
                    id = entryId,
                    occurredAt = occurredAt,
                    sourceCaptureId = plan.sourceCaptureId,
                    mutations = tasks.map { task ->
                        ActionLedgerMutation(
                            operation = ActionLedgerOperation.CREATE,
                            targetType = ActionLedgerTargetType(TASK_ACTION_LEDGER_TARGET_TYPE),
                            targetId = task.id.value,
                            payloadVersion = TASK_CREATE_ACTION_PAYLOAD_VERSION,
                        )
                    },
                )
                database.actionLedgerEntryDao().insert(entry.toEntity())
                database.actionLedgerMutationDao().insertAll(entry.toMutationEntities())

                CapturePlanTaskExecutionResult.Executed(
                    tasks = tasks,
                    actionLedgerEntryId = entry.id,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            CapturePlanTaskExecutionResult.Failed(failure)
        }
    }

    override suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTaskIds: List<TaskId>,
    ): UndoCapturePlanTaskExecutionResult = try {
        database.withWriteTransaction {
            val entry = database.actionLedgerEntryDao().getById(actionLedgerEntryId.value)
                ?: return@withWriteTransaction UndoCapturePlanTaskExecutionResult.MissingLedgerEntry
            if (entry.undoneAtEpochMillis != null) {
                return@withWriteTransaction UndoCapturePlanTaskExecutionResult.AlreadyUndone
            }
            if (entry.sourceCaptureId.isNullOrBlank()) {
                return@withWriteTransaction UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape
            }

            val mutations = database.actionLedgerMutationDao()
                .getByEntryId(actionLedgerEntryId.value)
            if (mutations.isEmpty() || mutations.map { it.position } != mutations.indices.toList()) {
                return@withWriteTransaction UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape
            }

            val unsupportedMutationIndex = mutations.indexOfFirst { mutation ->
                mutation.operation != ActionLedgerOperation.CREATE.name ||
                    mutation.targetType != TASK_ACTION_LEDGER_TARGET_TYPE
            }
            if (unsupportedMutationIndex >= 0) {
                return@withWriteTransaction UndoCapturePlanTaskExecutionResult.UnsupportedAction(
                    mutationIndex = unsupportedMutationIndex,
                )
            }

            val targetIds = mutations.map { it.targetId }
            if (
                mutations.any { mutation ->
                    mutation.payloadVersion != TASK_CREATE_ACTION_PAYLOAD_VERSION ||
                        mutation.beforeState != null ||
                        mutation.afterState != null
                } ||
                targetIds.any(String::isBlank) ||
                targetIds.distinct().size != targetIds.size
            ) {
                return@withWriteTransaction UndoCapturePlanTaskExecutionResult.UnsupportedLedgerShape
            }

            if (targetIds != expectedTaskIds.map(TaskId::value)) {
                return@withWriteTransaction UndoCapturePlanTaskExecutionResult.TargetMismatch
            }

            for ((index, targetId) in targetIds.withIndex()) {
                if (database.taskDao().getById(targetId) == null) {
                    return@withWriteTransaction UndoCapturePlanTaskExecutionResult.TargetMissing(index)
                }
            }

            for (targetId in targetIds) {
                check(database.taskDao().deleteById(targetId) == 1) {
                    "Expected exactly one Task delete during CapturePlan Undo"
                }
            }
            check(
                database.actionLedgerEntryDao().markUndone(
                    id = actionLedgerEntryId.value,
                    undoneAtEpochMillis = clock.now().toEpochMilli(),
                ) == 1,
            ) {
                "Expected exactly one Action Ledger entry update during CapturePlan Undo"
            }

            UndoCapturePlanTaskExecutionResult.Undone(
                actionLedgerEntryId = actionLedgerEntryId,
                taskIds = targetIds.map(::TaskId),
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        UndoCapturePlanTaskExecutionResult.Failed(failure)
    }
}

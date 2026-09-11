package com.edu.quickaside.data.local

import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.actions.RandomActionLedgerIdProvider
import com.edu.quickaside.application.tasks.CreateTaskActionResult
import com.edu.quickaside.application.tasks.RandomTaskIdProvider
import com.edu.quickaside.application.tasks.ReversibleTaskActions
import com.edu.quickaside.application.tasks.TASK_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.tasks.TASK_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.tasks.TaskIdProvider
import com.edu.quickaside.application.tasks.UndoTaskCreateResult
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
}

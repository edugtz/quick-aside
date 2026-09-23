package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CapturePlanTaskExecutorContractTest {
    @Test
    fun executionOutcomesPreserveIndexedAndPlanLevelRejections() {
        val unsupported = CapturePlanTaskExecutionResult.UnsupportedAction(actionIndex = 1)
        assertEquals(1, unsupported.actionIndex)

        val rejected = CapturePlanTaskExecutionResult.Rejected(
            actionIndex = 2,
            reason = CapturePlanTaskExecutionRejectionReason.TASK_TITLE_TOO_LONG,
        )
        assertEquals(2, rejected.actionIndex)
        assertEquals(CapturePlanTaskExecutionRejectionReason.TASK_TITLE_TOO_LONG, rejected.reason)
        assertEquals(
            CapturePlanTaskPlanRejectionReason.TOO_MANY_ACTIONS,
            (CapturePlanTaskExecutionResult.RejectedPlan(
                CapturePlanTaskPlanRejectionReason.TOO_MANY_ACTIONS,
            )).reason,
        )

        assertThrows(IllegalArgumentException::class.java) {
            CapturePlanTaskExecutionResult.UnsupportedAction(actionIndex = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapturePlanTaskExecutionResult.Rejected(
                actionIndex = -1,
                reason = CapturePlanTaskExecutionRejectionReason.BLANK_TITLE,
            )
        }
    }

    @Test
    fun undoOutcomesPreserveMutationAndTaskIndexes() {
        val unsupported = UndoCapturePlanTaskExecutionResult.UnsupportedAction(mutationIndex = 2)
        assertEquals(2, unsupported.mutationIndex)

        val targetMissing = UndoCapturePlanTaskExecutionResult.TargetMissing(taskIndex = 1)
        assertEquals(1, targetMissing.taskIndex)

        assertThrows(IllegalArgumentException::class.java) {
            UndoCapturePlanTaskExecutionResult.UnsupportedAction(mutationIndex = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UndoCapturePlanTaskExecutionResult.TargetMissing(taskIndex = -1)
        }
    }

    @Test
    fun successOutcomesRequireNonEmptyUniqueTaskBatches() {
        assertThrows(IllegalArgumentException::class.java) {
            CapturePlanTaskExecutionResult.Executed(
                tasks = emptyList(),
                actionLedgerEntryId = ActionLedgerEntryId("entry"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapturePlanTaskExecutionResult.Executed(
                tasks = listOf(task("same"), task("same")),
                actionLedgerEntryId = ActionLedgerEntryId("entry"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UndoCapturePlanTaskExecutionResult.Undone(
                actionLedgerEntryId = ActionLedgerEntryId("entry"),
                taskIds = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UndoCapturePlanTaskExecutionResult.Undone(
                actionLedgerEntryId = ActionLedgerEntryId("entry"),
                taskIds = listOf(TaskId("same"), TaskId("same")),
            )
        }
    }

    @Test
    fun successRetainsExactTasksAndOrderedIdentities() {
        val tasks = listOf(
            task(id = "personal", title = "  exact title  "),
            task(
                id = "trabajo",
                title = "duplicate title",
                space = TaskSpace.TRABAJO,
                dueDate = LocalDate.of(2026, 10, 2),
            ),
            task(id = "duplicate", title = "duplicate title"),
        )
        val entryId = ActionLedgerEntryId("exact-entry")

        val executed = CapturePlanTaskExecutionResult.Executed(tasks, entryId)
        val undone = UndoCapturePlanTaskExecutionResult.Undone(
            actionLedgerEntryId = entryId,
            taskIds = tasks.map(Task::id),
        )

        assertEquals(tasks, executed.tasks)
        assertEquals(entryId, executed.actionLedgerEntryId)
        assertEquals(tasks.map(Task::id), undone.taskIds)
        assertEquals(entryId, undone.actionLedgerEntryId)
    }

    private fun task(
        id: String,
        title: String = id,
        space: TaskSpace = TaskSpace.PERSONAL,
        dueDate: LocalDate? = null,
    ): Task = Task(
        id = TaskId(id),
        title = title,
        space = space,
        dueDate = dueDate,
        completedAt = null,
    )
}

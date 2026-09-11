package com.edu.quickaside.application.tasks

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReversibleTaskActionsContractTest {
    @Test
    fun savedResultCarriesExactTaskAndLedgerReceiptIdentity() {
        val task = Task(
            id = TaskId("task-1"),
            title = "  Revisar integración  ",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 9, 17),
            completedAt = null,
        )
        val result = CreateTaskActionResult.Saved(
            task = task,
            actionLedgerEntryId = ActionLedgerEntryId("entry-1"),
        )

        assertEquals(task, result.task)
        assertEquals(ActionLedgerEntryId("entry-1"), result.actionLedgerEntryId)
        assertEquals(null, task.completedAt)
    }

    @Test
    fun boundaryKeepsTaskCreateLedgerShapeConstantsExplicit() {
        assertEquals("task", TASK_ACTION_LEDGER_TARGET_TYPE)
        assertEquals(1, TASK_CREATE_ACTION_PAYLOAD_VERSION)
        val undone = UndoTaskCreateResult.Undone(
            actionLedgerEntryId = ActionLedgerEntryId("entry-1"),
            taskId = TaskId("task-1"),
        )
        assertEquals(ActionLedgerEntryId("entry-1"), undone.actionLedgerEntryId)
        assertEquals(TaskId("task-1"), undone.taskId)
    }

    @Test
    fun typedResultsRepresentBlankTitleAndFailure() {
        assertEquals(CreateTaskActionResult.BlankTitle, CreateTaskActionResult.BlankTitle)
        val cause = IllegalStateException("persistence failure")
        val failure = CreateTaskActionResult.Failed(cause)
        assertEquals(cause, failure.cause)
    }
}

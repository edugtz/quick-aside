package com.edu.quickaside.application.tasks

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReversibleTaskCompletionActionsContractTest {
    @Test
    fun changedResultCarriesExactPersistedTaskAndLedgerReceiptIdentity() {
        val task = Task(
            id = TaskId("task-1"),
            title = "  Revisar integración  ",
            space = TaskSpace.TRABAJO,
            completedAt = Instant.parse("2026-09-12T10:11:12.123Z"),
        )
        val result = TaskCompletionActionResult.Changed(
            task = task,
            actionLedgerEntryId = ActionLedgerEntryId("entry-1"),
        )

        assertEquals(task, result.task)
        assertEquals(ActionLedgerEntryId("entry-1"), result.actionLedgerEntryId)
    }

    @Test
    fun completionAndUndoOutcomesExposeDeterministicNoOpAndValidationStates() {
        assertEquals(
            TaskCompletionActionResult.AlreadyInRequestedState,
            TaskCompletionActionResult.AlreadyInRequestedState,
        )
        assertEquals(
            UndoTaskCompletionChangeResult.TargetStateMismatch,
            UndoTaskCompletionChangeResult.TargetStateMismatch,
        )
        assertEquals(
            UndoTaskCompletionChangeResult.Undone(
                actionLedgerEntryId = ActionLedgerEntryId("entry-1"),
                taskId = TaskId("task-1"),
            ),
            UndoTaskCompletionChangeResult.Undone(
                actionLedgerEntryId = ActionLedgerEntryId("entry-1"),
                taskId = TaskId("task-1"),
            ),
        )
    }

    @Test
    fun completionUpdatePayloadVersionRemainsExplicitAndSeparateFromCreate() {
        assertEquals("task", TASK_ACTION_LEDGER_TARGET_TYPE)
        assertEquals(1, TASK_CREATE_ACTION_PAYLOAD_VERSION)
        assertEquals(1, TASK_COMPLETION_UPDATE_PAYLOAD_VERSION)
        assertTrue(TASK_CREATE_ACTION_PAYLOAD_VERSION == TASK_COMPLETION_UPDATE_PAYLOAD_VERSION)
    }
}

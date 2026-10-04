package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId

/** Executes only Note/Structured Log creates, preserving their shared action order. */
interface CapturePlanMemoryExecutor {
    suspend fun execute(plan: CapturePlan): CapturePlanMemoryExecutionResult

    /** Reverses this exact receipt; never selects a latest action implicitly. */
    suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTargets: List<MemoryExecutionTarget>,
    ): UndoCapturePlanMemoryExecutionResult
}

sealed interface MemoryExecutionTarget {
    data class Note(val id: NoteId) : MemoryExecutionTarget
    data class StructuredLog(val id: StructuredLogId) : MemoryExecutionTarget
}

sealed interface CreatedMemoryRecord {
    val target: MemoryExecutionTarget

    data class Note(val note: com.edu.quickaside.domain.memory.Note) : CreatedMemoryRecord {
        override val target: MemoryExecutionTarget get() = MemoryExecutionTarget.Note(note.id)
    }

    data class StructuredLog(
        val log: com.edu.quickaside.domain.memory.StructuredLog,
    ) : CreatedMemoryRecord {
        override val target: MemoryExecutionTarget get() = MemoryExecutionTarget.StructuredLog(log.id)
    }
}

sealed interface CapturePlanMemoryExecutionResult {
    data class Executed(
        val records: List<CreatedMemoryRecord>,
        val actionLedgerEntryId: ActionLedgerEntryId,
    ) : CapturePlanMemoryExecutionResult {
        init {
            require(records.isNotEmpty()) { "An executed Memory plan must create at least one record" }
            require(records.map { it.target }.distinct().size == records.size) {
                "Memory execution targets must be unique"
            }
        }
    }

    data class UnsupportedAction(val actionIndex: Int) : CapturePlanMemoryExecutionResult

    data class Rejected(
        val actionIndex: Int,
        val reason: CapturePlanMemoryExecutionRejectionReason,
    ) : CapturePlanMemoryExecutionResult

    data class RejectedPlan(
        val reason: CapturePlanMemoryPlanRejectionReason,
    ) : CapturePlanMemoryExecutionResult

    data object MissingSourceCapture : CapturePlanMemoryExecutionResult
    data class Failed(val cause: Exception) : CapturePlanMemoryExecutionResult
}

enum class CapturePlanMemoryExecutionRejectionReason {
    BLANK_NOTE_TEXT,
    NOTE_TEXT_TOO_LONG,
    EMPTY_FIELDS,
    TOO_MANY_FIELDS,
    BLANK_FIELD_KEY,
    BLANK_FIELD_VALUE,
    FIELD_KEY_TOO_LONG,
    FIELD_VALUE_TOO_LONG,
}

enum class CapturePlanMemoryPlanRejectionReason {
    EMPTY_ACTIONS,
    TOO_MANY_ACTIONS,
}

sealed interface UndoCapturePlanMemoryExecutionResult {
    data class Undone(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val targets: List<MemoryExecutionTarget>,
    ) : UndoCapturePlanMemoryExecutionResult

    data object MissingLedgerEntry : UndoCapturePlanMemoryExecutionResult
    data object AlreadyUndone : UndoCapturePlanMemoryExecutionResult
    data class UnsupportedAction(val mutationIndex: Int) : UndoCapturePlanMemoryExecutionResult
    data object UnsupportedLedgerShape : UndoCapturePlanMemoryExecutionResult
    data object TargetMismatch : UndoCapturePlanMemoryExecutionResult
    data class TargetMissing(val recordIndex: Int) : UndoCapturePlanMemoryExecutionResult
    data class Failed(val cause: Exception) : UndoCapturePlanMemoryExecutionResult
}

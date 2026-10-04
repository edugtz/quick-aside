package com.edu.quickaside.data.local

import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.actions.ActionLedgerClock
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.actions.RandomActionLedgerIdProvider
import com.edu.quickaside.application.capture.CapturePlanMemoryExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanMemoryExecutionResult
import com.edu.quickaside.application.capture.CapturePlanMemoryExecutor
import com.edu.quickaside.application.capture.CapturePlanMemoryPlanRejectionReason
import com.edu.quickaside.application.capture.CreatedMemoryRecord
import com.edu.quickaside.application.capture.MemoryExecutionTarget
import com.edu.quickaside.application.capture.UndoCapturePlanMemoryExecutionResult
import com.edu.quickaside.application.memory.MemoryIdProvider
import com.edu.quickaside.application.memory.NOTE_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.memory.NOTE_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.memory.RandomMemoryIdProvider
import com.edu.quickaside.application.memory.STRUCTURED_LOG_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.memory.STRUCTURED_LOG_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.actions.ActionLedgerTargetType
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.capture.CapturePlanContract
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import java.time.Instant
import kotlinx.coroutines.CancellationException

class RoomCapturePlanMemoryExecutor(
    private val database: QuickAsideDatabase,
    private val memoryIdProvider: MemoryIdProvider = RandomMemoryIdProvider(),
    private val actionLedgerIdProvider: ActionLedgerIdProvider = RandomActionLedgerIdProvider(),
    private val clock: ActionLedgerClock = ActionLedgerClock { Instant.now() },
) : CapturePlanMemoryExecutor {
    override suspend fun execute(plan: CapturePlan): CapturePlanMemoryExecutionResult {
        val actions = plan.actions.toList()
        if (actions.isEmpty()) {
            return CapturePlanMemoryExecutionResult.RejectedPlan(
                CapturePlanMemoryPlanRejectionReason.EMPTY_ACTIONS,
            )
        }
        if (actions.size > CapturePlanContract.MAX_ACTIONS) {
            return CapturePlanMemoryExecutionResult.RejectedPlan(
                CapturePlanMemoryPlanRejectionReason.TOO_MANY_ACTIONS,
            )
        }

        // Snapshot mutable field maps as well as the action list before the first suspension.
        val validatedActions = ArrayList<CapturePlanAction>(actions.size)
        for ((index, action) in actions.withIndex()) {
            val reason = when (action) {
                is CapturePlanAction.CreateNote -> when {
                    action.text.isBlank() -> CapturePlanMemoryExecutionRejectionReason.BLANK_NOTE_TEXT
                    !CapturePlanContract.isWithinCharacterLimit(action.text, CapturePlanContract.MAX_NOTE_CHARS) ->
                        CapturePlanMemoryExecutionRejectionReason.NOTE_TEXT_TOO_LONG
                    else -> null
                }.also { if (it == null) validatedActions += action }
                is CapturePlanAction.CreateStructuredLog -> {
                    val fields = action.fields.toMap()
                    validateFields(fields).also {
                        if (it == null) validatedActions += CapturePlanAction.CreateStructuredLog(fields)
                    }
                }
                else -> return CapturePlanMemoryExecutionResult.UnsupportedAction(index)
            }
            if (reason != null) return CapturePlanMemoryExecutionResult.Rejected(index, reason)
        }

        return try {
            database.withWriteTransaction {
                if (database.captureDao().getById(plan.sourceCaptureId.value) == null) {
                    return@withWriteTransaction CapturePlanMemoryExecutionResult.MissingSourceCapture
                }
                val occurredAt = clock.now()
                val records = validatedActions.map { action ->
                    when (action) {
                        is CapturePlanAction.CreateNote -> {
                            val note = Note(
                                id = memoryIdProvider.nextNoteId(),
                                text = action.text,
                                sourceCaptureId = plan.sourceCaptureId,
                                createdAt = occurredAt,
                            )
                            check(note.id.value.isNotBlank()) { "Memory ID provider returned a blank Note ID" }
                            database.noteDao().insert(note.toEntity())
                            CreatedMemoryRecord.Note(note)
                        }
                        is CapturePlanAction.CreateStructuredLog -> {
                            val log = StructuredLog(
                                id = memoryIdProvider.nextStructuredLogId(),
                                fields = action.fields,
                                sourceCaptureId = plan.sourceCaptureId,
                                createdAt = occurredAt,
                            )
                            check(log.id.value.isNotBlank()) { "Memory ID provider returned a blank Structured Log ID" }
                            database.structuredLogDao().insert(log.toEntity())
                            database.structuredLogFieldDao().insertAll(log.toFieldEntities())
                            CreatedMemoryRecord.StructuredLog(log)
                        }
                        else -> error("Unsupported action passed Memory validation")
                    }
                }
                val entry = ActionLedgerEntry(
                    id = actionLedgerIdProvider.nextEntryId(),
                    occurredAt = occurredAt,
                    sourceCaptureId = plan.sourceCaptureId,
                    mutations = records.map { record ->
                        val target = record.target
                        ActionLedgerMutation(
                            operation = ActionLedgerOperation.CREATE,
                            targetType = ActionLedgerTargetType(target.ledgerType),
                            targetId = target.idValue,
                            payloadVersion = target.payloadVersion,
                        )
                    },
                )
                check(entry.id.value.isNotBlank()) { "Action Ledger ID provider returned a blank ID" }
                database.actionLedgerEntryDao().insert(entry.toEntity())
                database.actionLedgerMutationDao().insertAll(entry.toMutationEntities())
                CapturePlanMemoryExecutionResult.Executed(records, entry.id)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            CapturePlanMemoryExecutionResult.Failed(failure)
        }
    }

    override suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedTargets: List<MemoryExecutionTarget>,
    ): UndoCapturePlanMemoryExecutionResult {
        val expected = expectedTargets.toList()
        return try {
            database.withWriteTransaction {
                val entry = database.actionLedgerEntryDao().getById(actionLedgerEntryId.value)
                    ?: return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.MissingLedgerEntry
                if (entry.undoneAtEpochMillis != null) {
                    return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.AlreadyUndone
                }
                val mutations = database.actionLedgerMutationDao().getByEntryId(actionLedgerEntryId.value)
                if (
                    entry.sourceCaptureId.isNullOrBlank() || mutations.isEmpty() ||
                    mutations.size > CapturePlanContract.MAX_ACTIONS ||
                    mutations.map { it.position } != mutations.indices.toList()
                ) {
                    return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.UnsupportedLedgerShape
                }
                val targets = ArrayList<MemoryExecutionTarget>(mutations.size)
                for ((index, mutation) in mutations.withIndex()) {
                    if (
                        mutation.operation != ActionLedgerOperation.CREATE.name ||
                        mutation.targetType !in setOf(NOTE_ACTION_LEDGER_TARGET_TYPE, STRUCTURED_LOG_ACTION_LEDGER_TARGET_TYPE)
                    ) {
                        return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.UnsupportedAction(index)
                    }
                    if (mutation.targetId.isBlank() || mutation.beforeState != null || mutation.afterState != null) {
                        return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.UnsupportedLedgerShape
                    }
                    val target = when (mutation.targetType) {
                        NOTE_ACTION_LEDGER_TARGET_TYPE -> MemoryExecutionTarget.Note(NoteId(mutation.targetId))
                        else -> MemoryExecutionTarget.StructuredLog(StructuredLogId(mutation.targetId))
                    }
                    if (mutation.payloadVersion != target.payloadVersion) {
                        return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.UnsupportedLedgerShape
                    }
                    targets += target
                }
                if (targets.distinct().size != targets.size) {
                    return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.UnsupportedLedgerShape
                }
                if (targets != expected) {
                    return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.TargetMismatch
                }
                for ((index, target) in targets.withIndex()) {
                    val sourceCaptureId = when (target) {
                        is MemoryExecutionTarget.Note -> {
                            val note = database.noteDao().getById(target.id.value)
                                ?: return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.TargetMissing(index)
                            note.sourceCaptureId
                        }
                        is MemoryExecutionTarget.StructuredLog -> {
                            val log = database.structuredLogDao().getById(target.id.value)
                                ?: return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.TargetMissing(index)
                            log.sourceCaptureId
                        }
                    }
                    if (sourceCaptureId != entry.sourceCaptureId) {
                        return@withWriteTransaction UndoCapturePlanMemoryExecutionResult.TargetMismatch
                    }
                }
                // Every shape/target check above completes before any delete.
                for (target in targets.asReversed()) {
                    val deleted = when (target) {
                        is MemoryExecutionTarget.Note -> database.noteDao().deleteById(target.id.value)
                        is MemoryExecutionTarget.StructuredLog -> {
                            database.structuredLogFieldDao().deleteByStructuredLogId(target.id.value)
                            database.structuredLogDao().deleteById(target.id.value)
                        }
                    }
                    check(deleted == 1) { "Expected exactly one Memory record delete during CapturePlan Undo" }
                }
                check(database.actionLedgerEntryDao().markUndone(actionLedgerEntryId.value, clock.now().toEpochMilli()) == 1) {
                    "Expected exactly one Action Ledger entry update during CapturePlan Undo"
                }
                UndoCapturePlanMemoryExecutionResult.Undone(actionLedgerEntryId, targets)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            UndoCapturePlanMemoryExecutionResult.Failed(failure)
        }
    }

    private fun validateFields(fields: Map<String, String>): CapturePlanMemoryExecutionRejectionReason? = when {
        fields.isEmpty() -> CapturePlanMemoryExecutionRejectionReason.EMPTY_FIELDS
        fields.size > CapturePlanContract.MAX_STRUCTURED_LOG_FIELDS -> CapturePlanMemoryExecutionRejectionReason.TOO_MANY_FIELDS
        fields.keys.any(String::isBlank) -> CapturePlanMemoryExecutionRejectionReason.BLANK_FIELD_KEY
        fields.values.any(String::isBlank) -> CapturePlanMemoryExecutionRejectionReason.BLANK_FIELD_VALUE
        fields.keys.any { !CapturePlanContract.isWithinCharacterLimit(it, CapturePlanContract.MAX_STRUCTURED_LOG_KEY_CHARS) } ->
            CapturePlanMemoryExecutionRejectionReason.FIELD_KEY_TOO_LONG
        fields.values.any { !CapturePlanContract.isWithinCharacterLimit(it, CapturePlanContract.MAX_STRUCTURED_LOG_VALUE_CHARS) } ->
            CapturePlanMemoryExecutionRejectionReason.FIELD_VALUE_TOO_LONG
        else -> null
    }
}

private val MemoryExecutionTarget.idValue: String
    get() = when (this) {
        is MemoryExecutionTarget.Note -> id.value
        is MemoryExecutionTarget.StructuredLog -> id.value
    }

private val MemoryExecutionTarget.ledgerType: String
    get() = when (this) {
        is MemoryExecutionTarget.Note -> NOTE_ACTION_LEDGER_TARGET_TYPE
        is MemoryExecutionTarget.StructuredLog -> STRUCTURED_LOG_ACTION_LEDGER_TARGET_TYPE
    }

private val MemoryExecutionTarget.payloadVersion: Int
    get() = when (this) {
        is MemoryExecutionTarget.Note -> NOTE_CREATE_ACTION_PAYLOAD_VERSION
        is MemoryExecutionTarget.StructuredLog -> STRUCTURED_LOG_CREATE_ACTION_PAYLOAD_VERSION
    }

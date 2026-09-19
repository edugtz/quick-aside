package com.edu.quickaside.data.local

import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.actions.RandomActionLedgerIdProvider
import com.edu.quickaside.application.capture.CapturePlanListExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanListExecutionResult
import com.edu.quickaside.application.capture.CapturePlanListExecutor
import com.edu.quickaside.application.capture.UndoCapturePlanListExecutionResult
import com.edu.quickaside.application.lists.LIST_ITEM_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.lists.LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListItemIdProvider
import com.edu.quickaside.application.lists.RandomListItemIdProvider
import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.actions.ActionLedgerTargetType
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.capture.CapturePlanContract
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import java.time.Instant
import kotlinx.coroutines.CancellationException

class RoomCapturePlanListExecutor(
    private val database: QuickAsideDatabase,
    private val itemIdProvider: ListItemIdProvider = RandomListItemIdProvider(),
    private val actionLedgerIdProvider: ActionLedgerIdProvider = RandomActionLedgerIdProvider(),
    private val clock: ListClock = ListClock { Instant.now() },
) : CapturePlanListExecutor {
    override suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult {
        val actions = plan.actions.toList()
        for ((index, action) in actions.withIndex()) {
            when (action) {
                is CapturePlanAction.AddListItem -> {
                    if (action.listDefinitionId.value !in
                        CapturePlanContract.SUPPORTED_LIST_DEFINITION_IDS
                    ) {
                        return CapturePlanListExecutionResult.Rejected(
                            actionIndex = index,
                            reason = CapturePlanListExecutionRejectionReason.UNSUPPORTED_LIST_DEFINITION_ID,
                        )
                    }
                }

                else -> return CapturePlanListExecutionResult.UnsupportedAction(index)
            }
        }

        return try {
            database.withWriteTransaction {
                if (database.captureDao().getById(plan.sourceCaptureId.value) == null) {
                    return@withWriteTransaction CapturePlanListExecutionResult.MissingSourceCapture
                }

                val validatedActions = ArrayList<ValidatedListAction>(actions.size)
                for ((index, action) in actions.withIndex()) {
                    val addListItem = action as? CapturePlanAction.AddListItem
                        ?: error("CapturePlan action changed after execution preflight")
                    when (
                        val validation = database.validateListItemCreate(
                            listDefinitionId = addListItem.listDefinitionId,
                            text = addListItem.text,
                            listSessionId = null,
                        )
                    ) {
                        is ListItemCreateValidation.Valid -> {
                            val expectedDefinition = BuiltInListDefinitions.ALL
                                .single { it.id == addListItem.listDefinitionId }
                            if (validation.definition != expectedDefinition) {
                                return@withWriteTransaction CapturePlanListExecutionResult.Rejected(
                                    actionIndex = index,
                                    reason = CapturePlanListExecutionRejectionReason
                                        .LIST_DEFINITION_CONTRACT_MISMATCH,
                                )
                            }
                            validatedActions += ValidatedListAction(
                                action = addListItem,
                                listSessionId = validation.listSessionId,
                            )
                        }

                        else -> return@withWriteTransaction CapturePlanListExecutionResult.Rejected(
                            actionIndex = index,
                            reason = validation.toExecutionRejectionReason(),
                        )
                    }
                }

                val occurredAt = clock.now()
                val items = ArrayList<ListItem>(validatedActions.size)
                for (validated in validatedActions) {
                    val itemId = itemIdProvider.nextItemId()
                    check(itemId.value.isNotBlank()) { "ListItem ID provider returned a blank ID" }
                    val item = ListItem(
                        id = itemId,
                        listDefinitionId = validated.action.listDefinitionId,
                        text = validated.action.text,
                        listSessionId = validated.listSessionId,
                        isCompleted = false,
                        createdAt = occurredAt,
                    )
                    database.listItemDao().insert(item.toEntity())
                    items += item
                }

                val actionLedgerEntryId = actionLedgerIdProvider.nextEntryId()
                check(actionLedgerEntryId.value.isNotBlank()) {
                    "Action Ledger ID provider returned a blank ID"
                }
                val entry = ActionLedgerEntry(
                    id = actionLedgerEntryId,
                    occurredAt = occurredAt,
                    sourceCaptureId = plan.sourceCaptureId,
                    mutations = items.map { item ->
                        ActionLedgerMutation(
                            operation = ActionLedgerOperation.CREATE,
                            targetType = ActionLedgerTargetType(
                                LIST_ITEM_ACTION_LEDGER_TARGET_TYPE,
                            ),
                            targetId = item.id.value,
                            payloadVersion = LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION,
                            beforeState = null,
                            afterState = null,
                        )
                    },
                )
                database.actionLedgerEntryDao().insert(entry.toEntity())
                database.actionLedgerMutationDao().insertAll(entry.toMutationEntities())

                CapturePlanListExecutionResult.Executed(
                    items = items,
                    actionLedgerEntryId = entry.id,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            CapturePlanListExecutionResult.Failed(failure)
        }
    }

    override suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedItemIds: List<ListItemId>,
    ): UndoCapturePlanListExecutionResult = try {
        database.withWriteTransaction {
            val entry = database.actionLedgerEntryDao().getById(actionLedgerEntryId.value)
                ?: return@withWriteTransaction UndoCapturePlanListExecutionResult.MissingLedgerEntry
            if (entry.undoneAtEpochMillis != null) {
                return@withWriteTransaction UndoCapturePlanListExecutionResult.AlreadyUndone
            }
            if (entry.sourceCaptureId.isNullOrBlank()) {
                return@withWriteTransaction UndoCapturePlanListExecutionResult.UnsupportedLedgerShape
            }

            val mutations = database.actionLedgerMutationDao()
                .getByEntryId(actionLedgerEntryId.value)
            if (mutations.isEmpty()) {
                return@withWriteTransaction UndoCapturePlanListExecutionResult.UnsupportedLedgerShape
            }
            if (mutations.map { it.position } != mutations.indices.toList()) {
                return@withWriteTransaction UndoCapturePlanListExecutionResult.UnsupportedLedgerShape
            }

            val unsupportedMutationIndex = mutations.indexOfFirst { mutation ->
                mutation.operation != ActionLedgerOperation.CREATE.name ||
                    mutation.targetType != LIST_ITEM_ACTION_LEDGER_TARGET_TYPE
            }
            if (unsupportedMutationIndex >= 0) {
                return@withWriteTransaction UndoCapturePlanListExecutionResult.UnsupportedAction(
                    mutationIndex = unsupportedMutationIndex,
                )
            }

            val targetIds = mutations.map { it.targetId }
            if (
                mutations.any { mutation ->
                    mutation.payloadVersion != LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION ||
                        mutation.beforeState != null ||
                        mutation.afterState != null
                } ||
                targetIds.any(String::isBlank) ||
                targetIds.distinct().size != targetIds.size
            ) {
                return@withWriteTransaction UndoCapturePlanListExecutionResult.UnsupportedLedgerShape
            }

            if (targetIds != expectedItemIds.map(ListItemId::value)) {
                return@withWriteTransaction UndoCapturePlanListExecutionResult.TargetMismatch
            }

            for ((index, targetId) in targetIds.withIndex()) {
                if (database.listItemDao().getById(targetId) == null) {
                    return@withWriteTransaction UndoCapturePlanListExecutionResult.TargetMissing(index)
                }
            }

            for (targetId in targetIds) {
                check(database.listItemDao().deleteById(targetId) == 1) {
                    "Expected exactly one ListItem delete during CapturePlan Undo"
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

            UndoCapturePlanListExecutionResult.Undone(
                actionLedgerEntryId = actionLedgerEntryId,
                itemIds = targetIds.map(::ListItemId),
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        UndoCapturePlanListExecutionResult.Failed(failure)
    }

    private data class ValidatedListAction(
        val action: CapturePlanAction.AddListItem,
        val listSessionId: ListSessionId?,
    )

    private fun ListItemCreateValidation.toExecutionRejectionReason():
        CapturePlanListExecutionRejectionReason = when (this) {
        is ListItemCreateValidation.Valid -> error("Valid list action has no rejection reason")
        ListItemCreateValidation.BlankText -> CapturePlanListExecutionRejectionReason.BLANK_TEXT
        ListItemCreateValidation.MissingDefinition ->
            CapturePlanListExecutionRejectionReason.MISSING_DEFINITION
        ListItemCreateValidation.NoActiveSession ->
            CapturePlanListExecutionRejectionReason.NO_ACTIVE_SESSION
        ListItemCreateValidation.MissingSession ->
            CapturePlanListExecutionRejectionReason.MISSING_SESSION
        ListItemCreateValidation.SessionNotActive ->
            CapturePlanListExecutionRejectionReason.SESSION_NOT_ACTIVE
        ListItemCreateValidation.SessionDefinitionMismatch ->
            CapturePlanListExecutionRejectionReason.SESSION_DEFINITION_MISMATCH
        ListItemCreateValidation.SessionNotAllowed ->
            CapturePlanListExecutionRejectionReason.SESSION_NOT_ALLOWED
    }
}

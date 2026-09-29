package com.edu.quickaside.data.local

import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.actions.RandomActionLedgerIdProvider
import com.edu.quickaside.application.capture.CapturePlanListExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanListExecutionResult
import com.edu.quickaside.application.capture.CapturePlanListExecutor
import com.edu.quickaside.application.capture.MandadoSessionChoice
import com.edu.quickaside.application.capture.UndoCapturePlanListExecutionResult
import com.edu.quickaside.application.lists.LIST_ITEM_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.lists.LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListItemIdProvider
import com.edu.quickaside.application.lists.ListSessionIdProvider
import com.edu.quickaside.application.lists.RandomListItemIdProvider
import com.edu.quickaside.application.lists.RandomListSessionIdProvider
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
import com.edu.quickaside.domain.lists.ListBehavior
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

class RoomCapturePlanListExecutor(
    private val database: QuickAsideDatabase,
    private val itemIdProvider: ListItemIdProvider = RandomListItemIdProvider(),
    private val actionLedgerIdProvider: ActionLedgerIdProvider = RandomActionLedgerIdProvider(),
    private val clock: ListClock = ListClock { Instant.now() },
    private val sessionIdProvider: ListSessionIdProvider = RandomListSessionIdProvider(),
) : CapturePlanListExecutor {
    override suspend fun execute(plan: CapturePlan): CapturePlanListExecutionResult {
        validatePlanShape(plan)?.let { return it }
        return runPlan {
            executePlan(plan = plan, resolution = null)
        }
    }

    override suspend fun resolveMandadoSessionChoice(
        requirement: CapturePlanListExecutionResult.RequiresMandadoSessionChoice,
        choice: MandadoSessionChoice,
    ): CapturePlanListExecutionResult {
        validatePlanShape(requirement.plan)?.let { return it }
        return runPlan {
            executePlan(
                plan = requirement.plan,
                resolution = StaleMandadoResolution(requirement = requirement, choice = choice),
            )
        }
    }

    override suspend fun undoExecution(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedItemIds: List<ListItemId>,
        autoCreatedMandadoSessionId: ListSessionId?,
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
            if (autoCreatedMandadoSessionId != null) {
                // Conditional and atomic: only an active, now-empty session is
                // removed. Any unrelated item or an explicit finish preserves it.
                val deleted = database.listSessionDao()
                    .deleteActiveIfEmpty(autoCreatedMandadoSessionId.value)
                check(deleted in 0..1) {
                    "Expected at most one auto-created list session delete during Undo"
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

    private suspend fun runPlan(
        block: suspend () -> CapturePlanListExecutionResult,
    ): CapturePlanListExecutionResult = try {
        database.withWriteTransaction { block() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        CapturePlanListExecutionResult.Failed(failure)
    }

    private fun validatePlanShape(plan: CapturePlan): CapturePlanListExecutionResult? {
        for ((index, action) in plan.actions.withIndex()) {
            when (action) {
                is CapturePlanAction.AddListItem -> {
                    if (action.listDefinitionId.value !in
                        CapturePlanContract.SUPPORTED_LIST_DEFINITION_IDS
                    ) {
                        return CapturePlanListExecutionResult.Rejected(
                            actionIndex = index,
                            reason = CapturePlanListExecutionRejectionReason
                                .UNSUPPORTED_LIST_DEFINITION_ID,
                        )
                    }
                }

                else -> return CapturePlanListExecutionResult.UnsupportedAction(index)
            }
        }
        return null
    }

    /**
     * Validates the whole plan, resolves the Mandado lifecycle, and writes items,
     * session, and ledger in the enclosing Room write transaction. Any failure
     * or cancellation rolls back every provisional write.
     */
    private suspend fun executePlan(
        plan: CapturePlan,
        resolution: StaleMandadoResolution?,
    ): CapturePlanListExecutionResult {
        if (database.captureDao().getById(plan.sourceCaptureId.value) == null) {
            return CapturePlanListExecutionResult.MissingSourceCapture
        }

        val validatedActions = ArrayList<ValidatedListAction>(plan.actions.size)
        for ((index, action) in plan.actions.withIndex()) {
            val addListItem = action as? CapturePlanAction.AddListItem
                ?: error("CapturePlan action changed after execution preflight")
            if (addListItem.text.isBlank()) {
                return CapturePlanListExecutionResult.Rejected(
                    actionIndex = index,
                    reason = CapturePlanListExecutionRejectionReason.BLANK_TEXT,
                )
            }
            val definition = database.listDefinitionDao()
                .getById(addListItem.listDefinitionId.value)
                ?.toDomain()
                ?: return CapturePlanListExecutionResult.Rejected(
                    actionIndex = index,
                    reason = CapturePlanListExecutionRejectionReason.MISSING_DEFINITION,
                )
            val expectedDefinition = BuiltInListDefinitions.ALL
                .singleOrNull { it.id == addListItem.listDefinitionId }
                ?: return CapturePlanListExecutionResult.Rejected(
                    actionIndex = index,
                    reason = CapturePlanListExecutionRejectionReason.UNSUPPORTED_LIST_DEFINITION_ID,
                )
            if (definition != expectedDefinition) {
                return CapturePlanListExecutionResult.Rejected(
                    actionIndex = index,
                    reason = CapturePlanListExecutionRejectionReason
                        .LIST_DEFINITION_CONTRACT_MISMATCH,
                )
            }
            validatedActions += ValidatedListAction(
                action = addListItem,
                behavior = definition.behavior,
            )
        }

        val occurredAt = clock.now()
        val hasSessionBackedActions = validatedActions.any {
            it.behavior == ListBehavior.SESSION_BASED
        }

        var resolvedMandadoSessionId: ListSessionId? = null
        var autoCreatedMandadoSessionId: ListSessionId? = null

        if (hasSessionBackedActions) {
            val activeEntity = database.listSessionDao()
                .getActiveByDefinitionId(BuiltInListDefinitions.MANDADO.id.value)

            when {
                activeEntity == null -> {
                    if (resolution != null) {
                        return CapturePlanListExecutionResult.MandadoSessionChanged(
                            expectedActiveSessionId = resolution.requirement.expectedActiveSessionId,
                        )
                    }
                    // Bootstrap is written together with the items below.
                }

                resolution == null -> {
                    if (isStale(activeEntity.lastActivityAtEpochMillis, occurredAt)) {
                        return CapturePlanListExecutionResult.RequiresMandadoSessionChoice(
                            expectedActiveSessionId = ListSessionId(activeEntity.id),
                            observedLastActivityAt = Instant.ofEpochMilli(
                                activeEntity.lastActivityAtEpochMillis,
                            ),
                            plan = plan,
                        )
                    }
                    resolvedMandadoSessionId = ListSessionId(activeEntity.id)
                }

                else -> {
                    if (
                        activeEntity.id != resolution.requirement.expectedActiveSessionId.value ||
                        activeEntity.lastActivityAtEpochMillis !=
                        resolution.requirement.observedLastActivityAt.toEpochMilli()
                    ) {
                        return CapturePlanListExecutionResult.MandadoSessionChanged(
                            expectedActiveSessionId = resolution.requirement.expectedActiveSessionId,
                        )
                    }
                    when (resolution.choice) {
                        MandadoSessionChoice.CONTINUE -> {
                            resolvedMandadoSessionId = ListSessionId(activeEntity.id)
                        }

                        MandadoSessionChoice.NEW -> {
                            check(
                                database.listSessionDao().finishActive(
                                    id = activeEntity.id,
                                    endedAtEpochMillis = occurredAt.toEpochMilli(),
                                ) == 1,
                            ) {
                                "Expected exactly one stale Mandado session finish during New"
                            }
                            // The replacement session is created below, in this
                            // same transaction, before its items.
                        }
                    }
                }
            }
        }

        val items = ArrayList<ListItem>(validatedActions.size)
        for (validated in validatedActions) {
            val sessionId = when (validated.behavior) {
                ListBehavior.CONTINUOUS -> null

                ListBehavior.SESSION_BASED -> {
                    if (resolvedMandadoSessionId == null) {
                        val sessionIdCandidate = sessionIdProvider.nextSessionId()
                        check(sessionIdCandidate.value.isNotBlank()) {
                            "List session ID provider returned a blank ID"
                        }
                        val session = ListSession(
                            id = sessionIdCandidate,
                            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
                            startedAt = occurredAt,
                            lastActivityAt = occurredAt,
                        )
                        database.listSessionDao().insert(session.toEntity())
                        resolvedMandadoSessionId = session.id
                        autoCreatedMandadoSessionId = session.id
                    }
                    resolvedMandadoSessionId
                }
            }

            val itemId = itemIdProvider.nextItemId()
            check(itemId.value.isNotBlank()) { "ListItem ID provider returned a blank ID" }
            val item = ListItem(
                id = itemId,
                listDefinitionId = validated.action.listDefinitionId,
                text = validated.action.text,
                listSessionId = sessionId,
                isCompleted = false,
                createdAt = occurredAt,
            )
            database.listItemDao().insert(item.toEntity())
            items += item
        }

        if (resolvedMandadoSessionId != null) {
            check(
                database.listSessionDao().touchActivity(
                    id = resolvedMandadoSessionId.value,
                    activityAtEpochMillis = occurredAt.toEpochMilli(),
                ) == 1,
            ) {
                "Expected exactly one list session activity update during execution"
            }
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

        return CapturePlanListExecutionResult.Executed(
            items = items,
            actionLedgerEntryId = entry.id,
            autoCreatedMandadoSessionId = autoCreatedMandadoSessionId,
        )
    }

    private fun isStale(lastActivityAtEpochMillis: Long, now: Instant): Boolean =
        now.toEpochMilli() - lastActivityAtEpochMillis > MANDADO_STALE_THRESHOLD_MILLIS

    private data class ValidatedListAction(
        val action: CapturePlanAction.AddListItem,
        val behavior: ListBehavior,
    )

    private data class StaleMandadoResolution(
        val requirement: CapturePlanListExecutionResult.RequiresMandadoSessionChoice,
        val choice: MandadoSessionChoice,
    )

    private companion object {
        val MANDADO_STALE_THRESHOLD_MILLIS: Long = Duration.ofDays(7).toMillis()
    }
}

package com.edu.quickaside.data.local

import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.actions.RandomActionLedgerIdProvider
import com.edu.quickaside.application.lists.CreateListItemActionResult
import com.edu.quickaside.application.lists.LIST_ITEM_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.lists.LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListItemIdProvider
import com.edu.quickaside.application.lists.RandomListItemIdProvider
import com.edu.quickaside.application.lists.ReversibleListItemActions
import com.edu.quickaside.application.lists.UndoListItemCreateResult
import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.actions.ActionLedgerTargetType
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.lists.ListItem
import java.time.Instant
import kotlinx.coroutines.CancellationException

class RoomReversibleListItemActions(
    private val database: QuickAsideDatabase,
    private val itemIdProvider: ListItemIdProvider = RandomListItemIdProvider(),
    private val actionLedgerIdProvider: ActionLedgerIdProvider = RandomActionLedgerIdProvider(),
    private val clock: ListClock = ListClock { Instant.now() },
) : ReversibleListItemActions {
    override suspend fun create(
        listDefinitionId: ListDefinitionId,
        text: String,
        listSessionId: ListSessionId?,
    ): CreateListItemActionResult = try {
        database.withWriteTransaction {
            when (val validation = database.validateListItemCreate(
                listDefinitionId = listDefinitionId,
                text = text,
                listSessionId = listSessionId,
            )) {
                is ListItemCreateValidation.Valid -> {
                    val occurredAt = clock.now()
                    val item = ListItem(
                        id = itemIdProvider.nextItemId(),
                        listDefinitionId = validation.definition.id,
                        text = text,
                        listSessionId = validation.listSessionId,
                        isCompleted = false,
                        createdAt = occurredAt,
                    )
                    val entry = ActionLedgerEntry(
                        id = actionLedgerIdProvider.nextEntryId(),
                        occurredAt = occurredAt,
                        sourceCaptureId = null,
                        mutations = listOf(
                            ActionLedgerMutation(
                                operation = ActionLedgerOperation.CREATE,
                                targetType = ActionLedgerTargetType(
                                    LIST_ITEM_ACTION_LEDGER_TARGET_TYPE,
                                ),
                                targetId = item.id.value,
                                payloadVersion = LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION,
                            ),
                        ),
                    )
                    database.listItemDao().insert(item.toEntity())
                    database.actionLedgerEntryDao().insert(entry.toEntity())
                    database.actionLedgerMutationDao().insertAll(entry.toMutationEntities())
                    CreateListItemActionResult.Saved(
                        item = item,
                        actionLedgerEntryId = entry.id,
                    )
                }

                ListItemCreateValidation.BlankText -> CreateListItemActionResult.BlankText
                ListItemCreateValidation.MissingDefinition ->
                    CreateListItemActionResult.MissingDefinition
                ListItemCreateValidation.NoActiveSession ->
                    CreateListItemActionResult.NoActiveSession
                ListItemCreateValidation.MissingSession -> CreateListItemActionResult.MissingSession
                ListItemCreateValidation.SessionNotActive ->
                    CreateListItemActionResult.SessionNotActive
                ListItemCreateValidation.SessionDefinitionMismatch ->
                    CreateListItemActionResult.SessionDefinitionMismatch
                ListItemCreateValidation.SessionNotAllowed ->
                    CreateListItemActionResult.SessionNotAllowed
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        CreateListItemActionResult.Failed(failure)
    }

    override suspend fun undoCreate(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedItemId: ListItemId,
    ): UndoListItemCreateResult = try {
        database.withWriteTransaction {
            val entry = database.actionLedgerEntryDao().getById(actionLedgerEntryId.value)
                ?: return@withWriteTransaction UndoListItemCreateResult.MissingLedgerEntry
            if (entry.undoneAtEpochMillis != null) {
                return@withWriteTransaction UndoListItemCreateResult.AlreadyUndone
            }

            val mutations = database.actionLedgerMutationDao()
                .getByEntryId(actionLedgerEntryId.value)
            if (mutations.size != 1) {
                return@withWriteTransaction UndoListItemCreateResult.UnsupportedLedgerShape
            }
            val mutation = mutations.single()
            if (
                mutation.operation != ActionLedgerOperation.CREATE.name ||
                mutation.targetType != LIST_ITEM_ACTION_LEDGER_TARGET_TYPE
            ) {
                return@withWriteTransaction UndoListItemCreateResult.UnsupportedAction
            }
            if (mutation.payloadVersion != LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION) {
                return@withWriteTransaction UndoListItemCreateResult.UnsupportedLedgerShape
            }
            if (mutation.targetId != expectedItemId.value) {
                return@withWriteTransaction UndoListItemCreateResult.TargetMismatch
            }
            if (database.listItemDao().getById(expectedItemId.value) == null) {
                return@withWriteTransaction UndoListItemCreateResult.TargetMissing
            }

            check(database.listItemDao().deleteById(expectedItemId.value) == 1) {
                "Expected exactly one list item delete during Undo"
            }
            check(
                database.actionLedgerEntryDao().markUndone(
                    id = actionLedgerEntryId.value,
                    undoneAtEpochMillis = clock.now().toEpochMilli(),
                ) == 1,
            ) {
                "Expected exactly one Action Ledger entry update during Undo"
            }
            UndoListItemCreateResult.Undone(
                actionLedgerEntryId = actionLedgerEntryId,
                itemId = expectedItemId,
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        UndoListItemCreateResult.Failed(failure)
    }
}

package com.edu.quickaside.application.lists

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.ListItem

/** The only application boundary used by the manual list-item create UI. */
interface ReversibleListItemActions {
    suspend fun create(
        listDefinitionId: ListDefinitionId,
        text: String,
        listSessionId: ListSessionId? = null,
    ): CreateListItemActionResult

    suspend fun undoCreate(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedItemId: ListItemId,
    ): UndoListItemCreateResult
}

fun interface ListItemIdProvider {
    fun nextItemId(): ListItemId
}

class RandomListItemIdProvider : ListItemIdProvider {
    override fun nextItemId(): ListItemId = ListItemId(java.util.UUID.randomUUID().toString())
}

sealed interface CreateListItemActionResult {
    data class Saved(
        val item: ListItem,
        val actionLedgerEntryId: ActionLedgerEntryId,
    ) : CreateListItemActionResult

    data object BlankText : CreateListItemActionResult

    data object MissingDefinition : CreateListItemActionResult

    data object NoActiveSession : CreateListItemActionResult

    data object MissingSession : CreateListItemActionResult

    data object SessionNotActive : CreateListItemActionResult

    data object SessionDefinitionMismatch : CreateListItemActionResult

    data object SessionNotAllowed : CreateListItemActionResult

    data class Failed(val cause: Exception) : CreateListItemActionResult
}

sealed interface UndoListItemCreateResult {
    data class Undone(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val itemId: ListItemId,
    ) : UndoListItemCreateResult

    data object MissingLedgerEntry : UndoListItemCreateResult

    data object AlreadyUndone : UndoListItemCreateResult

    data object UnsupportedAction : UndoListItemCreateResult

    data object UnsupportedLedgerShape : UndoListItemCreateResult

    data object TargetMismatch : UndoListItemCreateResult

    data object TargetMissing : UndoListItemCreateResult

    data class Failed(val cause: Exception) : UndoListItemCreateResult
}

const val LIST_ITEM_ACTION_LEDGER_TARGET_TYPE = "list_item"
const val LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION = 1

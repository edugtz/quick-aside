package com.edu.quickaside

import com.edu.quickaside.application.lists.CreateListItemActionResult
import com.edu.quickaside.application.lists.ReversibleListItemActions
import com.edu.quickaside.application.lists.UndoListItemCreateResult
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.ListItem
import java.time.Instant

class FakeReversibleListItemActions : ReversibleListItemActions {
    data class CreateCall(
        val listDefinitionId: ListDefinitionId,
        val text: String,
        val listSessionId: ListSessionId?,
    )

    data class UndoCall(
        val actionLedgerEntryId: ActionLedgerEntryId,
        val expectedItemId: ListItemId,
    )

    val createCalls = mutableListOf<CreateCall>()
    val undoCalls = mutableListOf<UndoCall>()
    var createResult: CreateListItemActionResult? = null
    var undoResult: UndoListItemCreateResult? = null
    var onUndo: ((ListItemId) -> Unit)? = null

    override suspend fun create(
        listDefinitionId: ListDefinitionId,
        text: String,
        listSessionId: ListSessionId?,
    ): CreateListItemActionResult {
        createCalls += CreateCall(listDefinitionId, text, listSessionId)
        createResult?.let { return it }
        val itemId = ListItemId("created-item-" + createCalls.size)
        return CreateListItemActionResult.Saved(
            item = ListItem(
                id = itemId,
                listDefinitionId = listDefinitionId,
                text = text,
                listSessionId = listSessionId,
                createdAt = Instant.parse("2026-09-08T10:00:00Z"),
            ),
            actionLedgerEntryId = ActionLedgerEntryId("created-entry-" + createCalls.size),
        )
    }

    override suspend fun undoCreate(
        actionLedgerEntryId: ActionLedgerEntryId,
        expectedItemId: ListItemId,
    ): UndoListItemCreateResult {
        undoCalls += UndoCall(actionLedgerEntryId, expectedItemId)
        onUndo?.invoke(expectedItemId)
        return undoResult ?: UndoListItemCreateResult.Undone(
            actionLedgerEntryId = actionLedgerEntryId,
            itemId = expectedItemId,
        )
    }
}


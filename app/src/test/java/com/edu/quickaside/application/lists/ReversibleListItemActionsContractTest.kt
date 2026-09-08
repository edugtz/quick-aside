package com.edu.quickaside.application.lists

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.lists.ListItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReversibleListItemActionsContractTest {
    @Test
    fun savedResultCarriesExactItemAndLedgerReceiptIdentity() {
        val item = ListItem(
            id = ListItemId("item-1"),
            listDefinitionId = ListDefinitionId("compras"),
            text = "  Cuerdas guitarra  ",
            listSessionId = null,
            createdAt = Instant.parse("2026-09-08T10:00:00Z"),
        )
        val result = CreateListItemActionResult.Saved(
            item = item,
            actionLedgerEntryId = ActionLedgerEntryId("entry-1"),
        )

        assertEquals(item, result.item)
        assertEquals(ActionLedgerEntryId("entry-1"), result.actionLedgerEntryId)
    }

    @Test
    fun boundaryKeepsLedgerShapeConstantsExplicit() {
        assertEquals("list_item", LIST_ITEM_ACTION_LEDGER_TARGET_TYPE)
        assertEquals(1, LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION)
        val undone = UndoListItemCreateResult.Undone(
            actionLedgerEntryId = ActionLedgerEntryId("entry-1"),
            itemId = ListItemId("item-1"),
        )
        assertEquals(ActionLedgerEntryId("entry-1"), undone.actionLedgerEntryId)
        assertEquals(ListItemId("item-1"), undone.itemId)
    }
}

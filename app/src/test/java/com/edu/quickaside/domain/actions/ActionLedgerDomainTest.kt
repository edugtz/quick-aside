package com.edu.quickaside.domain.actions

import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionLedgerDomainTest {
    private val occurredAt = Instant.parse("2026-09-07T12:34:56.789Z")

    @Test
    fun validEntryPreservesMutationOrderAndOpaqueValues() {
        val mutations = listOf(
            ActionLedgerMutation(
                operation = ActionLedgerOperation.CREATE,
                targetType = ActionLedgerTargetType("future_target"),
                targetId = "target-1",
                payloadVersion = 1,
                beforeState = null,
                afterState = "  {opaque: true}  ",
            ),
            ActionLedgerMutation(
                operation = ActionLedgerOperation.UPDATE,
                targetType = ActionLedgerTargetType("future_target"),
                targetId = "target-2",
                payloadVersion = 2,
                beforeState = "before; do not parse",
                afterState = null,
            ),
            ActionLedgerMutation(
                operation = ActionLedgerOperation.DELETE,
                targetType = ActionLedgerTargetType("another_future_target"),
                targetId = "target-3",
                payloadVersion = 3,
            ),
        )
        val entry = ActionLedgerEntry(
            id = ActionLedgerEntryId("entry-1"),
            occurredAt = occurredAt,
            sourceCaptureId = CaptureId("capture-1"),
            mutations = mutations,
            undoneAt = null,
        )

        assertEquals(mutations, entry.mutations)
        assertEquals(CaptureId("capture-1"), entry.sourceCaptureId)
        assertEquals(occurredAt, entry.occurredAt)
        assertEquals(null, entry.undoneAt)
        assertEquals("  {opaque: true}  ", entry.mutations.first().afterState)
        assertEquals("before; do not parse", entry.mutations[1].beforeState)
    }

    @Test
    fun operationVocabularyContainsOnlyTheThreeGenericOperations() {
        assertEquals(
            listOf(
                ActionLedgerOperation.CREATE,
                ActionLedgerOperation.UPDATE,
                ActionLedgerOperation.DELETE,
            ),
            ActionLedgerOperation.entries,
        )
    }

    @Test
    fun emptyMutationEntryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionLedgerEntry(
                id = ActionLedgerEntryId("empty"),
                occurredAt = occurredAt,
                mutations = emptyList(),
            )
        }
    }

    @Test
    fun blankTargetTypeIsRejectedWithoutTrimmingAcceptedValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionLedgerTargetType(" \t\n ")
        }
        assertEquals("  list item  ", ActionLedgerTargetType("  list item  ").value)
    }

    @Test
    fun blankTargetIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionLedgerMutation(
                operation = ActionLedgerOperation.CREATE,
                targetType = ActionLedgerTargetType("list_item"),
                targetId = " \t\n ",
                payloadVersion = 1,
            )
        }
    }

    @Test
    fun nonPositivePayloadVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionLedgerMutation(
                operation = ActionLedgerOperation.UPDATE,
                targetType = ActionLedgerTargetType("list_item"),
                targetId = "item-1",
                payloadVersion = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActionLedgerMutation(
                operation = ActionLedgerOperation.UPDATE,
                targetType = ActionLedgerTargetType("list_item"),
                targetId = "item-1",
                payloadVersion = -1,
            )
        }
    }
}

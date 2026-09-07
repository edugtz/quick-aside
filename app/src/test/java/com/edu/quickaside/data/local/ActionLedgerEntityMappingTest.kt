package com.edu.quickaside.data.local

import com.edu.quickaside.domain.actions.ActionLedgerEntry
import com.edu.quickaside.domain.actions.ActionLedgerMutation
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.actions.ActionLedgerTargetType
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionLedgerEntityMappingTest {
    private val occurredAt = Instant.parse("2026-09-07T12:34:56.789Z")
    private val undoneAt = Instant.parse("2026-09-07T12:35:00Z")

    @Test
    fun entryAndChildrenRoundTripExactFieldsAndOrder() {
        val entry = ActionLedgerEntry(
            id = ActionLedgerEntryId("entry-1"),
            occurredAt = occurredAt,
            sourceCaptureId = CaptureId("capture-1"),
            mutations = listOf(
                ActionLedgerMutation(
                    operation = ActionLedgerOperation.CREATE,
                    targetType = ActionLedgerTargetType("list_item"),
                    targetId = "item-1",
                    payloadVersion = 1,
                    afterState = "  opaque after  ",
                ),
                ActionLedgerMutation(
                    operation = ActionLedgerOperation.UPDATE,
                    targetType = ActionLedgerTargetType("list_item"),
                    targetId = "item-2",
                    payloadVersion = 7,
                    beforeState = "before = []",
                    afterState = null,
                ),
            ),
            undoneAt = undoneAt,
        )

        val restored = entry.toEntity().toDomain(entry.toMutationEntities())

        assertEquals(entry, restored)
        assertEquals(
            listOf(0, 1),
            entry.toMutationEntities().map(ActionLedgerMutationEntity::position),
        )
        assertEquals("  opaque after  ", restored.mutations[0].afterState)
        assertEquals("before = []", restored.mutations[1].beforeState)
    }

    @Test
    fun invalidOperationFailsVisibly() {
        assertThrows(IllegalArgumentException::class.java) {
            entryEntity().toDomain(listOf(mutationEntity(operation = "NOT_AN_OPERATION")))
        }
    }

    @Test
    fun invalidOrUnorderedPositionsFailVisibly() {
        assertThrows(IllegalStateException::class.java) {
            entryEntity().toDomain(
                listOf(
                    mutationEntity(position = 0),
                    mutationEntity(position = 2),
                ),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            entryEntity().toDomain(
                listOf(
                    mutationEntity(position = 1),
                    mutationEntity(position = 0),
                ),
            )
        }
    }

    @Test
    fun mismatchedParentAndMissingChildrenFailVisibly() {
        assertThrows(IllegalStateException::class.java) {
            entryEntity().toDomain(listOf(mutationEntity(actionLedgerEntryId = "other")))
        }
        assertThrows(IllegalStateException::class.java) {
            entryEntity().toDomain(emptyList())
        }
    }

    private fun entryEntity() = ActionLedgerEntryEntity(
        id = "entry-1",
        occurredAtEpochMillis = occurredAt.toEpochMilli(),
        sourceCaptureId = "capture-1",
        undoneAtEpochMillis = undoneAt.toEpochMilli(),
    )

    private fun mutationEntity(
        actionLedgerEntryId: String = "entry-1",
        position: Int = 0,
        operation: String = "CREATE",
    ) = ActionLedgerMutationEntity(
        actionLedgerEntryId = actionLedgerEntryId,
        position = position,
        operation = operation,
        targetType = "list_item",
        targetId = "item-1",
        payloadVersion = 1,
    )
}

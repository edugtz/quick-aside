package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CapturePlanListExecutorContractTest {
    @Test
    fun executionOutcomesPreserveIndexedRejectionReason() {
        val unsupportedAction = CapturePlanListExecutionResult.UnsupportedAction(actionIndex = 1)
        assertEquals(1, unsupportedAction.actionIndex)

        val rejected = CapturePlanListExecutionResult.Rejected(
            actionIndex = 2,
            reason = CapturePlanListExecutionRejectionReason.NO_ACTIVE_SESSION,
        )
        assertEquals(2, rejected.actionIndex)
        assertEquals(CapturePlanListExecutionRejectionReason.NO_ACTIVE_SESSION, rejected.reason)

        assertThrows(IllegalArgumentException::class.java) {
            CapturePlanListExecutionResult.UnsupportedAction(actionIndex = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapturePlanListExecutionResult.Rejected(
                actionIndex = -1,
                reason = CapturePlanListExecutionRejectionReason.MISSING_DEFINITION,
            )
        }
    }

    @Test
    fun undoOutcomesPreserveMutationAndTargetIndexes() {
        val unsupportedAction = UndoCapturePlanListExecutionResult.UnsupportedAction(mutationIndex = 2)
        assertEquals(2, unsupportedAction.mutationIndex)

        val targetMissing = UndoCapturePlanListExecutionResult.TargetMissing(itemIndex = 1)
        assertEquals(1, targetMissing.itemIndex)

        assertThrows(IllegalArgumentException::class.java) {
            UndoCapturePlanListExecutionResult.UnsupportedAction(mutationIndex = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UndoCapturePlanListExecutionResult.TargetMissing(itemIndex = -1)
        }
    }

    @Test
    fun successfulOutcomesCannotRepresentEmptyOrDuplicateBatches() {
        assertThrows(IllegalArgumentException::class.java) {
            CapturePlanListExecutionResult.Executed(
                items = emptyList(),
                actionLedgerEntryId = ActionLedgerEntryId("entry"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UndoCapturePlanListExecutionResult.Undone(
                actionLedgerEntryId = ActionLedgerEntryId("entry"),
                itemIds = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UndoCapturePlanListExecutionResult.Undone(
                actionLedgerEntryId = ActionLedgerEntryId("entry"),
                itemIds = listOf(ListItemId("item"), ListItemId("item")),
            )
        }
    }

    @Test
    fun successfulExecutionResultRetainsExactItemAndEntryIdentifiers() {
        val item = ListItem(
            id = ListItemId("exact-item"),
            listDefinitionId = ListDefinitionId("compras"),
            text = "  exact text  ",
            createdAt = Instant.parse("2026-09-19T12:00:00Z"),
        )

        val result = CapturePlanListExecutionResult.Executed(
            items = listOf(item),
            actionLedgerEntryId = ActionLedgerEntryId("exact-entry"),
        )

        assertEquals(listOf(item), result.items)
        assertEquals(ActionLedgerEntryId("exact-entry"), result.actionLedgerEntryId)
        assertEquals(null, result.autoCreatedMandadoSessionId)
    }

    @Test
    fun staleMandadoChoiceRequirementCarriesExactSessionActivityAndPlan() {
        val plan = CapturePlan(
            sourceCaptureId = CaptureId("capture"),
            actions = listOf(
                CapturePlanAction.AddListItem(BuiltInListDefinitions.MANDADO.id, "pollo"),
            ),
        )
        val observed = Instant.parse("2026-09-19T12:00:00Z")
        val requirement = CapturePlanListExecutionResult.RequiresMandadoSessionChoice(
            expectedActiveSessionId = ListSessionId("stale-session"),
            observedLastActivityAt = observed,
            plan = plan,
        )

        assertEquals(ListSessionId("stale-session"), requirement.expectedActiveSessionId)
        assertEquals(observed, requirement.observedLastActivityAt)
        assertEquals(plan, requirement.plan)
        assertEquals(
            listOf(MandadoSessionChoice.CONTINUE, MandadoSessionChoice.NEW),
            MandadoSessionChoice.entries,
        )
    }

    @Test
    fun staleMandadoChoiceRequirementRejectsPlansWithoutMandadoActions() {
        assertThrows(IllegalArgumentException::class.java) {
            CapturePlanListExecutionResult.RequiresMandadoSessionChoice(
                expectedActiveSessionId = ListSessionId("stale-session"),
                observedLastActivityAt = Instant.parse("2026-09-19T12:00:00Z"),
                plan = CapturePlan(
                    sourceCaptureId = CaptureId("capture"),
                    actions = listOf(
                        CapturePlanAction.AddListItem(BuiltInListDefinitions.COMPRAS.id, "leche"),
                    ),
                ),
            )
        }
    }

    @Test
    fun sessionChangedResultRetainsTheExpectedSessionIdentity() {
        val changed = CapturePlanListExecutionResult.MandadoSessionChanged(
            expectedActiveSessionId = ListSessionId("obsolete-session"),
        )

        assertEquals(ListSessionId("obsolete-session"), changed.expectedActiveSessionId)
    }
}

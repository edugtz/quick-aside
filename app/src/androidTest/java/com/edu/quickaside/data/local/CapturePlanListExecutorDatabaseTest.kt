package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.capture.CapturePlanListExecutionRejectionReason
import com.edu.quickaside.application.capture.CapturePlanListExecutionResult
import com.edu.quickaside.application.capture.UndoCapturePlanListExecutionResult
import com.edu.quickaside.application.lists.CreateListItemActionResult
import com.edu.quickaside.application.lists.LIST_ITEM_ACTION_LEDGER_TARGET_TYPE
import com.edu.quickaside.application.lists.LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListItemIdProvider
import com.edu.quickaside.domain.actions.ActionLedgerOperation
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapturePlanListExecutorDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-027-capture-plan-list-" + UUID.randomUUID() + ".db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun singleComprasActionPersistsExactItemLedgerAndSourceCapture() = runBlocking {
        openFreshDatabase()
        val sourceCaptureId = "  trusted-source-capture  "
        insertSourceCapture(sourceCaptureId)
        val occurredAt = Instant.parse("2026-09-19T12:01:02.123Z")

        val result = executor(
            itemIds = listOf("compras-item"),
            entryIds = listOf("compras-entry"),
            times = listOf(occurredAt),
        ).execute(
            plan(
                CapturePlanAction.AddListItem(
                    listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
                    text = "  Chobani  ",
                ),
                sourceCaptureId = sourceCaptureId,
            ),
        ).requireExecuted()

        val item = result.items.single()
        assertEquals(ListItemId("compras-item"), item.id)
        assertEquals(BuiltInListDefinitions.COMPRAS.id, item.listDefinitionId)
        assertEquals("  Chobani  ", item.text)
        assertNull(item.listSessionId)
        assertFalse(item.isCompleted)
        assertEquals(occurredAt, item.createdAt)
        assertEquals(item, database.listItemDao().getById("compras-item")?.toDomain())

        val entry = database.actionLedgerEntryDao().getById("compras-entry")
        assertNotNull(entry)
        assertEquals(occurredAt.toEpochMilli(), entry?.occurredAtEpochMillis)
        assertEquals(sourceCaptureId, entry?.sourceCaptureId)
        assertNull(entry?.undoneAtEpochMillis)
        assertEquals(ActionLedgerEntryId("compras-entry"), result.actionLedgerEntryId)

        val mutations = database.actionLedgerMutationDao().getByEntryId("compras-entry")
        assertEquals(1, mutations.size)
        assertMutation(
            mutations.single(),
            position = 0,
            targetId = "compras-item",
        )
        assertEquals(1, database.actionLedgerEntryDao().getRecent(50).size)
    }

    @Test
    fun multipleComprasActionsUseOneParentCommonTimeAndOrderedMutations() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val occurredAt = Instant.parse("2026-09-19T12:02:03.456Z")
        val expectedTexts = listOf("Chobani", "  pollo  ", "leche")
        val expectedIds = listOf("item-chobani", "item-pollo", "item-leche")

        val result = executor(
            itemIds = expectedIds,
            entryIds = listOf("one-logical-execution"),
            times = listOf(occurredAt),
        ).execute(
            plan(*expectedTexts.map { compras(it) }.toTypedArray()),
        ).requireExecuted()

        assertEquals(expectedIds, result.items.map { it.id.value })
        assertEquals(expectedTexts, result.items.map(ListItem::text))
        assertTrue(result.items.all { it.createdAt == occurredAt })
        assertTrue(result.items.all { it.listSessionId == null && !it.isCompleted })
        expectedIds.forEachIndexed { index, id ->
            assertEquals(result.items[index], database.listItemDao().getById(id)?.toDomain())
        }

        val entries = database.actionLedgerEntryDao().getRecent(50)
        assertEquals(1, entries.size)
        assertEquals("one-logical-execution", entries.single().id)
        assertEquals("plan-source", entries.single().sourceCaptureId)
        assertEquals(occurredAt.toEpochMilli(), entries.single().occurredAtEpochMillis)
        val mutations = database.actionLedgerMutationDao().getByEntryId("one-logical-execution")
        assertEquals(expectedIds.size, mutations.size)
        mutations.forEachIndexed { index, mutation ->
            assertMutation(mutation, position = index, targetId = expectedIds[index])
        }
    }

    @Test
    fun mixedMandadoAndComprasActionsPreserveOrderAndSessionSemantics() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "active-mandado-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = 100,
            ),
        )
        val occurredAt = Instant.parse("2026-09-19T12:03:04Z")

        val result = executor(
            itemIds = listOf("mandado-item", "compras-item"),
            entryIds = listOf("mixed-entry"),
            times = listOf(occurredAt),
        ).execute(
            plan(
                CapturePlanAction.AddListItem(BuiltInListDefinitions.MANDADO.id, "  Jabón  "),
                CapturePlanAction.AddListItem(BuiltInListDefinitions.COMPRAS.id, "Cable"),
            ),
        ).requireExecuted()

        assertEquals(listOf("mandado-item", "compras-item"), result.items.map { it.id.value })
        assertEquals("  Jabón  ", result.items[0].text)
        assertEquals(ListSessionId("active-mandado-session"), result.items[0].listSessionId)
        assertEquals("Cable", result.items[1].text)
        assertNull(result.items[1].listSessionId)
        assertTrue(result.items.all { it.createdAt == occurredAt })
        val mutations = database.actionLedgerMutationDao().getByEntryId("mixed-entry")
        assertEquals(listOf("mandado-item", "compras-item"), mutations.map { it.targetId })
        mutations.forEachIndexed { index, mutation ->
            assertMutation(mutation, position = index, targetId = result.items[index].id.value)
        }
    }

    @Test
    fun unsupportedActionFamiliesAndListIdsWriteNothing() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val itemIds = CountingItemIdProvider()
        val entryIds = CountingEntryIdProvider()
        val clock = CountingListClock()
        val action = RoomCapturePlanListExecutor(database, itemIds, entryIds, clock)
        val unsupportedActions = listOf(
            CapturePlanAction.CreateTask(TaskSpace.PERSONAL, "task"),
            CapturePlanAction.CreateNote("note"),
            CapturePlanAction.CreateStructuredLog(mapOf("key" to "value")),
            CapturePlanAction.UndoLast,
        )

        unsupportedActions.forEach { unsupported ->
            val result = action.execute(
                plan(compras("supported first"), unsupported),
            )
            assertEquals(CapturePlanListExecutionResult.UnsupportedAction(1), result)
        }

        val unsupportedListId = action.execute(
            plan(
                CapturePlanAction.AddListItem(
                    listDefinitionId = ListDefinitionId("compras "),
                    text = "not silently normalized",
                ),
            ),
        )
        assertEquals(
            CapturePlanListExecutionResult.Rejected(
                actionIndex = 0,
                reason = CapturePlanListExecutionRejectionReason.UNSUPPORTED_LIST_DEFINITION_ID,
            ),
            unsupportedListId,
        )
        assertEquals(0, itemIds.calls)
        assertEquals(0, entryIds.calls)
        assertEquals(0, clock.calls)
        assertNull(database.listItemDao().getById("unused-item-0"))
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("unused-entry").isEmpty())
    }

    @Test
    fun noActiveMandadoRejectsWholeBatchAtFailingIndexBeforeIdsOrClock() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "ended-mandado-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = 100,
            ),
        )
        assertEquals(1, database.listSessionDao().finishActive("ended-mandado-session", 200))
        val itemIds = CountingItemIdProvider()
        val entryIds = CountingEntryIdProvider()
        val clock = CountingListClock()

        val result = RoomCapturePlanListExecutor(database, itemIds, entryIds, clock).execute(
            plan(
                compras("would have been first"),
                CapturePlanAction.AddListItem(BuiltInListDefinitions.MANDADO.id, "pollo"),
            ),
        )

        assertEquals(
            CapturePlanListExecutionResult.Rejected(
                actionIndex = 1,
                reason = CapturePlanListExecutionRejectionReason.NO_ACTIVE_SESSION,
            ),
            result,
        )
        assertEquals(0, itemIds.calls)
        assertEquals(0, entryIds.calls)
        assertEquals(0, clock.calls)
        assertNull(database.listItemDao().getById("unused-item-0"))
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("unused-entry").isEmpty())
    }

    @Test
    fun mismatchedBuiltInListBehaviorRejectsWithoutWrites() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        database.close()
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(
                "UPDATE list_definitions SET behavior = 'CONTINUOUS' WHERE id = 'mandado'",
            )
        }
        openFreshDatabase()
        val itemIds = CountingItemIdProvider()
        val entryIds = CountingEntryIdProvider()
        val clock = CountingListClock()

        val result = RoomCapturePlanListExecutor(database, itemIds, entryIds, clock).execute(
            plan(CapturePlanAction.AddListItem(BuiltInListDefinitions.MANDADO.id, "pollo")),
        )

        assertEquals(
            CapturePlanListExecutionResult.Rejected(
                actionIndex = 0,
                reason = CapturePlanListExecutionRejectionReason.LIST_DEFINITION_CONTRACT_MISMATCH,
            ),
            result,
        )
        assertEquals(0, itemIds.calls)
        assertEquals(0, entryIds.calls)
        assertEquals(0, clock.calls)
        assertNull(database.listItemDao().getById("unused-item-0"))
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
    }

    @Test
    fun missingSourceCaptureWritesNothingAndDoesNotConsumeIdsOrClock() = runBlocking {
        openFreshDatabase()
        val itemIds = CountingItemIdProvider()
        val entryIds = CountingEntryIdProvider()
        val clock = CountingListClock()

        val result = RoomCapturePlanListExecutor(database, itemIds, entryIds, clock).execute(
            plan(compras("must not persist"), sourceCaptureId = "missing-source"),
        )

        assertEquals(CapturePlanListExecutionResult.MissingSourceCapture, result)
        assertEquals(0, itemIds.calls)
        assertEquals(0, entryIds.calls)
        assertEquals(0, clock.calls)
        assertNull(database.listItemDao().getById("unused-item-0"))
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("unused-entry").isEmpty())
    }

    @Test
    fun laterItemIdCollisionRollsBackEarlierInsertWithoutReplacingExistingItem() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        database.listItemDao().insert(
            ListItemEntity(
                id = "collision-item",
                listDefinitionId = BuiltInListDefinitions.COMPRAS.id.value,
                listSessionId = null,
                text = "pre-existing durable value",
                isCompleted = true,
                createdAtEpochMillis = 777,
            ),
        )

        val result = executor(
            itemIds = listOf("first-item", "collision-item"),
            entryIds = listOf("collision-entry"),
            times = listOf(Instant.parse("2026-09-19T12:04:00Z")),
        ).execute(plan(compras("first"), compras("second")))

        assertTrue(result is CapturePlanListExecutionResult.Failed)
        assertNull(database.listItemDao().getById("first-item"))
        assertEquals(
            ListItemEntity(
                id = "collision-item",
                listDefinitionId = BuiltInListDefinitions.COMPRAS.id.value,
                listSessionId = null,
                text = "pre-existing durable value",
                isCompleted = true,
                createdAtEpochMillis = 777,
            ),
            database.listItemDao().getById("collision-item"),
        )
        assertNull(database.actionLedgerEntryDao().getById("collision-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("collision-entry").isEmpty())
    }

    @Test
    fun ledgerParentFailureRollsBackEveryInsertedItem() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        database.close()
        addTrigger(
            "CREATE TRIGGER fail_change027_ledger_parent BEFORE INSERT ON action_ledger_entries " +
                "WHEN NEW.id = 'parent-failure-entry' " +
                "BEGIN SELECT RAISE(ABORT, 'forced Change 027 parent failure'); END",
        )
        openFreshDatabase()

        val result = executor(
            itemIds = listOf("parent-failure-item-1", "parent-failure-item-2"),
            entryIds = listOf("parent-failure-entry"),
            times = listOf(Instant.parse("2026-09-19T12:05:00Z")),
        ).execute(plan(compras("first"), compras("second")))

        assertTrue(result is CapturePlanListExecutionResult.Failed)
        assertNull(database.listItemDao().getById("parent-failure-item-1"))
        assertNull(database.listItemDao().getById("parent-failure-item-2"))
        assertNull(database.actionLedgerEntryDao().getById("parent-failure-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("parent-failure-entry").isEmpty())
    }

    @Test
    fun childFailureAfterAnEarlierMutationRollsBackItemsParentAndChildren() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        database.close()
        addTrigger(
            "CREATE TRIGGER fail_change027_second_child BEFORE INSERT ON action_ledger_mutations " +
                "WHEN NEW.target_id = 'child-failure-item-2' " +
                "BEGIN SELECT RAISE(ABORT, 'forced Change 027 child failure'); END",
        )
        openFreshDatabase()

        val result = executor(
            itemIds = listOf("child-failure-item-1", "child-failure-item-2"),
            entryIds = listOf("child-failure-entry"),
            times = listOf(Instant.parse("2026-09-19T12:06:00Z")),
        ).execute(plan(compras("first"), compras("second")))

        assertTrue(result is CapturePlanListExecutionResult.Failed)
        assertNull(database.listItemDao().getById("child-failure-item-1"))
        assertNull(database.listItemDao().getById("child-failure-item-2"))
        assertNull(database.actionLedgerEntryDao().getById("child-failure-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("child-failure-entry").isEmpty())
    }

    @Test
    fun cancellationAfterItemInsertsPropagatesAndRollsBackTheCompletePlan() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val action = RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("cancel-item-1", "cancel-item-2")),
            actionLedgerIdProvider = object : ActionLedgerIdProvider {
                override fun nextEntryId(): ActionLedgerEntryId =
                    throw CancellationException("cancel after list inserts")
            },
            clock = QueueListClock(listOf(Instant.parse("2026-09-19T12:07:00Z"))),
        )
        var cancellationCaught = false
        try {
            action.execute(plan(compras("first"), compras("second")))
        } catch (_: CancellationException) {
            cancellationCaught = true
        }

        assertTrue(cancellationCaught)
        assertNull(database.listItemDao().getById("cancel-item-1"))
        assertNull(database.listItemDao().getById("cancel-item-2"))
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
        assertTrue(database.actionLedgerMutationDao().getByEntryId("cancel-entry").isEmpty())
    }

    @Test
    fun targetedUndoPreservesUnrelatedRowsAndExecutionAndUndoSurviveReopen() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val saved = executor(
            itemIds = listOf("undo-item-1", "undo-item-2"),
            entryIds = listOf("undo-entry"),
            times = listOf(Instant.parse("2026-09-19T12:08:00Z")),
        ).execute(plan(compras("one"), compras("two"))).requireExecuted()
        val unrelated = RoomReversibleListItemActions(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("unrelated-manual-item")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("unrelated-manual-entry")),
            clock = QueueListClock(listOf(Instant.parse("2026-09-19T12:08:30Z"))),
        ).create(BuiltInListDefinitions.COMPRAS.id, "unrelated") as CreateListItemActionResult.Saved

        reopenDatabase()
        assertNotNull(database.listItemDao().getById("undo-item-1"))
        assertNotNull(database.listItemDao().getById("undo-item-2"))
        assertNull(database.actionLedgerEntryDao().getById("undo-entry")?.undoneAtEpochMillis)
        assertEquals("plan-source", database.actionLedgerEntryDao().getById("undo-entry")?.sourceCaptureId)

        val undoAt = Instant.parse("2026-09-19T12:09:00Z")
        val undone = RoomCapturePlanListExecutor(
            database = database,
            clock = QueueListClock(listOf(undoAt)),
        ).undoExecution(ActionLedgerEntryId("undo-entry"), saved.items.map(ListItem::id))
        assertEquals(
            UndoCapturePlanListExecutionResult.Undone(
                actionLedgerEntryId = ActionLedgerEntryId("undo-entry"),
                itemIds = listOf(ListItemId("undo-item-1"), ListItemId("undo-item-2")),
            ),
            undone,
        )
        assertNull(database.listItemDao().getById("undo-item-1"))
        assertNull(database.listItemDao().getById("undo-item-2"))
        assertNotNull(database.listItemDao().getById(unrelated.item.id.value))
        assertEquals(undoAt.toEpochMilli(), database.actionLedgerEntryDao()
            .getById("undo-entry")?.undoneAtEpochMillis)

        reopenDatabase()
        assertNull(database.listItemDao().getById("undo-item-1"))
        assertNull(database.listItemDao().getById("undo-item-2"))
        assertEquals(undoAt.toEpochMilli(), database.actionLedgerEntryDao()
            .getById("undo-entry")?.undoneAtEpochMillis)
        assertNotNull(database.listItemDao().getById(unrelated.item.id.value))
        assertNull(database.actionLedgerEntryDao()
            .getById(unrelated.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    @Test
    fun expectedIdAndOrderMismatchesLeaveEveryTargetAndLedgerActive() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val saved = executor(
            itemIds = listOf("mismatch-item-1", "mismatch-item-2"),
            entryIds = listOf("mismatch-entry"),
            times = listOf(Instant.parse("2026-09-19T12:10:00Z")),
        ).execute(plan(compras("one"), compras("two"))).requireExecuted()
        val action = RoomCapturePlanListExecutor(database)

        assertEquals(
            UndoCapturePlanListExecutionResult.TargetMismatch,
            action.undoExecution(
                ActionLedgerEntryId("mismatch-entry"),
                listOf(ListItemId("mismatch-item-2"), ListItemId("mismatch-item-1")),
            ),
        )
        assertEquals(
            UndoCapturePlanListExecutionResult.TargetMismatch,
            action.undoExecution(
                ActionLedgerEntryId("mismatch-entry"),
                listOf(ListItemId("mismatch-item-1"), ListItemId("other-item")),
            ),
        )
        assertEquals(
            UndoCapturePlanListExecutionResult.TargetMismatch,
            action.undoExecution(ActionLedgerEntryId("mismatch-entry"), saved.items.take(1).map(ListItem::id)),
        )
        assertNotNull(database.listItemDao().getById("mismatch-item-1"))
        assertNotNull(database.listItemDao().getById("mismatch-item-2"))
        assertNull(database.actionLedgerEntryDao().getById("mismatch-entry")?.undoneAtEpochMillis)
    }

    @Test
    fun targetedUndoRejectsManualListEntriesWithoutCaptureProvenance() = runBlocking {
        openFreshDatabase()
        val manual = RoomReversibleListItemActions(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("manual-item")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("manual-entry")),
            clock = QueueListClock(listOf(Instant.parse("2026-09-19T12:10:30Z"))),
        ).create(BuiltInListDefinitions.COMPRAS.id, "manual item") as CreateListItemActionResult.Saved
        assertNull(database.actionLedgerEntryDao().getById("manual-entry")?.sourceCaptureId)

        val result = RoomCapturePlanListExecutor(database).undoExecution(
            manual.actionLedgerEntryId,
            listOf(manual.item.id),
        )

        assertEquals(UndoCapturePlanListExecutionResult.UnsupportedLedgerShape, result)
        assertNotNull(database.listItemDao().getById("manual-item"))
        assertNull(database.actionLedgerEntryDao().getById("manual-entry")?.undoneAtEpochMillis)
    }

    @Test
    fun missingTargetIsDetectedBeforeAnyTargetIsDeleted() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val saved = executor(
            itemIds = listOf("missing-target-item-1", "missing-target-item-2"),
            entryIds = listOf("missing-target-entry"),
            times = listOf(Instant.parse("2026-09-19T12:11:00Z")),
        ).execute(plan(compras("one"), compras("two"))).requireExecuted()
        assertEquals(1, database.listItemDao().deleteById("missing-target-item-2"))

        val result = RoomCapturePlanListExecutor(database).undoExecution(
            ActionLedgerEntryId("missing-target-entry"),
            saved.items.map(ListItem::id),
        )

        assertEquals(UndoCapturePlanListExecutionResult.TargetMissing(1), result)
        assertNotNull(database.listItemDao().getById("missing-target-item-1"))
        assertNull(database.listItemDao().getById("missing-target-item-2"))
        assertNull(database.actionLedgerEntryDao().getById("missing-target-entry")?.undoneAtEpochMillis)
    }

    @Test
    fun malformedLedgerOperationsTypesVersionsPayloadsAndShapesAreRejected() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val malformed = listOf(
            MalformedLedgerCase(
                entryId = "wrong-operation-entry",
                targetIds = listOf("wrong-operation-item"),
                mutations = listOf(mutation("wrong-operation-entry", 0, targetId = "wrong-operation-item", operation = "UPDATE")),
                expected = UndoCapturePlanListExecutionResult.UnsupportedAction(0),
            ),
            MalformedLedgerCase(
                entryId = "wrong-target-type-entry",
                targetIds = listOf("wrong-target-type-item"),
                mutations = listOf(mutation("wrong-target-type-entry", 0, targetId = "wrong-target-type-item", targetType = "note")),
                expected = UndoCapturePlanListExecutionResult.UnsupportedAction(0),
            ),
            MalformedLedgerCase(
                entryId = "wrong-version-entry",
                targetIds = listOf("wrong-version-item"),
                mutations = listOf(mutation("wrong-version-entry", 0, targetId = "wrong-version-item", payloadVersion = 2)),
                expected = UndoCapturePlanListExecutionResult.UnsupportedLedgerShape,
            ),
            MalformedLedgerCase(
                entryId = "before-state-entry",
                targetIds = listOf("before-state-item"),
                mutations = listOf(mutation("before-state-entry", 0, targetId = "before-state-item", beforeState = "unexpected")),
                expected = UndoCapturePlanListExecutionResult.UnsupportedLedgerShape,
            ),
            MalformedLedgerCase(
                entryId = "after-state-entry",
                targetIds = listOf("after-state-item"),
                mutations = listOf(mutation("after-state-entry", 0, targetId = "after-state-item", afterState = "unexpected")),
                expected = UndoCapturePlanListExecutionResult.UnsupportedLedgerShape,
            ),
            MalformedLedgerCase(
                entryId = "empty-mutations-entry",
                targetIds = emptyList(),
                mutations = emptyList(),
                expected = UndoCapturePlanListExecutionResult.UnsupportedLedgerShape,
            ),
            MalformedLedgerCase(
                entryId = "blank-target-entry",
                targetIds = listOf("   "),
                mutations = listOf(mutation("blank-target-entry", 0, targetId = "   ")),
                expected = UndoCapturePlanListExecutionResult.UnsupportedLedgerShape,
            ),
            MalformedLedgerCase(
                entryId = "duplicate-target-entry",
                targetIds = listOf("duplicate-target-item"),
                mutations = listOf(
                    mutation("duplicate-target-entry", 0, targetId = "duplicate-target-item"),
                    mutation("duplicate-target-entry", 1, targetId = "duplicate-target-item"),
                ),
                expected = UndoCapturePlanListExecutionResult.UnsupportedLedgerShape,
                expectedIds = listOf("duplicate-target-item", "duplicate-target-item"),
            ),
            MalformedLedgerCase(
                entryId = "gapped-position-entry",
                targetIds = listOf("gapped-position-item"),
                mutations = listOf(mutation("gapped-position-entry", 1, targetId = "gapped-position-item")),
                expected = UndoCapturePlanListExecutionResult.UnsupportedLedgerShape,
            ),
        )
        malformed.forEach { case ->
            case.targetIds.filter(String::isNotBlank).distinct().forEach { id ->
                insertItem(id, text = "preserve $id")
            }
            database.actionLedgerEntryDao().insert(
                ActionLedgerEntryEntity(
                    id = case.entryId,
                    occurredAtEpochMillis = 1_000,
                    sourceCaptureId = "plan-source",
                ),
            )
            if (case.mutations.isNotEmpty()) {
                database.actionLedgerMutationDao().insertAll(case.mutations)
            }
        }

        val action = RoomCapturePlanListExecutor(database)
        malformed.forEach { case ->
            assertEquals(
                "${case.entryId} should be rejected without mutation",
                case.expected,
                action.undoExecution(
                    ActionLedgerEntryId(case.entryId),
                    case.expectedIds.map(::ListItemId),
                ),
            )
        }
        malformed.flatMap(MalformedLedgerCase::targetIds).filter(String::isNotBlank).distinct()
            .forEach { assertNotNull(database.listItemDao().getById(it)) }
        malformed.forEach { case ->
            assertNull(database.actionLedgerEntryDao().getById(case.entryId)?.undoneAtEpochMillis)
        }
    }

    @Test
    fun secondUndoReturnsAlreadyUndoneWithoutFurtherMutation() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val saved = executor(
            itemIds = listOf("second-undo-item"),
            entryIds = listOf("second-undo-entry"),
            times = listOf(Instant.parse("2026-09-19T12:12:00Z")),
        ).execute(plan(compras("one"))).requireExecuted()
        val action = RoomCapturePlanListExecutor(
            database = database,
            clock = QueueListClock(listOf(Instant.parse("2026-09-19T12:13:00Z"))),
        )

        assertTrue(
            action.undoExecution(saved.actionLedgerEntryId, saved.items.map(ListItem::id)) is
                UndoCapturePlanListExecutionResult.Undone,
        )
        val undoneAt = database.actionLedgerEntryDao().getById("second-undo-entry")?.undoneAtEpochMillis
        val secondResult = action.undoExecution(saved.actionLedgerEntryId, saved.items.map(ListItem::id))

        assertEquals(UndoCapturePlanListExecutionResult.AlreadyUndone, secondResult)
        assertNull(database.listItemDao().getById("second-undo-item"))
        assertEquals(undoneAt, database.actionLedgerEntryDao()
            .getById("second-undo-entry")?.undoneAtEpochMillis)
    }

    @Test
    fun missingLedgerEntryReturnsWithoutDeletingExpectedItem() = runBlocking {
        openFreshDatabase()
        insertItem("orphan-item", "preserve me")

        val result = RoomCapturePlanListExecutor(database).undoExecution(
            ActionLedgerEntryId("missing-entry"),
            listOf(ListItemId("orphan-item")),
        )

        assertEquals(UndoCapturePlanListExecutionResult.MissingLedgerEntry, result)
        assertNotNull(database.listItemDao().getById("orphan-item"))
        assertNull(database.actionLedgerEntryDao().getById("missing-entry"))
    }

    @Test
    fun deleteFailureAfterEarlierDeleteRollsBackAllTargets() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        executor(
            itemIds = listOf("delete-failure-item-1", "delete-failure-item-2"),
            entryIds = listOf("delete-failure-entry"),
            times = listOf(Instant.parse("2026-09-19T12:14:00Z")),
        ).execute(plan(compras("one"), compras("two"))).requireExecuted()
        database.close()
        addTrigger(
            "CREATE TRIGGER fail_change027_second_delete BEFORE DELETE ON list_items " +
                "WHEN OLD.id = 'delete-failure-item-2' " +
                "BEGIN SELECT RAISE(ABORT, 'forced Change 027 delete failure'); END",
        )
        openFreshDatabase()

        val result = RoomCapturePlanListExecutor(database).undoExecution(
            ActionLedgerEntryId("delete-failure-entry"),
            listOf(ListItemId("delete-failure-item-1"), ListItemId("delete-failure-item-2")),
        )

        assertTrue(result is UndoCapturePlanListExecutionResult.Failed)
        assertNotNull(database.listItemDao().getById("delete-failure-item-1"))
        assertNotNull(database.listItemDao().getById("delete-failure-item-2"))
        assertNull(database.actionLedgerEntryDao().getById("delete-failure-entry")?.undoneAtEpochMillis)
    }

    @Test
    fun markUndoneFailureRollsBackAllTargetDeletes() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        executor(
            itemIds = listOf("mark-failure-item-1", "mark-failure-item-2"),
            entryIds = listOf("mark-failure-entry"),
            times = listOf(Instant.parse("2026-09-19T12:15:00Z")),
        ).execute(plan(compras("one"), compras("two"))).requireExecuted()
        database.close()
        addTrigger(
            "CREATE TRIGGER fail_change027_undo_mark BEFORE UPDATE OF undone_at_epoch_millis " +
                "ON action_ledger_entries WHEN OLD.id = 'mark-failure-entry' " +
                "BEGIN SELECT RAISE(ABORT, 'forced Change 027 mark failure'); END",
        )
        openFreshDatabase()

        val result = RoomCapturePlanListExecutor(database).undoExecution(
            ActionLedgerEntryId("mark-failure-entry"),
            listOf(ListItemId("mark-failure-item-1"), ListItemId("mark-failure-item-2")),
        )

        assertTrue(result is UndoCapturePlanListExecutionResult.Failed)
        assertNotNull(database.listItemDao().getById("mark-failure-item-1"))
        assertNotNull(database.listItemDao().getById("mark-failure-item-2"))
        assertNull(database.actionLedgerEntryDao().getById("mark-failure-entry")?.undoneAtEpochMillis)
    }

    @Test
    fun undoCancellationAfterDeletesPropagatesAndRestoresEveryTarget() = runBlocking {
        openFreshDatabase()
        insertSourceCapture()
        val clock = CancelOnSecondClock(
            first = Instant.parse("2026-09-19T12:16:00Z"),
        )
        val saved = RoomCapturePlanListExecutor(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("undo-cancel-item-1", "undo-cancel-item-2")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("undo-cancel-entry")),
            clock = clock,
        ).execute(plan(compras("one"), compras("two"))).requireExecuted()
        var cancellationCaught = false
        try {
            RoomCapturePlanListExecutor(database, clock = clock)
                .undoExecution(saved.actionLedgerEntryId, saved.items.map(ListItem::id))
        } catch (_: CancellationException) {
            cancellationCaught = true
        }

        assertTrue(cancellationCaught)
        assertNotNull(database.listItemDao().getById("undo-cancel-item-1"))
        assertNotNull(database.listItemDao().getById("undo-cancel-item-2"))
        assertNull(database.actionLedgerEntryDao().getById("undo-cancel-entry")?.undoneAtEpochMillis)
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun reopenDatabase() {
        database.close()
        openFreshDatabase()
    }

    private suspend fun insertSourceCapture(id: String = "plan-source") {
        database.captureDao().insert(
            Capture(
                id = CaptureId(id),
                originalInput = CaptureInput.Text("original capture"),
                capturedAt = Instant.parse("2026-09-19T12:00:00Z"),
            ).toEntity(),
        )
    }

    private suspend fun insertItem(id: String, text: String) {
        database.listItemDao().insert(
            ListItemEntity(
                id = id,
                listDefinitionId = BuiltInListDefinitions.COMPRAS.id.value,
                listSessionId = null,
                text = text,
                isCompleted = false,
                createdAtEpochMillis = 1_000,
            ),
        )
    }

    private fun executor(
        itemIds: List<String> = emptyList(),
        entryIds: List<String> = emptyList(),
        times: List<Instant> = emptyList(),
    ): RoomCapturePlanListExecutor = RoomCapturePlanListExecutor(
        database = database,
        itemIdProvider = QueueItemIdProvider(itemIds),
        actionLedgerIdProvider = QueueEntryIdProvider(entryIds),
        clock = QueueListClock(times),
    )

    private fun plan(
        vararg actions: CapturePlanAction,
        sourceCaptureId: String = "plan-source",
    ): CapturePlan = CapturePlan(
        sourceCaptureId = CaptureId(sourceCaptureId),
        actions = actions.toList(),
    )

    private fun compras(text: String): CapturePlanAction.AddListItem =
        CapturePlanAction.AddListItem(BuiltInListDefinitions.COMPRAS.id, text)

    private fun assertMutation(
        mutation: ActionLedgerMutationEntity,
        position: Int,
        targetId: String,
    ) {
        assertEquals(position, mutation.position)
        assertEquals(ActionLedgerOperation.CREATE.name, mutation.operation)
        assertEquals(LIST_ITEM_ACTION_LEDGER_TARGET_TYPE, mutation.targetType)
        assertEquals(targetId, mutation.targetId)
        assertEquals(LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION, mutation.payloadVersion)
        assertNull(mutation.beforeState)
        assertNull(mutation.afterState)
    }

    private fun mutation(
        entryId: String,
        position: Int,
        targetId: String,
        operation: String = ActionLedgerOperation.CREATE.name,
        targetType: String = LIST_ITEM_ACTION_LEDGER_TARGET_TYPE,
        payloadVersion: Int = LIST_ITEM_CREATE_ACTION_PAYLOAD_VERSION,
        beforeState: String? = null,
        afterState: String? = null,
    ): ActionLedgerMutationEntity = ActionLedgerMutationEntity(
        actionLedgerEntryId = entryId,
        position = position,
        operation = operation,
        targetType = targetType,
        targetId = targetId,
        payloadVersion = payloadVersion,
        beforeState = beforeState,
        afterState = afterState,
    )

    private fun addTrigger(sql: String) {
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(sql)
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private fun CapturePlanListExecutionResult.requireExecuted(): CapturePlanListExecutionResult.Executed =
        this as? CapturePlanListExecutionResult.Executed
            ?: throw AssertionError("Expected CapturePlan execution success, got $this")

    private data class MalformedLedgerCase(
        val entryId: String,
        val targetIds: List<String>,
        val mutations: List<ActionLedgerMutationEntity>,
        val expected: UndoCapturePlanListExecutionResult,
        val expectedIds: List<String> = targetIds,
    )

    private class QueueItemIdProvider(ids: List<String>) : ListItemIdProvider {
        private val ids = ArrayDeque(ids)

        override fun nextItemId(): ListItemId = ListItemId(ids.removeFirst())
    }

    private class QueueEntryIdProvider(ids: List<String>) : ActionLedgerIdProvider {
        private val ids = ArrayDeque(ids)

        override fun nextEntryId(): ActionLedgerEntryId = ActionLedgerEntryId(ids.removeFirst())
    }

    private class QueueListClock(times: List<Instant>) : ListClock {
        private val times = ArrayDeque(times)

        override fun now(): Instant = times.removeFirst()
    }

    private class CountingItemIdProvider : ListItemIdProvider {
        var calls: Int = 0
            private set

        override fun nextItemId(): ListItemId = ListItemId("unused-item-${calls++}")
    }

    private class CountingEntryIdProvider : ActionLedgerIdProvider {
        var calls: Int = 0
            private set

        override fun nextEntryId(): ActionLedgerEntryId = ActionLedgerEntryId("unused-entry-${calls++}")
    }

    private class CountingListClock : ListClock {
        var calls: Int = 0
            private set

        override fun now(): Instant = Instant.parse("2026-09-19T12:00:00Z").also { calls++ }
    }

    private class CancelOnSecondClock(
        private val first: Instant,
    ) : ListClock {
        private var calls: Int = 0

        override fun now(): Instant {
            calls += 1
            if (calls == 1) return first
            throw CancellationException("cancel before marking batch undone")
        }
    }
}

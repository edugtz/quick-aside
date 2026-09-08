package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.lists.CreateListItemActionResult
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListItemIdProvider
import com.edu.quickaside.application.lists.ReversibleListItemActions
import com.edu.quickaside.application.lists.UndoListItemCreateResult
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReversibleListItemActionsDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-019-reversible-list-" + UUID.randomUUID() + ".db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun comprasCreatePersistsExactItemAndOneLedgerMutation() = runBlocking {
        openFreshDatabase()
        val createdAt = Instant.parse("2026-09-08T10:00:00Z")
        val result = actions(
            itemIds = listOf("compras-item"),
            entryIds = listOf("compras-entry"),
            times = listOf(createdAt),
        ).create(
            listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
            text = "  Cuerdas guitarra  ",
            listSessionId = null,
        ) as CreateListItemActionResult.Saved

        assertEquals(result.item, database.listItemDao().getById("compras-item")?.toDomain())
        assertEquals(createdAt, result.item.createdAt)
        assertEquals(null, result.item.listSessionId)
        assertEquals("  Cuerdas guitarra  ", result.item.text)
        val entry = database.actionLedgerEntryDao().getById("compras-entry")
        assertNotNull(entry)
        assertEquals(createdAt.toEpochMilli(), entry?.occurredAtEpochMillis)
        assertNull(entry?.undoneAtEpochMillis)
        assertNull(entry?.sourceCaptureId)
        val mutations = database.actionLedgerMutationDao().getByEntryId("compras-entry")
        assertEquals(1, mutations.size)
        assertEquals("CREATE", mutations.single().operation)
        assertEquals("list_item", mutations.single().targetType)
        assertEquals("compras-item", mutations.single().targetId)
        assertEquals(1, mutations.single().payloadVersion)
        assertNull(mutations.single().beforeState)
        assertNull(mutations.single().afterState)
    }

    @Test
    fun mandadoCreateUsesActiveSessionAndPreservesExactText() = runBlocking {
        openFreshDatabase()
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "active-mandado-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = 1_000,
            ),
        )

        val result = actions(
            itemIds = listOf("mandado-item"),
            entryIds = listOf("mandado-entry"),
            times = listOf(Instant.parse("2026-09-08T10:01:00Z")),
        ).create(
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "  Jabón  ",
            listSessionId = null,
        ) as CreateListItemActionResult.Saved

        assertEquals(ListSessionId("active-mandado-session"), result.item.listSessionId)
        assertEquals(result.item, database.listItemDao().getById("mandado-item")?.toDomain())
        assertEquals(
            "mandado-item",
            database.actionLedgerMutationDao().getByEntryId("mandado-entry").single().targetId,
        )
    }

    @Test
    fun existingCreateValidationOutcomesRemainDeterministic() = runBlocking {
        openFreshDatabase()
        val action = RoomReversibleListItemActions(database)

        assertEquals(
            CreateListItemActionResult.BlankText,
            action.create(BuiltInListDefinitions.COMPRAS.id, " \t\n "),
        )
        assertEquals(
            CreateListItemActionResult.MissingDefinition,
            action.create(ListDefinitionId("missing"), "producto"),
        )
        assertEquals(
            CreateListItemActionResult.NoActiveSession,
            action.create(BuiltInListDefinitions.MANDADO.id, "producto"),
        )

        database.listSessionDao().insert(
            ListSessionEntity(
                id = "ended-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = 2_000,
            ),
        )
        database.listSessionDao().finishActive("ended-session", 3_000)
        assertEquals(
            CreateListItemActionResult.MissingSession,
            action.create(
                BuiltInListDefinitions.MANDADO.id,
                "producto",
                ListSessionId("does-not-exist"),
            ),
        )
        assertEquals(
            CreateListItemActionResult.SessionNotActive,
            action.create(
                BuiltInListDefinitions.MANDADO.id,
                "producto",
                ListSessionId("ended-session"),
            ),
        )

        database.listSessionDao().insert(
            ListSessionEntity(
                id = "wrong-definition-session",
                listDefinitionId = BuiltInListDefinitions.COMPRAS.id.value,
                startedAtEpochMillis = 4_000,
            ),
        )
        assertEquals(
            CreateListItemActionResult.SessionDefinitionMismatch,
            action.create(
                BuiltInListDefinitions.MANDADO.id,
                "producto",
                ListSessionId("wrong-definition-session"),
            ),
        )
        assertEquals(
            CreateListItemActionResult.SessionNotAllowed,
            action.create(
                BuiltInListDefinitions.COMPRAS.id,
                "producto",
                ListSessionId("wrong-definition-session"),
            ),
        )
        assertTrue(database.listItemDao().getContinuousByDefinitionId("compras").isEmpty())
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())
    }

    @Test
    fun createChildLedgerFailureRollsBackItemParentAndMutation() = runBlocking {
        openFreshDatabase()
        assertTrue(database.actionLedgerEntryDao().getRecent(1).isEmpty())
        database.close()
        addCreateMutationFailureTrigger()
        openFreshDatabase()

        val result = actions(
            itemIds = listOf("rollback-item"),
            entryIds = listOf("rollback-entry"),
            times = listOf(Instant.parse("2026-09-08T10:02:00Z")),
        ).create(BuiltInListDefinitions.COMPRAS.id, "rollback")

        assertTrue(result is CreateListItemActionResult.Failed)
        assertNull(database.listItemDao().getById("rollback-item"))
        assertNull(database.actionLedgerEntryDao().getById("rollback-entry"))
        assertTrue(database.actionLedgerMutationDao().getByEntryId("rollback-entry").isEmpty())
    }

    @Test
    fun undoRemovesOnlyExactTargetMarksEntryAndPersistsAcrossReopen() = runBlocking {
        openFreshDatabase()
        val action = actions(
            itemIds = listOf("item-one", "item-two"),
            entryIds = listOf("entry-one", "entry-two"),
            times = listOf(
                Instant.parse("2026-09-08T10:03:00Z"),
                Instant.parse("2026-09-08T10:04:00Z"),
                Instant.parse("2026-09-08T10:05:00Z"),
            ),
        )
        val first = action.create(BuiltInListDefinitions.COMPRAS.id, "uno")
            as CreateListItemActionResult.Saved
        val second = action.create(BuiltInListDefinitions.COMPRAS.id, "dos")
            as CreateListItemActionResult.Saved

        assertEquals(
            UndoListItemCreateResult.Undone(first.actionLedgerEntryId, first.item.id),
            action.undoCreate(first.actionLedgerEntryId, first.item.id),
        )
        assertNull(database.listItemDao().getById(first.item.id.value))
        assertNotNull(database.listItemDao().getById(second.item.id.value))
        assertNotNull(database.actionLedgerEntryDao().getById(second.actionLedgerEntryId.value))
        assertNotNull(database.actionLedgerEntryDao().getById(first.actionLedgerEntryId.value)?.undoneAtEpochMillis)

        database.close()
        openFreshDatabase()
        val reopenedEntry = database.actionLedgerEntryDao().getById(first.actionLedgerEntryId.value)
        assertNotNull(reopenedEntry?.undoneAtEpochMillis)
        assertEquals(
            UndoListItemCreateResult.AlreadyUndone,
            RoomReversibleListItemActions(database).undoCreate(
                first.actionLedgerEntryId,
                first.item.id,
            ),
        )
        assertNotNull(database.listItemDao().getById(second.item.id.value))
    }

    @Test
    fun undoRejectsMissingEntryMismatchAndMissingTargetWithoutChangingActiveEntry() = runBlocking {
        openFreshDatabase()
        val action = actions(
            itemIds = listOf("target-item"),
            entryIds = listOf("target-entry"),
            times = listOf(Instant.parse("2026-09-08T10:06:00Z")),
        )
        val saved = action.create(BuiltInListDefinitions.COMPRAS.id, "target")
            as CreateListItemActionResult.Saved

        assertEquals(
            UndoListItemCreateResult.MissingLedgerEntry,
            action.undoCreate(ActionLedgerEntryId("missing-entry"), saved.item.id),
        )
        assertEquals(
            UndoListItemCreateResult.TargetMismatch,
            action.undoCreate(saved.actionLedgerEntryId, ListItemId("different-item")),
        )
        assertNotNull(database.listItemDao().getById(saved.item.id.value))
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)

        database.listItemDao().deleteById(saved.item.id.value)
        assertEquals(
            UndoListItemCreateResult.TargetMissing,
            action.undoCreate(saved.actionLedgerEntryId, saved.item.id),
        )
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    @Test
    fun undoRejectsUnsupportedAndMalformedLedgerShapesWithoutDeletingTargets() = runBlocking {
        openFreshDatabase()
        insertMalformedAction(
            entryId = "wrong-operation",
            itemId = "wrong-operation-item",
            operation = "UPDATE",
        )
        insertMalformedAction(
            entryId = "wrong-target-type",
            itemId = "wrong-target-type-item",
            targetType = "note",
        )
        insertMalformedAction(
            entryId = "wrong-version",
            itemId = "wrong-version-item",
            payloadVersion = 2,
        )
        insertMalformedAction(
            entryId = "multiple-mutations",
            itemId = "multiple-mutations-item",
            mutationCount = 2,
        )
        val action = RoomReversibleListItemActions(database)

        assertEquals(
            UndoListItemCreateResult.UnsupportedAction,
            action.undoCreate(ActionLedgerEntryId("wrong-operation"), ListItemId("wrong-operation-item")),
        )
        assertEquals(
            UndoListItemCreateResult.UnsupportedAction,
            action.undoCreate(ActionLedgerEntryId("wrong-target-type"), ListItemId("wrong-target-type-item")),
        )
        assertEquals(
            UndoListItemCreateResult.UnsupportedLedgerShape,
            action.undoCreate(ActionLedgerEntryId("wrong-version"), ListItemId("wrong-version-item")),
        )
        assertEquals(
            UndoListItemCreateResult.UnsupportedLedgerShape,
            action.undoCreate(ActionLedgerEntryId("multiple-mutations"), ListItemId("multiple-mutations-item")),
        )
        listOf(
            "wrong-operation-item",
            "wrong-target-type-item",
            "wrong-version-item",
            "multiple-mutations-item",
        ).forEach { assertNotNull(database.listItemDao().getById(it)) }
    }

    @Test
    fun forcedUndoMarkFailureRollsBackDeleteAndLedgerUpdate() = runBlocking {
        openFreshDatabase()
        val saved = actions(
            itemIds = listOf("undo-failure-item"),
            entryIds = listOf("undo-failure-entry"),
            times = listOf(Instant.parse("2026-09-08T10:07:00Z")),
        ).create(BuiltInListDefinitions.COMPRAS.id, "undo failure")
            as CreateListItemActionResult.Saved
        database.close()
        addUndoUpdateFailureTrigger()
        openFreshDatabase()

        val result = actions(
            times = listOf(Instant.parse("2026-09-08T10:08:00Z")),
        ).undoCreate(saved.actionLedgerEntryId, saved.item.id)

        assertTrue(result is UndoListItemCreateResult.Failed)
        assertNotNull(database.listItemDao().getById(saved.item.id.value))
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    @Test
    fun cancellationPropagatesAndUndoCancellationRollsBackDelete() = runBlocking {
        openFreshDatabase()
        var createCaught = false
        try {
            RoomReversibleListItemActions(
                database = database,
                clock = ListClock { throw CancellationException("create cancelled") },
            ).create(BuiltInListDefinitions.COMPRAS.id, "cancelled")
        } catch (_: CancellationException) {
            createCaught = true
        }
        assertTrue(createCaught)
        assertTrue(database.actionLedgerEntryDao().getRecent(50).isEmpty())

        val saved = actions(
            itemIds = listOf("cancel-item"),
            entryIds = listOf("cancel-entry"),
            times = listOf(Instant.parse("2026-09-08T10:09:00Z")),
        ).create(BuiltInListDefinitions.COMPRAS.id, "cancel undo")
            as CreateListItemActionResult.Saved
        var undoCaught = false
        try {
            RoomReversibleListItemActions(
                database = database,
                clock = ListClock { throw CancellationException("undo cancelled") },
            ).undoCreate(saved.actionLedgerEntryId, saved.item.id)
        } catch (_: CancellationException) {
            undoCaught = true
        }

        assertTrue(undoCaught)
        assertNotNull(database.listItemDao().getById(saved.item.id.value))
        assertNull(database.actionLedgerEntryDao().getById(saved.actionLedgerEntryId.value)?.undoneAtEpochMillis)
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun actions(
        itemIds: List<String> = emptyList(),
        entryIds: List<String> = emptyList(),
        times: List<Instant> = emptyList(),
    ): ReversibleListItemActions = RoomReversibleListItemActions(
        database = database,
        itemIdProvider = QueueItemIdProvider(itemIds),
        actionLedgerIdProvider = QueueEntryIdProvider(entryIds),
        clock = QueueListClock(times),
    )

    private suspend fun insertMalformedAction(
        entryId: String,
        itemId: String,
        operation: String = "CREATE",
        targetType: String = "list_item",
        payloadVersion: Int = 1,
        mutationCount: Int = 1,
    ) {
        database.listItemDao().insert(
            ListItemEntity(
                id = itemId,
                listDefinitionId = BuiltInListDefinitions.COMPRAS.id.value,
                text = itemId,
                isCompleted = false,
                createdAtEpochMillis = 10_000,
            ),
        )
        database.actionLedgerEntryDao().insert(
            ActionLedgerEntryEntity(
                id = entryId,
                occurredAtEpochMillis = 10_000,
            ),
        )
        database.actionLedgerMutationDao().insertAll(
            (0 until mutationCount).map { position ->
                ActionLedgerMutationEntity(
                    actionLedgerEntryId = entryId,
                    position = position,
                    operation = operation,
                    targetType = targetType,
                    targetId = itemId,
                    payloadVersion = payloadVersion,
                )
            },
        )
    }

    private fun addCreateMutationFailureTrigger() {
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_change019_create_mutation BEFORE INSERT ON action_ledger_mutations " +
                    "WHEN NEW.target_id = 'rollback-item' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced Change 019 create failure'); END",
            )
        }
    }

    private fun addUndoUpdateFailureTrigger() {
        BundledSQLiteDriver().open(context.getDatabasePath(databaseName).absolutePath).use { connection ->
            connection.execute(
                "CREATE TRIGGER fail_change019_undo_update BEFORE UPDATE OF undone_at_epoch_millis ON action_ledger_entries " +
                    "WHEN OLD.id = 'undo-failure-entry' " +
                    "BEGIN SELECT RAISE(ABORT, 'forced Change 019 undo failure'); END",
            )
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private class QueueListClock(times: List<Instant>) : ListClock {
        private val times = ArrayDeque(times)

        override fun now(): Instant = times.removeFirst()
    }

    private class QueueItemIdProvider(ids: List<String>) : ListItemIdProvider {
        private val ids = ArrayDeque(ids)

        override fun nextItemId(): ListItemId = ListItemId(ids.removeFirst())
    }

    private class QueueEntryIdProvider(ids: List<String>) : ActionLedgerIdProvider {
        private val ids = ArrayDeque(ids)

        override fun nextEntryId(): ActionLedgerEntryId = ActionLedgerEntryId(ids.removeFirst())
    }
}

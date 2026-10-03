package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.actions.ActionLedgerIdProvider
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.CreateListItemActionResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListIdProvider
import com.edu.quickaside.application.lists.ListItemIdProvider
import com.edu.quickaside.application.lists.SessionStartResult
import com.edu.quickaside.domain.common.ActionLedgerEntryId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListSessionActivityDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "list-session-activity-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun newSessionInitializesActivityFromCreationTime() = runBlocking {
        val startedAt = Instant.parse("2026-09-19T10:00:00Z")
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(sessionIds = listOf("activity-session")),
            clock = ListClock { startedAt },
        )

        val created = store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created

        assertEquals(startedAt, created.session.startedAt)
        assertEquals(startedAt, created.session.lastActivityAt)
        assertEquals(
            startedAt.toEpochMilli(),
            database.listSessionDao().getById("activity-session")?.lastActivityAtEpochMillis,
        )
    }

    @Test
    fun addingItemsAdvancesActivity() = runBlocking {
        val startedAt = Instant.parse("2026-09-19T10:00:00Z")
        val firstItemAt = Instant.parse("2026-09-19T10:01:00Z")
        val secondItemAt = Instant.parse("2026-09-19T10:02:00Z")
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(
                sessionIds = listOf("activity-session"),
                itemIds = listOf("item-one", "item-two"),
            ),
            clock = QueueListClock(listOf(startedAt, firstItemAt, secondItemAt)),
        )
        store.startSession(BuiltInListDefinitions.MANDADO.id) as SessionStartResult.Created

        store.addItem(BuiltInListDefinitions.MANDADO.id, "uno") as AddListItemResult.Saved
        assertEquals(
            firstItemAt.toEpochMilli(),
            database.listSessionDao().getById("activity-session")?.lastActivityAtEpochMillis,
        )

        store.addItem(BuiltInListDefinitions.MANDADO.id, "dos") as AddListItemResult.Saved
        assertEquals(
            secondItemAt.toEpochMilli(),
            database.listSessionDao().getById("activity-session")?.lastActivityAtEpochMillis,
        )
    }

    @Test
    fun completionAndReopenAdvanceActivity() = runBlocking {
        val startedAt = Instant.parse("2026-09-19T10:00:00Z")
        val itemAt = Instant.parse("2026-09-19T10:01:00Z")
        val completedAt = Instant.parse("2026-09-19T10:02:00Z")
        val reopenedAt = Instant.parse("2026-09-19T10:03:00Z")
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(
                sessionIds = listOf("activity-session"),
                itemIds = listOf("activity-item"),
            ),
            clock = QueueListClock(listOf(startedAt, itemAt, completedAt, reopenedAt)),
        )
        store.startSession(BuiltInListDefinitions.MANDADO.id) as SessionStartResult.Created
        val saved = store.addItem(BuiltInListDefinitions.MANDADO.id, "pollo")
            as AddListItemResult.Saved

        val completed = store.setItemCompleted(saved.item.id, true)
            as ItemCompletionResult.Updated
        assertEquals(true, completed.item.isCompleted)
        assertEquals(
            completedAt.toEpochMilli(),
            database.listSessionDao().getById("activity-session")?.lastActivityAtEpochMillis,
        )

        val reopened = store.setItemCompleted(saved.item.id, false)
            as ItemCompletionResult.Updated
        assertEquals(false, reopened.item.isCompleted)
        assertEquals(
            reopenedAt.toEpochMilli(),
            database.listSessionDao().getById("activity-session")?.lastActivityAtEpochMillis,
        )
    }

    @Test
    fun toggleAdvancesActivity() = runBlocking {
        val startedAt = Instant.parse("2026-09-19T10:00:00Z")
        val itemAt = Instant.parse("2026-09-19T10:01:00Z")
        val toggledAt = Instant.parse("2026-09-19T10:02:00Z")
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(
                sessionIds = listOf("activity-session"),
                itemIds = listOf("activity-item"),
            ),
            clock = QueueListClock(listOf(startedAt, itemAt, toggledAt)),
        )
        store.startSession(BuiltInListDefinitions.MANDADO.id) as SessionStartResult.Created
        val saved = store.addItem(BuiltInListDefinitions.MANDADO.id, "pollo")
            as AddListItemResult.Saved

        store.toggleItemCompleted(saved.item.id) as ItemCompletionResult.Updated

        assertEquals(
            toggledAt.toEpochMilli(),
            database.listSessionDao().getById("activity-session")?.lastActivityAtEpochMillis,
        )
    }

    @Test
    fun idempotentCompletionWriteDoesNotFabricateActivity() = runBlocking {
        val startedAt = Instant.parse("2026-09-19T10:00:00Z")
        val itemAt = Instant.parse("2026-09-19T10:01:00Z")
        val completedAt = Instant.parse("2026-09-19T10:02:00Z")
        val idempotentAt = Instant.parse("2026-09-19T10:03:00Z")
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(
                sessionIds = listOf("activity-session"),
                itemIds = listOf("activity-item"),
            ),
            clock = QueueListClock(listOf(startedAt, itemAt, completedAt, idempotentAt)),
        )
        store.startSession(BuiltInListDefinitions.MANDADO.id) as SessionStartResult.Created
        val saved = store.addItem(BuiltInListDefinitions.MANDADO.id, "pollo")
            as AddListItemResult.Saved
        store.setItemCompleted(saved.item.id, true) as ItemCompletionResult.Updated

        // Boundary validation obtains a deterministic instant, but the idempotent
        // write must neither update the item nor fabricate session activity.
        val repeated = store.setItemCompleted(saved.item.id, true)
            as ItemCompletionResult.Updated

        assertEquals(true, repeated.item.isCompleted)
        assertEquals(
            completedAt.toEpochMilli(),
            database.listSessionDao().getById("activity-session")?.lastActivityAtEpochMillis,
        )
    }

    @Test
    fun passiveReadsDoNotAdvanceActivity() = runBlocking {
        val startedAt = Instant.parse("2026-09-19T10:00:00Z")
        val itemAt = Instant.parse("2026-09-19T10:01:00Z")
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(
                sessionIds = listOf("activity-session"),
                itemIds = listOf("activity-item"),
            ),
            clock = QueueListClock(listOf(startedAt, itemAt)),
        )
        val session = (store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created).session
        store.addItem(BuiltInListDefinitions.MANDADO.id, "pollo") as AddListItemResult.Saved

        store.getActiveSession(BuiltInListDefinitions.MANDADO.id)
        store.readCurrentItems(BuiltInListDefinitions.MANDADO.id)
        store.readSession(session.id)
        store.readRecentSessions(BuiltInListDefinitions.MANDADO.id)

        assertEquals(
            itemAt.toEpochMilli(),
            database.listSessionDao().getById("activity-session")?.lastActivityAtEpochMillis,
        )
    }

    @Test
    fun manualLedgeredCreateAdvancesActivity() = runBlocking {
        val sessionStartedAt = Instant.parse("2026-09-19T10:00:00Z")
        database.listSessionDao().insert(
            ListSessionEntity(
                id = "manual-session",
                listDefinitionId = BuiltInListDefinitions.MANDADO.id.value,
                startedAtEpochMillis = sessionStartedAt.toEpochMilli(),
                lastActivityAtEpochMillis = sessionStartedAt.toEpochMilli(),
            ),
        )
        val createdItemAt = Instant.parse("2026-09-19T10:04:00Z")
        val actions = RoomReversibleListItemActions(
            database = database,
            itemIdProvider = QueueItemIdProvider(listOf("manual-item")),
            actionLedgerIdProvider = QueueEntryIdProvider(listOf("manual-entry")),
            clock = ListClock { createdItemAt },
        )

        val result = actions.create(BuiltInListDefinitions.MANDADO.id, "pollo")
            as CreateListItemActionResult.Saved

        assertEquals(ListSessionId("manual-session"), result.item.listSessionId)
        assertEquals(
            createdItemAt.toEpochMilli(),
            database.listSessionDao().getById("manual-session")?.lastActivityAtEpochMillis,
        )
    }

    @Test
    fun comprasItemsNeverRequireOrTouchASession() = runBlocking {
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(itemIds = listOf("compras-item")),
            clock = ListClock { Instant.parse("2026-09-19T10:05:00Z") },
        )

        val saved = store.addItem(BuiltInListDefinitions.COMPRAS.id, "leche")
            as AddListItemResult.Saved

        assertNull(saved.item.listSessionId)
        assertNull(database.listSessionDao().getActiveByDefinitionId("compras"))
    }

    private class QueueListIdProvider(
        sessionIds: List<String> = emptyList(),
        itemIds: List<String> = emptyList(),
    ) : ListIdProvider {
        private val sessionIds = ArrayDeque(sessionIds)
        private val itemIds = ArrayDeque(itemIds)

        override fun nextSessionId(): ListSessionId = ListSessionId(sessionIds.removeFirst())

        override fun nextItemId(): ListItemId = ListItemId(itemIds.removeFirst())
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

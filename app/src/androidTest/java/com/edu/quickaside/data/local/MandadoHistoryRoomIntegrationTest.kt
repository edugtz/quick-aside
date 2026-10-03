package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListIdProvider
import com.edu.quickaside.application.lists.SessionFinishResult
import com.edu.quickaside.application.lists.SessionStartResult
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.MandadoCalendarPolicy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
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
class MandadoHistoryRoomIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-012-mandado-history-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
        context.deleteDatabase(databaseName)
    }

    @Test
    fun completedSessionsRemainOrderedAttachedAndDurableAlongsideAnActiveSession() = runBlocking {
        database = QuickAsideDatabase.create(context, databaseName)
        val clock = MutableHistoryClock(Instant.parse("2026-09-01T10:00:00Z"))
        val store = RoomListStore(
            database = database,
            idProvider = QueueListIdProvider(
                sessionIds = listOf("session-a", "session-b", "session-c"),
                itemIds = listOf("a-milk", "a-bread", "b-eggs", "c-coffee"),
            ),
            clock = clock,
            mandadoCalendarPolicy = MandadoCalendarPolicy(clock = clock, zoneId = ZoneOffset.UTC),
        )

        clock.current = Instant.parse("2026-09-01T10:00:00Z")
        val sessionA = (store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created).session
        clock.current = Instant.parse("2026-09-01T10:01:00Z")
        val aMilk = (store.addItem(
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "Leche",
            listSessionId = sessionA.id,
        ) as AddListItemResult.Saved).item
        clock.current = Instant.parse("2026-09-01T10:02:00Z")
        val aBread = (store.addItem(
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "Pan",
            listSessionId = sessionA.id,
        ) as AddListItemResult.Saved).item
        clock.current = Instant.parse("2026-09-01T10:03:00Z")
        assertTrue(store.setItemCompleted(aMilk.id, true) is ItemCompletionResult.Updated)
        clock.current = Instant.parse("2026-09-01T10:30:00Z")
        assertTrue(
            store.finishActiveSession(BuiltInListDefinitions.MANDADO.id)
                is SessionFinishResult.Finished,
        )

        clock.current = Instant.parse("2026-09-07T12:00:00Z")
        val sessionB = (store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created).session
        clock.current = Instant.parse("2026-09-07T12:01:00Z")
        val bEggs = (store.addItem(
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "Huevos",
            listSessionId = sessionB.id,
        ) as AddListItemResult.Saved).item
        clock.current = Instant.parse("2026-09-07T12:30:00Z")
        assertTrue(
            store.finishActiveSession(BuiltInListDefinitions.MANDADO.id)
                is SessionFinishResult.Finished,
        )

        clock.current = Instant.parse("2026-09-14T15:00:00Z")
        val sessionC = (store.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created).session
        clock.current = Instant.parse("2026-09-14T15:01:00Z")
        val cCoffee = (store.addItem(
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "Café",
            listSessionId = sessionC.id,
        ) as AddListItemResult.Saved).item

        val beforeReopen = store.readRecentSessions(BuiltInListDefinitions.MANDADO.id)
        assertEquals(
            listOf(sessionC.id, sessionB.id, sessionA.id),
            beforeReopen.map { it.session.id },
        )
        assertNull(beforeReopen.first { it.session.id == sessionC.id }.session.endedAt)
        assertNotNull(beforeReopen.first { it.session.id == sessionB.id }.session.endedAt)
        assertNotNull(beforeReopen.first { it.session.id == sessionA.id }.session.endedAt)
        assertEquals(
            listOf(sessionB.id, sessionA.id),
            beforeReopen
                .filter { it.session.endedAt != null }
                .map { it.session.id },
        )
        assertEquals(
            listOf(aMilk.id, aBread.id),
            beforeReopen.first { it.session.id == sessionA.id }.items.map { it.id },
        )
        assertEquals(
            listOf(bEggs.id),
            beforeReopen.first { it.session.id == sessionB.id }.items.map { it.id },
        )
        assertEquals(
            listOf(cCoffee.id),
            beforeReopen.first { it.session.id == sessionC.id }.items.map { it.id },
        )
        assertTrue(
            beforeReopen.first { it.session.id == sessionA.id }
                .items.first { it.id == aMilk.id }
                .isCompleted,
        )
        assertFalse(
            beforeReopen.first { it.session.id == sessionA.id }
                .items.first { it.id == aBread.id }
                .isCompleted,
        )

        database.close()
        database = QuickAsideDatabase.create(context, databaseName)
        val reopenedStore = RoomListStore(
            database = database,
            mandadoCalendarPolicy = MandadoCalendarPolicy(
                clock = Clock.fixed(Instant.parse("2026-09-14T15:01:00Z"), ZoneOffset.UTC),
                zoneId = ZoneOffset.UTC,
            ),
        )
        val afterReopen = reopenedStore.readRecentSessions(BuiltInListDefinitions.MANDADO.id)

        assertEquals(
            beforeReopen,
            afterReopen,
        )
        assertEquals(
            listOf(sessionB.id, sessionA.id),
            afterReopen.filter { it.session.endedAt != null }.map { it.session.id },
        )
        assertNull(afterReopen.first { it.session.id == sessionC.id }.session.endedAt)
        assertTrue(
            afterReopen.first { it.session.id == sessionA.id }
                .items.first { it.id == aMilk.id }
                .isCompleted,
        )
    }

    private class QueueListIdProvider(
        sessionIds: List<String>,
        itemIds: List<String>,
    ) : ListIdProvider {
        private val sessionIds = ArrayDeque(sessionIds)
        private val itemIds = ArrayDeque(itemIds)

        override fun nextSessionId(): ListSessionId = ListSessionId(sessionIds.removeFirst())

        override fun nextItemId(): ListItemId = ListItemId(itemIds.removeFirst())
    }

    private class MutableHistoryClock(
        var current: Instant,
    ) : Clock(), ListClock {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = current

        override fun now(): Instant = current
    }
}

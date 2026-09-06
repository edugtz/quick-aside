package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.SessionFinishResult
import com.edu.quickaside.application.lists.SessionStartResult
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MandadoRoomIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private var database: QuickAsideDatabase? = null

    @Before
    fun setUp() {
        databaseName = "change-010-mandado-room-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun mandadoSurvivesCloseReopenAndFinishWithoutDeletingHistory() = runBlocking {
        database = QuickAsideDatabase.create(context, databaseName)
        val firstStore = RoomListStore(checkNotNull(database))
        val session = (firstStore.startSession(BuiltInListDefinitions.MANDADO.id)
            as SessionStartResult.Created).session
        val firstItem = (firstStore.addItem(
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "  Leche  ",
            listSessionId = session.id,
        ) as AddListItemResult.Saved).item
        val secondItem = (firstStore.addItem(
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "Pan",
            listSessionId = session.id,
        ) as AddListItemResult.Saved).item

        assertTrue(firstStore.setItemCompleted(firstItem.id, true) is ItemCompletionResult.Updated)
        assertEquals(
            listOf(firstItem.id, secondItem.id),
            firstStore.readCurrentItems(BuiltInListDefinitions.MANDADO.id).map { it.id },
        )

        database?.close()
        database = QuickAsideDatabase.create(context, databaseName)
        val reopenedStore = RoomListStore(checkNotNull(database))
        val activeAfterReopen = reopenedStore.getActiveSession(BuiltInListDefinitions.MANDADO.id)
        assertNotNull(activeAfterReopen)
        assertEquals(session.id, activeAfterReopen?.id)
        val restoredItems = reopenedStore.readCurrentItems(BuiltInListDefinitions.MANDADO.id)
        assertEquals(true, restoredItems.first { it.id == firstItem.id }.isCompleted)
        assertFalse(restoredItems.first { it.id == secondItem.id }.isCompleted)
        assertEquals("  Leche  ", restoredItems.first { it.id == firstItem.id }.text)

        assertTrue(
            reopenedStore.finishActiveSession(BuiltInListDefinitions.MANDADO.id)
                is SessionFinishResult.Finished,
        )
        assertEquals(null, reopenedStore.getActiveSession(BuiltInListDefinitions.MANDADO.id))
        val history = reopenedStore.readSession(session.id)
        assertNotNull(history)
        assertEquals(session.id, history?.session?.id)
        assertEquals(
            setOf(firstItem.id, secondItem.id),
            history?.items?.map { it.id }?.toSet(),
        )
        assertTrue(history?.items?.first { it.id == firstItem.id }?.isCompleted == true)
        assertEquals(listOf(firstItem.id, secondItem.id), history?.items?.map { it.id })
    }
}

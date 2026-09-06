package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ItemCompletionResult
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
class ComprasRoomIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private var database: QuickAsideDatabase? = null

    @Before
    fun setUp() {
        databaseName = "change-011-compras-room-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun comprasItemsPersistAcrossCloseReopenWithoutListSessions() = runBlocking {
        database = QuickAsideDatabase.create(context, databaseName)
        val firstStore = RoomListStore(checkNotNull(database))
        val firstItem = (firstStore.addItem(
            listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
            text = "  Cuerdas guitarra  ",
            listSessionId = null,
        ) as AddListItemResult.Saved).item
        val secondItem = (firstStore.addItem(
            listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
            text = "Leche",
            listSessionId = null,
        ) as AddListItemResult.Saved).item

        assertEquals(null, firstItem.listSessionId)
        assertEquals(null, secondItem.listSessionId)
        assertTrue(firstStore.setItemCompleted(firstItem.id, true) is ItemCompletionResult.Updated)
        val beforeClose = firstStore.readCurrentItems(BuiltInListDefinitions.COMPRAS.id)
        assertEquals(listOf(firstItem.id, secondItem.id), beforeClose.map { it.id })
        assertTrue(beforeClose.first { it.id == firstItem.id }.isCompleted)
        assertFalse(beforeClose.first { it.id == secondItem.id }.isCompleted)
        assertTrue(
            database?.listSessionDao()
                ?.getByDefinitionId(BuiltInListDefinitions.COMPRAS.id.value)
                ?.isEmpty() == true,
        )

        database?.close()
        database = null
        database = QuickAsideDatabase.create(context, databaseName)
        val reopenedStore = RoomListStore(checkNotNull(database))
        val restoredItems = reopenedStore.readCurrentItems(BuiltInListDefinitions.COMPRAS.id)

        assertEquals(listOf(firstItem.id, secondItem.id), restoredItems.map { it.id })
        assertEquals(
            listOf("  Cuerdas guitarra  ", "Leche"),
            restoredItems.map { it.text },
        )
        assertEquals(listOf(null, null), restoredItems.map { it.listSessionId })
        assertTrue(restoredItems.first { it.id == firstItem.id }.isCompleted)
        assertFalse(restoredItems.first { it.id == secondItem.id }.isCompleted)
        assertNotNull(reopenedStore.readCurrentItems(BuiltInListDefinitions.COMPRAS.id))
        assertTrue(
            database?.listSessionDao()
                ?.getByDefinitionId(BuiltInListDefinitions.COMPRAS.id.value)
                ?.isEmpty() == true,
        )
    }
}

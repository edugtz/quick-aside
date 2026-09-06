package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.memory.MemoryClock
import com.edu.quickaside.application.memory.MemoryIdProvider
import com.edu.quickaside.application.memory.NoteCreationResult
import com.edu.quickaside.application.memory.RandomMemoryIdProvider
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesMemoryStoreIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-014-notes-boundary-${UUID.randomUUID()}.db"
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
    fun productionMemoryStoreNotesSurviveCreateReadCloseAndReopen() = runBlocking {
        openDatabase()
        val createdAt = Instant.parse("2026-09-06T12:00:00Z")
        val store = RoomMemoryStore(
            database = database,
            idProvider = FixedMemoryIdProvider("note-a", "note-b"),
            clock = MemoryClock { createdAt },
        )

        val savedA = store.createNote("  Llamar al taller  ") as NoteCreationResult.Saved
        val savedB = store.createNote("  Comprar focos  ") as NoteCreationResult.Saved

        assertEquals(listOf(savedB.note, savedA.note), store.readRecentNotes())
        assertEquals("note-a", savedA.note.id.value)
        assertEquals("  Llamar al taller  ", savedA.note.text)
        assertEquals(createdAt, savedA.note.createdAt)
        assertNull(savedA.note.sourceCaptureId)
        assertEquals("note-b", savedB.note.id.value)
        assertEquals("  Comprar focos  ", savedB.note.text)
        assertEquals(createdAt, savedB.note.createdAt)
        assertNull(savedB.note.sourceCaptureId)

        database.close()
        openDatabase()

        assertEquals(
            listOf(savedB.note, savedA.note),
            RoomMemoryStore(database).readRecentNotes(),
        )
    }

    private fun openDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private class FixedMemoryIdProvider(
        vararg noteIds: String,
    ) : MemoryIdProvider {
        private val noteIds = ArrayDeque(noteIds.toList())
        private val fallback = RandomMemoryIdProvider()

        override fun nextNoteId(): NoteId = NoteId(noteIds.removeFirst())

        override fun nextStructuredLogId(): StructuredLogId = fallback.nextStructuredLogId()
    }
}

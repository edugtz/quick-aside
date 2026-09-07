package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.memory.MemoryClock
import com.edu.quickaside.application.memory.MemoryIdProvider
import com.edu.quickaside.application.memory.RandomMemoryIdProvider
import com.edu.quickaside.application.memory.StructuredLogCreationResult
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
class StructuredLogMemoryStoreIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "change-015-structured-logs-${UUID.randomUUID()}.db"
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
    fun productionMemoryStoreStructuredLogsSurviveCreateReadCloseAndReopen() = runBlocking {
        openDatabase()
        val createdAt = Instant.parse("2026-09-06T12:00:00Z")
        val store = RoomMemoryStore(
            database = database,
            idProvider = FixedMemoryIdProvider("log-a", "log-b"),
            clock = MemoryClock { createdAt },
        )
        val fieldsA = linkedMapOf(
            "weight" to " 210 lbs ",
            "exercise" to "press inclinado",
        )
        val fieldsB = linkedMapOf(
            "place" to "gimnasio",
            "duration" to "45 min",
        )

        val savedA = store.createStructuredLog(fieldsA) as StructuredLogCreationResult.Saved
        val savedB = store.createStructuredLog(fieldsB) as StructuredLogCreationResult.Saved

        assertEquals(listOf(savedB.log, savedA.log), store.readRecentStructuredLogs())
        assertEquals("log-a", savedA.log.id.value)
        assertEquals(fieldsA, savedA.log.fields)
        assertEquals(createdAt, savedA.log.createdAt)
        assertNull(savedA.log.sourceCaptureId)
        assertEquals("log-b", savedB.log.id.value)
        assertEquals(fieldsB, savedB.log.fields)
        assertEquals(createdAt, savedB.log.createdAt)
        assertNull(savedB.log.sourceCaptureId)
        assertEquals(
            listOf("exercise", "weight"),
            database.structuredLogFieldDao()
                .getByStructuredLogId("log-a")
                .map(StructuredLogFieldEntity::fieldKey),
        )

        database.close()
        openDatabase()

        assertEquals(
            listOf(savedB.log, savedA.log),
            RoomMemoryStore(database).readRecentStructuredLogs(),
        )
        assertEquals(savedA.log, RoomMemoryStore(database).getStructuredLog(savedA.log.id))
        assertEquals(savedB.log, RoomMemoryStore(database).getStructuredLog(savedB.log.id))
    }

    private fun openDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private class FixedMemoryIdProvider(
        vararg structuredLogIds: String,
    ) : MemoryIdProvider {
        private val structuredLogIds = ArrayDeque(structuredLogIds.toList())
        private val fallback = RandomMemoryIdProvider()

        override fun nextNoteId(): NoteId = fallback.nextNoteId()

        override fun nextStructuredLogId(): StructuredLogId =
            StructuredLogId(structuredLogIds.removeFirst())
    }
}

package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureHistoryDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "capture-history-test-${UUID.randomUUID()}.db"
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun emptyDatabaseReturnsNoRecentCaptures() = runBlocking {
        assertTrue(RoomCaptureReader(database).readRecent().isEmpty())
    }

    @Test
    fun recentCapturesAreNewestFirstAndEqualTimestampsUseIdTieBreak() = runBlocking {
        val sameTimestamp = Instant.parse("2026-09-03T12:00:00Z")
        val captures = listOf(
            capture("older", Instant.parse("2026-09-02T12:00:00Z")),
            capture("tie-a", sameTimestamp),
            capture("newest", Instant.parse("2026-09-04T12:00:00Z")),
            capture("tie-b", sameTimestamp),
        )
        captures.forEach { database.captureDao().insert(it.toEntity()) }

        val recent = RoomCaptureReader(database).readRecent()

        assertEquals(
            listOf("newest", "tie-b", "tie-a", "older"),
            recent.map { it.id.value },
        )
        assertEquals("texto más nuevo", (recent.first().originalInput as CaptureInput.Text).originalText)
    }

    @Test
    fun existingVoiceCaptureIsReadAsVoiceWithOriginalTranscript() = runBlocking {
        val voice = capture(
            id = "voice-history",
            originalInput = CaptureInput.Voice("Mañana revisa el PR"),
        )
        database.captureDao().insert(voice.toEntity())

        val restored = RoomCaptureReader(database).readRecent().single()

        assertEquals(voice, restored)
        assertTrue(restored.originalInput is CaptureInput.Voice)
    }

    @Test
    fun recentReadIsBoundedToFiftyCaptures() = runBlocking {
        val capturedAt = Instant.parse("2026-09-03T12:00:00Z")
        (0..50).forEach { index ->
            database.captureDao().insert(
                capture(
                    id = "capture-${index.toString().padStart(2, '0')}",
                    capturedAt = capturedAt,
                ).toEntity(),
            )
        }

        val recent = RoomCaptureReader(database).readRecent()

        assertEquals(50, recent.size)
        assertEquals("capture-50", recent.first().id.value)
        assertEquals("capture-01", recent.last().id.value)
    }

    @Test
    fun captureRemainsReadableThroughReaderAfterDatabaseReopen() = runBlocking {
        val capture = capture(
            id = "reopen-history",
            originalInput = CaptureInput.Text("Visible after relaunch"),
        )
        database.captureDao().insert(capture.toEntity())
        database.close()

        database = QuickAsideDatabase.create(context, databaseName)

        assertEquals(listOf(capture), RoomCaptureReader(database).readRecent())
    }

    private fun capture(
        id: String,
        capturedAt: Instant = Instant.parse("2026-09-03T12:00:00Z"),
        originalInput: CaptureInput = CaptureInput.Text("texto más nuevo"),
    ): Capture = Capture(
        id = CaptureId(id),
        originalInput = originalInput,
        capturedAt = capturedAt,
    )
}

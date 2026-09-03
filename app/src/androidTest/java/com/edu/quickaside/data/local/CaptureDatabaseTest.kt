package com.edu.quickaside.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "capture-persistence-test.db"
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = QuickAsideDatabase.create(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun textCaptureRoundTripsThroughRoom() = runBlocking {
        val capture = Capture(
            id = CaptureId("database-text"),
            originalInput = CaptureInput.Text("Comprar leche"),
            capturedAt = Instant.parse("2026-09-03T12:34:56.789Z"),
        )

        database.captureDao().insert(capture.toEntity())

        assertEquals(capture, database.captureDao().getById(capture.id.value)?.toDomain())
    }

    @Test
    fun voiceCaptureRoundTripsThroughRoom() = runBlocking {
        val capture = Capture(
            id = CaptureId("database-voice"),
            originalInput = CaptureInput.Voice("Mañana revisa el PR"),
            capturedAt = Instant.parse("2026-09-03T12:34:56.789Z"),
        )

        database.captureDao().insert(capture.toEntity())

        assertEquals(capture, database.captureDao().getById(capture.id.value)?.toDomain())
    }

    @Test
    fun duplicateCaptureIdDoesNotOverwriteExistingCapture() = runBlocking {
        val original = Capture(
            id = CaptureId("database-duplicate"),
            originalInput = CaptureInput.Text("Texto original"),
            capturedAt = Instant.parse("2026-09-03T12:34:56Z"),
        )
        val duplicate = original.copy(
            originalInput = CaptureInput.Voice("Intento de reemplazo"),
            capturedAt = Instant.parse("2026-09-03T13:34:56Z"),
        )

        database.captureDao().insert(original.toEntity())
        val failure = runCatching { database.captureDao().insert(duplicate.toEntity()) }
            .exceptionOrNull()

        assertNotNull("Duplicate Capture ID must fail", failure)
        assertEquals(original, database.captureDao().getById(original.id.value)?.toDomain())
    }

    @Test
    fun captureSurvivesCloseAndReopenOfTheSameNamedDatabase() = runBlocking {
        val capture = Capture(
            id = CaptureId("database-reopen"),
            originalInput = CaptureInput.Voice("Persistir después de cerrar"),
            capturedAt = Instant.parse("2026-09-03T14:00:00Z"),
        )
        database.captureDao().insert(capture.toEntity())
        database.close()

        database = QuickAsideDatabase.create(context, databaseName)

        assertEquals(capture, database.captureDao().getById(capture.id.value)?.toDomain())
        assertNull(database.captureDao().getById("missing"))
    }
}

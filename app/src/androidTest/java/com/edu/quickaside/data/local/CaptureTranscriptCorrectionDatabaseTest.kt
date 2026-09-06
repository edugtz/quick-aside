package com.edu.quickaside.data.local

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureSubmissionResult
import com.edu.quickaside.application.capture.CaptureTranscriptCorrectionResult
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
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
class CaptureTranscriptCorrectionDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: QuickAsideDatabase

    @Before
    fun setUp() {
        databaseName = "capture-transcript-correction-${UUID.randomUUID()}.db"
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
    fun newV2VoiceCaptureStoresOriginalTranscriptAndNullCorrection() = runBlocking {
        openFreshDatabase()
        val captureId = CaptureId("new-v2-voice")
        val transcript = "  Comprar leche mañana  "
        val capturedAt = Instant.parse("2026-09-03T16:00:00Z")
        val result = CaptureSubmission(
            writer = RoomCaptureWriter(database),
            idProvider = { captureId },
            capturedAtProvider = { capturedAt },
        ).submitVoice(transcript)

        val saved = result as CaptureSubmissionResult.Saved
        val entity = database.captureDao().getById(captureId.value)

        assertEquals(transcript, (saved.capture.originalInput as CaptureInput.Voice).originalTranscript)
        assertNull(saved.capture.transcriptCorrection)
        assertEquals(transcript, saved.capture.effectiveTranscript)
        assertNotNull(entity)
        assertEquals(transcript, entity?.originalText)
        assertNull(entity?.correctedTranscript)
    }

    @Test
    fun realV1TextAndVoiceRowsMigrateWithoutChangingAnyExistingValue() = runBlocking {
        val rows = listOf(
            V1Row(
                id = "legacy-text",
                kind = "TEXT",
                originalText = "  Texto legado  ",
                capturedAtEpochMillis = 1788438896789,
            ),
            V1Row(
                id = "legacy-voice",
                kind = "VOICE",
                originalText = "comprar leche manana",
                capturedAtEpochMillis = 1788438900123,
            ),
        )
        createVersion1Fixture(rows)

        openProductionDatabase()

        rows.forEach { row -> assertMigratedRow(row) }
        assertEquals(2, database.captureDao().getRecent(50).size)

        database.close()
        assertEquals(4L, readUserVersion())
        assertEquals(
            listOf(
                "id",
                "kind",
                "original_text",
                "captured_at_epoch_millis",
                "corrected_transcript",
            ),
            readCaptureColumns().map { it.name },
        )
        assertFalse(readCaptureColumns().last().notNull)

        openProductionDatabase()
        rows.forEach { row -> assertMigratedRow(row) }
    }

    @Test
    fun correctionReturnsOriginalAndCorrectedValuesAndUsesCorrectionEffectively() = runBlocking {
        openFreshDatabase()
        val original = voiceCapture(
            id = "correct-me",
            originalTranscript = "comprar leche manana",
        )
        database.captureDao().insert(original.toEntity())

        val result = corrector().correct(
            captureId = original.id,
            correctedTranscript = "Comprar leche mañana",
        )

        val saved = result as CaptureTranscriptCorrectionResult.Saved
        assertEquals(original.id, saved.capture.id)
        assertEquals(original.originalInput, saved.capture.originalInput)
        assertEquals(original.capturedAt, saved.capture.capturedAt)
        assertEquals("Comprar leche mañana", saved.capture.transcriptCorrection)
        assertEquals("comprar leche manana", (saved.capture.originalInput as CaptureInput.Voice).originalTranscript)
        assertEquals("Comprar leche mañana", saved.capture.effectiveTranscript)

        val entity = database.captureDao().getById(original.id.value)
        assertEquals(original.id.value, entity?.id)
        assertEquals("VOICE", entity?.kind)
        assertEquals("comprar leche manana", entity?.originalText)
        assertEquals(original.capturedAt.toEpochMilli(), entity?.capturedAtEpochMillis)
        assertEquals("Comprar leche mañana", entity?.correctedTranscript)
    }

    @Test
    fun secondCorrectionChangesOnlyCorrectionField() = runBlocking {
        openFreshDatabase()
        val original = voiceCapture(
            id = "correct-twice",
            originalTranscript = "original transcript",
        )
        database.captureDao().insert(original.toEntity())
        val before = checkNotNull(database.captureDao().getById(original.id.value))

        assertTrue(
            corrector().correct(original.id, "Primera corrección")
                is CaptureTranscriptCorrectionResult.Saved,
        )
        assertTrue(
            corrector().correct(original.id, "Segunda corrección")
                is CaptureTranscriptCorrectionResult.Saved,
        )

        val after = checkNotNull(database.captureDao().getById(original.id.value))
        assertEquals(before.id, after.id)
        assertEquals(before.kind, after.kind)
        assertEquals(before.originalText, after.originalText)
        assertEquals(before.capturedAtEpochMillis, after.capturedAtEpochMillis)
        assertEquals("Segunda corrección", after.correctedTranscript)
    }

    @Test
    fun blankCorrectionIsRejectedAndDoesNotChangeTheRow() = runBlocking {
        openFreshDatabase()
        val original = voiceCapture("blank-rejected", "Original")
        database.captureDao().insert(original.toEntity())
        val before = checkNotNull(database.captureDao().getById(original.id.value))

        val result = corrector().correct(original.id, " \t\n ")

        assertEquals(CaptureTranscriptCorrectionResult.Blank, result)
        assertEquals(before, database.captureDao().getById(original.id.value))
    }

    @Test
    fun textCaptureCorrectionIsRejectedAndDoesNotChangeTheRow() = runBlocking {
        openFreshDatabase()
        val original = Capture(
            id = CaptureId("text-rejected"),
            originalInput = CaptureInput.Text("Texto original"),
            capturedAt = FIXED_CAPTURE_TIME,
        )
        database.captureDao().insert(original.toEntity())
        val before = checkNotNull(database.captureDao().getById(original.id.value))

        val result = corrector().correct(original.id, "No debe aplicar")

        assertEquals(CaptureTranscriptCorrectionResult.NotVoice, result)
        assertEquals(before, database.captureDao().getById(original.id.value))
    }

    @Test
    fun missingCaptureIsNotReportedAsSuccessfulCorrection() = runBlocking {
        openFreshDatabase()

        val result = corrector().correct(CaptureId("missing"), "Corrección")

        assertEquals(CaptureTranscriptCorrectionResult.Missing, result)
        assertFalse(result is CaptureTranscriptCorrectionResult.Saved)
    }

    @Test
    fun closedDatabasePersistenceFailureIsReturnedAsFailure() = runBlocking {
        openFreshDatabase()
        val capture = voiceCapture("closed-database", "Original")
        database.captureDao().insert(capture.toEntity())
        val correctionBoundary = corrector()
        database.close()

        val result = correctionBoundary.correct(capture.id, "Corrección")

        assertTrue(result is CaptureTranscriptCorrectionResult.Failed)
        assertFalse(result is CaptureTranscriptCorrectionResult.Saved)
    }

    @Test
    fun textAndVoiceSubmissionStillWorkAfterV1Migration() = runBlocking {
        createVersion1Fixture(
            listOf(
                V1Row("legacy-before-submit", "TEXT", "Texto anterior", 1788438896789),
                V1Row("legacy-voice-before-submit", "VOICE", "Voz anterior", 1788438900123),
            ),
        )
        openProductionDatabase()
        val submission = CaptureSubmission(
            writer = RoomCaptureWriter(database),
            idProvider = object : () -> CaptureId {
                private var nextId = 0

                override fun invoke(): CaptureId {
                    nextId += 1
                    return CaptureId("post-migration-$nextId")
                }
            },
            capturedAtProvider = { FIXED_CAPTURE_TIME },
        )

        val textResult = submission.submit("Texto después de migrar")
        val voiceResult = submission.submitVoice("Voz después de migrar")

        assertTrue(textResult is CaptureSubmissionResult.Saved)
        assertTrue(voiceResult is CaptureSubmissionResult.Saved)
        assertNull(database.captureDao().getById("post-migration-1")?.correctedTranscript)
        assertNull(database.captureDao().getById("post-migration-2")?.correctedTranscript)
        assertEquals(4, database.captureDao().getRecent(50).size)
    }

    private fun openFreshDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun openProductionDatabase() {
        database = QuickAsideDatabase.create(context, databaseName)
    }

    private fun corrector(): RoomCaptureTranscriptCorrector =
        RoomCaptureTranscriptCorrector(database.captureDao())

    private fun voiceCapture(id: String, originalTranscript: String): Capture = Capture(
        id = CaptureId(id),
        originalInput = CaptureInput.Voice(originalTranscript),
        capturedAt = FIXED_CAPTURE_TIME,
    )

    private suspend fun assertMigratedRow(row: V1Row) {
        val entity = database.captureDao().getById(row.id)
        assertNotNull("Migrated row ${row.id} must exist", entity)
        assertEquals(row.id, entity?.id)
        assertEquals(row.kind, entity?.kind)
        assertEquals(row.originalText, entity?.originalText)
        assertEquals(row.capturedAtEpochMillis, entity?.capturedAtEpochMillis)
        assertNull(entity?.correctedTranscript)
        assertEquals(row.kind, entity?.toDomain()?.kind?.name)
        assertEquals(row.originalText, entity?.toDomain()?.originalTextValue())
    }

    private fun Capture.originalTextValue(): String = when (val input = originalInput) {
        is CaptureInput.Text -> input.originalText
        is CaptureInput.Voice -> input.originalTranscript
    }

    private fun createVersion1Fixture(rows: List<V1Row>) {
        val path = context.getDatabasePath(databaseName).apply {
            parentFile?.mkdirs()
        }.absolutePath
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execute(
                "CREATE TABLE IF NOT EXISTS captures " +
                    "(id TEXT NOT NULL, kind TEXT NOT NULL, original_text TEXT NOT NULL, " +
                    "captured_at_epoch_millis INTEGER NOT NULL, PRIMARY KEY(id))",
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY,identity_hash TEXT)",
            )
            connection.execute(
                "INSERT OR REPLACE INTO room_master_table " +
                    "(id,identity_hash) VALUES(42, '$V1_IDENTITY_HASH')",
            )
            connection.execute("PRAGMA user_version = 1")
            rows.forEach { row -> connection.insertV1Row(row) }
        }
    }

    private fun SQLiteConnection.insertV1Row(row: V1Row) {
        prepare(
            "INSERT INTO captures " +
                "(id, kind, original_text, captured_at_epoch_millis) VALUES (?, ?, ?, ?)",
        ).use { statement ->
            statement.bindText(1, row.id)
            statement.bindText(2, row.kind)
            statement.bindText(3, row.originalText)
            statement.bindLong(4, row.capturedAtEpochMillis)
            statement.step()
        }
    }

    private fun readUserVersion(): Long {
        val path = context.getDatabasePath(databaseName).absolutePath
        return BundledSQLiteDriver().open(path).use { connection ->
            connection.prepare("PRAGMA user_version").use { statement ->
                assertTrue(statement.step())
                statement.getLong(0)
            }
        }
    }

    private fun readCaptureColumns(): List<ColumnInfo> {
        val path = context.getDatabasePath(databaseName).absolutePath
        return BundledSQLiteDriver().open(path).use { connection ->
            connection.prepare("PRAGMA table_info(captures)").use { statement ->
                buildList {
                    while (statement.step()) {
                        add(
                            ColumnInfo(
                                name = statement.getText(1),
                                notNull = statement.getLong(3) != 0L,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private data class V1Row(
        val id: String,
        val kind: String,
        val originalText: String,
        val capturedAtEpochMillis: Long,
    )

    private data class ColumnInfo(
        val name: String,
        val notNull: Boolean,
    )

    private companion object {
        const val V1_IDENTITY_HASH = "d1f59529c9d2bf7f20168757fa29fbb0"
        val FIXED_CAPTURE_TIME = Instant.parse("2026-09-03T16:00:00Z")
    }
}

package com.edu.quickaside.data.local

import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MemoryEntityMappingTest {
    private val createdAt = Instant.parse("2026-09-05T12:34:56.789Z")

    @Test
    fun noteRoundTripsExactTextSourceAndTimestamp() {
        val note = Note(
            id = NoteId("note-1"),
            text = "  Llamar al taller  ",
            sourceCaptureId = CaptureId("capture-1"),
            createdAt = createdAt,
        )

        assertEquals(note, note.toEntity().toDomain())
        assertEquals("  Llamar al taller  ", note.toEntity().text)
        assertEquals(createdAt.toEpochMilli(), note.toEntity().createdAtEpochMillis)
    }

    @Test
    fun structuredLogRoundTripsExactFieldsSourceAndTimestamp() {
        val log = StructuredLog(
            id = StructuredLogId("log-1"),
            fields = linkedMapOf(
                "exercise" to "press inclinado",
                "weight" to "210 lbs",
            ),
            sourceCaptureId = CaptureId("capture-1"),
            createdAt = createdAt,
        )

        val restored = log.toEntity().toDomain(log.toFieldEntities())

        assertEquals(log, restored)
        assertEquals(
            listOf("exercise", "weight"),
            log.toFieldEntities().map(StructuredLogFieldEntity::fieldKey),
        )
        assertEquals("press inclinado", restored.fields["exercise"])
        assertEquals("210 lbs", restored.fields["weight"])
    }

    @Test
    fun blankNoteTextIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Note(
                id = NoteId("blank-note"),
                text = " \t\n ",
                createdAt = createdAt,
            )
        }
    }

    @Test
    fun emptyStructuredLogIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StructuredLog(
                id = StructuredLogId("empty-log"),
                createdAt = createdAt,
            )
        }
    }

    @Test
    fun blankStructuredLogFieldKeyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StructuredLog(
                id = StructuredLogId("blank-key"),
                fields = mapOf(" \t" to "value"),
                createdAt = createdAt,
            )
        }
    }

    @Test
    fun blankStructuredLogFieldValueIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StructuredLog(
                id = StructuredLogId("blank-value"),
                fields = mapOf("key" to " \t"),
                createdAt = createdAt,
            )
        }
    }

    @Test
    fun mismatchedStructuredLogFieldParentFailsVisibly() {
        assertThrows(IllegalStateException::class.java) {
            StructuredLogEntity(
                id = "log-1",
                createdAtEpochMillis = createdAt.toEpochMilli(),
            ).toDomain(
                listOf(
                    StructuredLogFieldEntity(
                        structuredLogId = "different-log",
                        fieldKey = "key",
                        fieldValue = "value",
                    ),
                ),
            )
        }
    }
}

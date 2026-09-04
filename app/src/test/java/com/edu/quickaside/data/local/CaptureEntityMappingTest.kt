package com.edu.quickaside.data.local

import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CaptureKind
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureEntityMappingTest {
    @Test
    fun textCaptureRoundTripsThroughPersistenceRepresentation() {
        val capture = Capture(
            id = CaptureId("capture-text"),
            originalInput = CaptureInput.Text("Comprar leche"),
            capturedAt = Instant.parse("2026-09-03T12:34:56.789Z"),
        )

        val restored = capture.toEntity().toDomain()

        assertEquals(capture, restored)
        assertEquals(CaptureKind.TEXT, restored.kind)
    }

    @Test
    fun voiceCaptureRoundTripsThroughPersistenceRepresentation() {
        val capture = Capture(
            id = CaptureId("capture-voice"),
            originalInput = CaptureInput.Voice("Mañana revisa el PR"),
            capturedAt = Instant.parse("2026-09-03T12:34:56.789Z"),
        )

        val restored = capture.toEntity().toDomain()

        assertEquals(capture, restored)
        assertEquals(CaptureKind.VOICE, restored.kind)
    }

    @Test
    fun correctedVoiceCaptureRoundTripsWithOriginalAndCorrection() {
        val capture = Capture(
            id = CaptureId("capture-corrected-voice"),
            originalInput = CaptureInput.Voice("comprar leche manana"),
            capturedAt = Instant.parse("2026-09-03T12:34:56.789Z"),
            transcriptCorrection = "Comprar leche mañana",
        )

        val entity = capture.toEntity()
        val restored = entity.toDomain()

        assertEquals("comprar leche manana", entity.originalText)
        assertEquals("Comprar leche mañana", entity.correctedTranscript)
        assertEquals(capture, restored)
        assertEquals("comprar leche manana", (restored.originalInput as CaptureInput.Voice).originalTranscript)
        assertEquals("Comprar leche mañana", restored.effectiveTranscript)
    }

    @Test
    fun persistenceRepresentationUsesDeterministicStorageValues() {
        val capture = Capture(
            id = CaptureId("capture-deterministic"),
            originalInput = CaptureInput.Voice("Revisar integración"),
            capturedAt = Instant.parse("2026-09-03T12:34:56.789Z"),
        )

        assertEquals(
            CaptureEntity(
                id = "capture-deterministic",
                kind = "VOICE",
                originalText = "Revisar integración",
                capturedAtEpochMillis = 1788438896789,
            ),
            capture.toEntity(),
        )
    }
}

package com.edu.quickaside.domain.capture

import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTranscriptCorrectionDomainTest {
    private val capturedAt = Instant.parse("2026-09-03T16:00:00Z")

    @Test
    fun voiceCaptureExposesOriginalCorrectionAndEffectiveTranscriptSeparately() {
        val capture = Capture(
            id = CaptureId("voice-correction"),
            originalInput = CaptureInput.Voice("comprar leche manana"),
            capturedAt = capturedAt,
            transcriptCorrection = "Comprar leche mañana",
        )

        assertEquals("comprar leche manana", (capture.originalInput as CaptureInput.Voice).originalTranscript)
        assertEquals("Comprar leche mañana", capture.transcriptCorrection)
        assertEquals("Comprar leche mañana", capture.effectiveTranscript)
    }

    @Test
    fun uncorrectedVoiceUsesOriginalTranscriptAsEffectiveTranscript() {
        val capture = Capture(
            id = CaptureId("voice-without-correction"),
            originalInput = CaptureInput.Voice("Comprar leche"),
            capturedAt = capturedAt,
        )

        assertNull(capture.transcriptCorrection)
        assertEquals("Comprar leche", capture.effectiveTranscript)
    }

    @Test
    fun textCaptureHasNoEffectiveTranscriptCorrectionSurface() {
        val capture = Capture(
            id = CaptureId("text-without-correction"),
            originalInput = CaptureInput.Text("Comprar leche"),
            capturedAt = capturedAt,
        )

        assertNull(capture.transcriptCorrection)
        assertNull(capture.effectiveTranscript)
    }

    @Test
    fun blankCorrectionIsRejected() {
        val failure = runCatching {
            Capture(
                id = CaptureId("blank-correction"),
                originalInput = CaptureInput.Voice("Original"),
                capturedAt = capturedAt,
                transcriptCorrection = " \t\n ",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun textCorrectionIsRejected() {
        val failure = runCatching {
            Capture(
                id = CaptureId("text-correction"),
                originalInput = CaptureInput.Text("Original"),
                capturedAt = capturedAt,
                transcriptCorrection = "Corrección",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}

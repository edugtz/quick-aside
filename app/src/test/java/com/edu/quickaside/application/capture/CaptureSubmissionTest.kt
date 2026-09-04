package com.edu.quickaside.application.capture

import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CaptureKind
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSubmissionTest {
    private val capturedAt = Instant.parse("2026-09-03T16:00:00Z")

    @Test
    fun validTextSubmissionCreatesOneTextCaptureAndPreservesOriginalText() = runBlocking {
        val savedCaptures = mutableListOf<Capture>()
        val submission = CaptureSubmission(
            writer = CaptureWriter { capture -> savedCaptures += capture },
            idProvider = { CaptureId("capture-text-1") },
            capturedAtProvider = { capturedAt },
        )

        val result = submission.submit("  Compra pollo  ") as CaptureSubmissionResult.Saved

        assertEquals(1, savedCaptures.size)
        assertEquals(
            Capture(
                id = CaptureId("capture-text-1"),
                originalInput = CaptureInput.Text("  Compra pollo  "),
                capturedAt = capturedAt,
            ),
            savedCaptures.single(),
        )
        assertEquals(savedCaptures.single(), result.capture)
        assertEquals(CaptureKind.TEXT, savedCaptures.single().kind)
    }

    @Test
    fun validVoiceSubmissionCreatesOneVoiceCaptureAndPreservesOriginalTranscript() = runBlocking {
        val savedCaptures = mutableListOf<Capture>()
        val submission = CaptureSubmission(
            writer = CaptureWriter { capture -> savedCaptures += capture },
            idProvider = { CaptureId("capture-voice-1") },
            capturedAtProvider = { capturedAt },
        )

        val transcript = "  Comprar leche mañana  "
        val result = submission.submitVoice(transcript) as CaptureSubmissionResult.Saved

        assertEquals(1, savedCaptures.size)
        assertEquals(
            Capture(
                id = CaptureId("capture-voice-1"),
                originalInput = CaptureInput.Voice(transcript),
                capturedAt = capturedAt,
            ),
            savedCaptures.single(),
        )
        assertEquals(savedCaptures.single(), result.capture)
        assertEquals(CaptureKind.VOICE, savedCaptures.single().kind)
    }

    @Test
    fun blankVoiceSubmissionCreatesNothing() = runBlocking {
        val savedCaptures = mutableListOf<Capture>()
        val submission = CaptureSubmission(
            writer = CaptureWriter { capture -> savedCaptures += capture },
            idProvider = { CaptureId("should-not-be-created") },
            capturedAtProvider = { capturedAt },
        )

        assertTrue(submission.submitVoice(" \t\n ") is CaptureSubmissionResult.Blank)

        assertTrue(savedCaptures.isEmpty())
    }

    @Test
    fun blankAndWhitespaceOnlySubmissionsCreateNothing() = runBlocking {
        val savedCaptures = mutableListOf<Capture>()
        val submission = CaptureSubmission(
            writer = CaptureWriter { capture -> savedCaptures += capture },
            idProvider = { CaptureId("should-not-be-created") },
            capturedAtProvider = { capturedAt },
        )

        assertTrue(submission.submit("") is CaptureSubmissionResult.Blank)
        assertTrue(submission.submit(" \t\n ") is CaptureSubmissionResult.Blank)

        assertTrue(savedCaptures.isEmpty())
    }

    @Test
    fun successfulSubmissionsUseDistinctIds() = runBlocking {
        val savedCaptures = mutableListOf<Capture>()
        val submission = CaptureSubmission(
            writer = CaptureWriter { capture -> savedCaptures += capture },
            capturedAtProvider = { capturedAt },
        )

        submission.submit("Primera captura")
        submission.submit("Segunda captura")

        assertEquals(2, savedCaptures.size)
        assertNotEquals(savedCaptures[0].id, savedCaptures[1].id)
    }

    @Test
    fun persistenceFailureReturnsFailureWithoutReplacingItWithSuccess() = runBlocking {
        val submission = CaptureSubmission(
            writer = CaptureWriter { throw IllegalStateException("database unavailable") },
            idProvider = { CaptureId("capture-failure") },
            capturedAtProvider = { capturedAt },
        )

        val result = submission.submit("No borrar este texto")

        assertTrue(result is CaptureSubmissionResult.Failed)
        assertEquals("database unavailable", (result as CaptureSubmissionResult.Failed).cause.message)
    }
}

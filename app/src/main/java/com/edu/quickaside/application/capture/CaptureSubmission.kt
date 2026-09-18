package com.edu.quickaside.application.capture

import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException

sealed interface CaptureSubmissionResult {
    data object Blank : CaptureSubmissionResult

    data class Saved(
        val capture: Capture,
        val interpretation: CaptureInterpretationResult? = null,
    ) : CaptureSubmissionResult

    data class Failed(
        val cause: Exception,
    ) : CaptureSubmissionResult
}

class CaptureSubmission(
    private val writer: CaptureWriter,
    private val interpreter: CaptureInterpreter? = null,
    private val idProvider: () -> CaptureId = {
        CaptureId(UUID.randomUUID().toString())
    },
    private val capturedAtProvider: () -> Instant = Instant::now,
) {
    suspend fun submit(originalText: String): CaptureSubmissionResult = submitInput(
        input = CaptureInput.Text(originalText),
    )

    suspend fun submitVoice(originalTranscript: String): CaptureSubmissionResult = submitInput(
        input = CaptureInput.Voice(originalTranscript),
    )

    private suspend fun submitInput(input: CaptureInput): CaptureSubmissionResult {
        val originalText = when (input) {
            is CaptureInput.Text -> input.originalText
            is CaptureInput.Voice -> input.originalTranscript
        }
        if (originalText.isBlank()) {
            return CaptureSubmissionResult.Blank
        }

        val capture = Capture(
            id = idProvider(),
            originalInput = input,
            capturedAt = capturedAtProvider(),
        )

        try {
            writer.save(capture)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return CaptureSubmissionResult.Failed(failure)
        }

        // Persistence is complete before any remote interpretation can begin.
        val interpretation = interpreter?.interpret(capture)
        return CaptureSubmissionResult.Saved(
            capture = capture,
            interpretation = interpretation,
        )
    }
}

package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.common.CaptureId

fun interface CaptureTranscriptCorrector {
    suspend fun correct(
        captureId: CaptureId,
        correctedTranscript: String,
    ): CaptureTranscriptCorrectionResult
}

sealed interface CaptureTranscriptCorrectionResult {
    data class Saved(
        val capture: Capture,
    ) : CaptureTranscriptCorrectionResult

    data object Missing : CaptureTranscriptCorrectionResult

    data object NotVoice : CaptureTranscriptCorrectionResult

    data object Blank : CaptureTranscriptCorrectionResult

    data class Failed(
        val cause: Exception,
    ) : CaptureTranscriptCorrectionResult
}

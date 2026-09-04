package com.edu.quickaside.domain.capture

import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant

enum class CaptureKind {
    TEXT,
    VOICE,
}

sealed interface CaptureInput {
    val kind: CaptureKind

    data class Text(
        val originalText: String,
    ) : CaptureInput {
        override val kind: CaptureKind = CaptureKind.TEXT
    }

    data class Voice(
        val originalTranscript: String,
    ) : CaptureInput {
        override val kind: CaptureKind = CaptureKind.VOICE
    }
}

data class Capture(
    val id: CaptureId,
    val originalInput: CaptureInput,
    val capturedAt: Instant,
    val transcriptCorrection: String? = null,
) {
    init {
        require(transcriptCorrection == null || transcriptCorrection.isNotBlank()) {
            "Transcript correction must not be blank"
        }
        require(transcriptCorrection == null || originalInput is CaptureInput.Voice) {
            "Transcript correction applies only to Voice captures"
        }
    }

    val kind: CaptureKind
        get() = originalInput.kind

    val effectiveTranscript: String?
        get() = when (val input = originalInput) {
            is CaptureInput.Text -> null
            is CaptureInput.Voice -> transcriptCorrection ?: input.originalTranscript
        }
}

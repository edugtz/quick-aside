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
) {
    val kind: CaptureKind
        get() = originalInput.kind
}


package com.edu.quickaside.data.local

import com.edu.quickaside.application.capture.CaptureTranscriptCorrectionResult
import com.edu.quickaside.application.capture.CaptureTranscriptCorrector
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import kotlinx.coroutines.CancellationException

class RoomCaptureTranscriptCorrector(
    private val captureDao: CaptureDao,
) : CaptureTranscriptCorrector {
    override suspend fun correct(
        captureId: CaptureId,
        correctedTranscript: String,
    ): CaptureTranscriptCorrectionResult {
        if (correctedTranscript.isBlank()) {
            return CaptureTranscriptCorrectionResult.Blank
        }

        return try {
            val existingEntity = captureDao.getById(captureId.value)
                ?: return CaptureTranscriptCorrectionResult.Missing
            val existingCapture = existingEntity.toDomain()
            if (existingCapture.originalInput !is CaptureInput.Voice) {
                return CaptureTranscriptCorrectionResult.NotVoice
            }

            check(captureDao.updateCorrectedTranscript(captureId.value, correctedTranscript) == 1) {
                "Expected exactly one Voice Capture correction update"
            }

            val correctedEntity = captureDao.getById(captureId.value)
                ?: error("Corrected Voice Capture disappeared after update")
            CaptureTranscriptCorrectionResult.Saved(correctedEntity.toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            CaptureTranscriptCorrectionResult.Failed(failure)
        }
    }
}

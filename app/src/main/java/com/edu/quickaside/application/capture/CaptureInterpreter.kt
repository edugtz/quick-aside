package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanDraft
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

fun interface CaptureInterpreter {
    suspend fun interpret(capture: Capture): CaptureInterpretationResult
}

sealed interface CaptureInterpretationResult {
    data class Success(
        val plan: CapturePlan,
    ) : CaptureInterpretationResult

    data object BlankInput : CaptureInterpretationResult

    data class InvalidPlan(
        val issues: List<CapturePlanValidationIssue>,
    ) : CaptureInterpretationResult {
        init {
            require(issues.isNotEmpty()) {
                "Invalid Capture interpretation result must contain an issue"
            }
        }
    }

    data class ProviderFailure(
        val cause: Exception,
    ) : CaptureInterpretationResult
}

class ProviderCaptureInterpreter(
    private val provider: AIProvider,
    private val validator: CapturePlanValidator,
    private val timeZoneIdProvider: () -> String = { ZoneId.systemDefault().id },
) : CaptureInterpreter {
    override suspend fun interpret(capture: Capture): CaptureInterpretationResult {
        val inputText = effectiveInputText(capture)
        if (inputText.isBlank()) {
            return CaptureInterpretationResult.BlankInput
        }

        val timeZone = try {
            val candidate = timeZoneIdProvider()
            require(candidate in ZoneId.getAvailableZoneIds()) {
                "Android timezone must be an IANA timezone identifier"
            }
            candidate
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return CaptureInterpretationResult.ProviderFailure(
                AIProviderException(AIProviderFailureReason.INVALID_REQUEST, failure),
            )
        }

        val candidate = try {
            provider.interpret(
                AIInterpretationRequest(
                    inputText = inputText,
                    capturedAt = capture.capturedAt,
                    timeZone = timeZone,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return CaptureInterpretationResult.ProviderFailure(failure)
        }

        val validation = validator.validate(
            CapturePlanDraft(
                sourceCaptureId = capture.id.value,
                actions = candidate.actions,
            ),
        )

        return when (validation) {
            is CapturePlanValidationResult.Valid ->
                CaptureInterpretationResult.Success(validation.plan)

            is CapturePlanValidationResult.Invalid ->
                CaptureInterpretationResult.InvalidPlan(validation.issues)
        }
    }

    private fun effectiveInputText(capture: Capture): String =
        when (val input = capture.originalInput) {
            is CaptureInput.Text -> input.originalText
            is CaptureInput.Voice -> capture.transcriptCorrection ?: input.originalTranscript
        }
}

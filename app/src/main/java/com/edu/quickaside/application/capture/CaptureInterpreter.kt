package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanDraft
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
) : CaptureInterpreter {
    override suspend fun interpret(capture: Capture): CaptureInterpretationResult {
        val inputText = effectiveInputText(capture)
        if (inputText.isBlank()) {
            return CaptureInterpretationResult.BlankInput
        }

        val candidate = try {
            provider.interpret(AIInterpretationRequest(inputText = inputText))
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

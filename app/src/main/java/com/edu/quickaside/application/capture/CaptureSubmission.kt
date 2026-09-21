package com.edu.quickaside.application.capture

import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException

sealed interface CaptureSubmissionResult {
    data object Blank : CaptureSubmissionResult

    data class Saved(
        val capture: Capture,
        val interpretation: CaptureInterpretationResult? = null,
        val execution: CaptureExecutionOutcome = CaptureExecutionOutcome.NoValidPlan,
    ) : CaptureSubmissionResult

    data class Failed(
        val cause: Exception,
    ) : CaptureSubmissionResult
}

sealed interface CaptureExecutionOutcome {
    /** Interpretation was absent or did not produce a validated CapturePlan. */
    data object NoValidPlan : CaptureExecutionOutcome

    /** A validated plan contained an action outside CHG-028's list-only scope. */
    data object NotEligible : CaptureExecutionOutcome

    data class Executed(
        val receipt: CapturePlanListExecutionResult.Executed,
    ) : CaptureExecutionOutcome

    data class Rejected(
        val result: CapturePlanListExecutionResult,
    ) : CaptureExecutionOutcome {
        init {
            require(
                result is CapturePlanListExecutionResult.UnsupportedAction ||
                    result is CapturePlanListExecutionResult.Rejected ||
                    result is CapturePlanListExecutionResult.MissingSourceCapture,
            ) { "A rejected Capture execution must contain a non-success executor result" }
        }
    }

    data class Failed(
        val result: CapturePlanListExecutionResult.Failed,
    ) : CaptureExecutionOutcome
}

class CaptureSubmission(
    private val writer: CaptureWriter,
    private val interpreter: CaptureInterpreter? = null,
    private val listExecutor: CapturePlanListExecutor? = null,
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
        val execution = executeEligibleListPlan(interpretation)
        return CaptureSubmissionResult.Saved(
            capture = capture,
            interpretation = interpretation,
            execution = execution,
        )
    }

    private suspend fun executeEligibleListPlan(
        interpretation: CaptureInterpretationResult?,
    ): CaptureExecutionOutcome {
        val plan = (interpretation as? CaptureInterpretationResult.Success)?.plan
            ?: return CaptureExecutionOutcome.NoValidPlan
        if (plan.actions.any { it !is CapturePlanAction.AddListItem }) {
            return CaptureExecutionOutcome.NotEligible
        }

        val executor = listExecutor ?: return CaptureExecutionOutcome.Failed(
            CapturePlanListExecutionResult.Failed(
                IllegalStateException("Eligible list CapturePlan has no configured executor"),
            ),
        )
        val result = try {
            executor.execute(plan)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            CapturePlanListExecutionResult.Failed(failure)
        }
        return when (result) {
            is CapturePlanListExecutionResult.Executed ->
                CaptureExecutionOutcome.Executed(result)

            is CapturePlanListExecutionResult.Failed ->
                CaptureExecutionOutcome.Failed(result)

            is CapturePlanListExecutionResult.UnsupportedAction,
            is CapturePlanListExecutionResult.Rejected,
            CapturePlanListExecutionResult.MissingSourceCapture,
            -> CaptureExecutionOutcome.Rejected(result)
        }
    }
}

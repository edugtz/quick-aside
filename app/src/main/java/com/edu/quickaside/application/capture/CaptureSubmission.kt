package com.edu.quickaside.application.capture

import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CapturePlan
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

    /** A validated plan contained actions from more than one executable family. */
    data object NotEligible : CaptureExecutionOutcome

    sealed interface Executed : CaptureExecutionOutcome {
        data class ListItems(
            val receipt: CapturePlanListExecutionResult.Executed,
        ) : Executed

        data class Tasks(
            val receipt: CapturePlanTaskExecutionResult.Executed,
        ) : Executed
    }

    /**
     * A validated Mandado plan needs the user's focused `Continuar` / `Nuevo`
     * lifecycle decision before any list mutation happens.
     */
    data class RequiresMandadoSessionChoice(
        val requirement: CapturePlanListExecutionResult.RequiresMandadoSessionChoice,
    ) : CaptureExecutionOutcome

    sealed interface Rejected : CaptureExecutionOutcome {
        data class ListItems(
            val result: CapturePlanListExecutionResult,
        ) : Rejected {
            init {
                require(
                    result is CapturePlanListExecutionResult.UnsupportedAction ||
                        result is CapturePlanListExecutionResult.Rejected ||
                        result is CapturePlanListExecutionResult.MandadoSessionChanged ||
                        result is CapturePlanListExecutionResult.MissingSourceCapture,
                ) {
                    "Rejected.ListItems accepts only UnsupportedAction, Rejected, " +
                        "MandadoSessionChanged, or MissingSourceCapture"
                }
            }
        }

        data class Tasks(
            val result: CapturePlanTaskExecutionResult,
        ) : Rejected {
            init {
                require(
                    result is CapturePlanTaskExecutionResult.UnsupportedAction ||
                        result is CapturePlanTaskExecutionResult.Rejected ||
                        result is CapturePlanTaskExecutionResult.RejectedPlan ||
                        result is CapturePlanTaskExecutionResult.MissingSourceCapture,
                ) {
                    "Rejected.Tasks accepts only UnsupportedAction, Rejected, RejectedPlan, or MissingSourceCapture"
                }
            }
        }
    }

    sealed interface Failed : CaptureExecutionOutcome {
        data class ListItems(
            val result: CapturePlanListExecutionResult.Failed,
        ) : Failed

        data class Tasks(
            val result: CapturePlanTaskExecutionResult.Failed,
        ) : Failed
    }
}

class CaptureSubmission(
    private val writer: CaptureWriter,
    private val interpreter: CaptureInterpreter? = null,
    private val listExecutor: CapturePlanListExecutor? = null,
    private val taskExecutor: CapturePlanTaskExecutor? = null,
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
        val execution = executeEligiblePlan(interpretation)
        return CaptureSubmissionResult.Saved(
            capture = capture,
            interpretation = interpretation,
            execution = execution,
        )
    }

    private suspend fun executeEligiblePlan(
        interpretation: CaptureInterpretationResult?,
    ): CaptureExecutionOutcome {
        val plan = (interpretation as? CaptureInterpretationResult.Success)?.plan
            ?: return CaptureExecutionOutcome.NoValidPlan

        return when {
            plan.actions.all { it is CapturePlanAction.AddListItem } ->
                executeEligibleListPlan(plan)

            plan.actions.all { it is CapturePlanAction.CreateTask } ->
                executeEligibleTaskPlan(plan)

            else -> CaptureExecutionOutcome.NotEligible
        }
    }

    private suspend fun executeEligibleListPlan(
        plan: CapturePlan,
    ): CaptureExecutionOutcome {
        val executor = listExecutor ?: return CaptureExecutionOutcome.Failed.ListItems(
            result = CapturePlanListExecutionResult.Failed(
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
                CaptureExecutionOutcome.Executed.ListItems(result)

            is CapturePlanListExecutionResult.RequiresMandadoSessionChoice ->
                CaptureExecutionOutcome.RequiresMandadoSessionChoice(result)

            is CapturePlanListExecutionResult.Failed ->
                CaptureExecutionOutcome.Failed.ListItems(result)

            is CapturePlanListExecutionResult.UnsupportedAction,
            is CapturePlanListExecutionResult.Rejected,
            is CapturePlanListExecutionResult.MandadoSessionChanged,
            CapturePlanListExecutionResult.MissingSourceCapture,
            -> CaptureExecutionOutcome.Rejected.ListItems(result)
        }
    }

    private suspend fun executeEligibleTaskPlan(
        plan: CapturePlan,
    ): CaptureExecutionOutcome {
        val executor = taskExecutor ?: return CaptureExecutionOutcome.Failed.Tasks(
            CapturePlanTaskExecutionResult.Failed(
                IllegalStateException("Eligible Task CapturePlan has no configured executor"),
            ),
        )
        val result = try {
            executor.execute(plan)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            CapturePlanTaskExecutionResult.Failed(failure)
        }
        return when (result) {
            is CapturePlanTaskExecutionResult.Executed ->
                CaptureExecutionOutcome.Executed.Tasks(result)

            is CapturePlanTaskExecutionResult.Failed ->
                CaptureExecutionOutcome.Failed.Tasks(result)

            is CapturePlanTaskExecutionResult.UnsupportedAction,
            is CapturePlanTaskExecutionResult.Rejected,
            is CapturePlanTaskExecutionResult.RejectedPlan,
            CapturePlanTaskExecutionResult.MissingSourceCapture,
            -> CaptureExecutionOutcome.Rejected.Tasks(result)
        }
    }
}

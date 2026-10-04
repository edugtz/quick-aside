package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.capture.CapturePlanActionDraft
import com.edu.quickaside.domain.capture.CapturePlanContract
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCaptureInterpreterClarificationTest {
    private val capture = Capture(
        CaptureId("local-034-capture"),
        CaptureInput.Text("Revisar PR mañana"),
        Instant.parse("2026-10-03T18:00:00Z"),
    )

    @Test
    fun validClarificationAttachesLocalIdentityAndPreservesTaskFields() = runBlocking {
        val date = LocalDate.of(2026, 10, 4)
        val result = interpret(AIInterpretationCandidate(
            clarification = AIClarificationCandidate.TaskSpace("Revisar PR", date),
        )) as CaptureInterpretationResult.ClarificationRequired

        assertEquals(CaptureClarification.TaskSpace(capture.id, "Revisar PR", date), result.clarification)
    }

    @Test
    fun emptyActionsWithoutClarificationAreUnsupported() = runBlocking {
        assertEquals(CaptureInterpretationResult.Unsupported, interpret(AIInterpretationCandidate(emptyList())))
    }

    @Test
    fun ordinaryHighConfidenceTaskRemainsSuccessWithoutClarification() = runBlocking {
        val action = CapturePlanActionDraft.CreateTask(TaskSpace.TRABAJO, "Revisar PR")
        val result = interpret(AIInterpretationCandidate(listOf(action))) as CaptureInterpretationResult.Success
        assertEquals(capture.id, result.plan.sourceCaptureId)
        assertEquals(listOf(CapturePlanAction.CreateTask(TaskSpace.TRABAJO, "Revisar PR")), result.plan.actions)
    }

    @Test
    fun blankClarificationUsesExistingTaskValidationReason() = runBlocking {
        val result = interpret(AIInterpretationCandidate(
            clarification = AIClarificationCandidate.TaskSpace(" \t"),
        )) as CaptureInterpretationResult.InvalidPlan
        assertEquals(listOf(CapturePlanValidationIssue.Action(0, CapturePlanValidationReason.BLANK_TASK_TITLE)), result.issues)
    }

    @Test
    fun overLimitClarificationUsesExistingTaskValidationReason() = runBlocking {
        val result = interpret(AIInterpretationCandidate(
            clarification = AIClarificationCandidate.TaskSpace("a".repeat(CapturePlanContract.MAX_TASK_TITLE_CHARS + 1)),
        )) as CaptureInterpretationResult.InvalidPlan
        assertEquals(listOf(CapturePlanValidationIssue.Action(0, CapturePlanValidationReason.TASK_TITLE_TOO_LONG)), result.issues)
    }

    @Test
    fun unicodeTitleAtCodePointLimitIsAcceptedWithoutDueDate() = runBlocking {
        val title = "😀".repeat(CapturePlanContract.MAX_TASK_TITLE_CHARS)
        val result = interpret(AIInterpretationCandidate(
            clarification = AIClarificationCandidate.TaskSpace(title),
        )) as CaptureInterpretationResult.ClarificationRequired
        assertEquals(CaptureClarification.TaskSpace(capture.id, title), result.clarification)
    }

    @Test
    fun candidateCannotRequestBothExecutionAndClarification() {
        val result = runCatching {
            AIInterpretationCandidate(
                actions = listOf(CapturePlanActionDraft.CreateTask(TaskSpace.PERSONAL, "Pagar")),
                clarification = AIClarificationCandidate.TaskSpace("Revisar"),
            )
        }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private suspend fun interpret(candidate: AIInterpretationCandidate) = ProviderCaptureInterpreter(
        provider = AIProvider { candidate },
        validator = CapturePlanValidator(),
        timeZoneIdProvider = { "America/Mexico_City" },
    ).interpret(capture)
}

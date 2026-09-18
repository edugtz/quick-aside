package com.edu.quickaside.application.capture

import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.domain.capture.CapturePlanActionDraft
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSubmissionRemoteIntegrationTest {
    private val capturedAt = Instant.parse("2026-09-18T20:00:00Z")

    @Test
    fun captureIsPersistedBeforeRemoteInterpretation() = runBlocking {
        val events = mutableListOf<String>()
        val provider = AIProvider { request ->
            events += "provider:${request.inputText}"
            AIInterpretationCandidate(listOf(CapturePlanActionDraft.CreateNote("nota")))
        }
        val submission = CaptureSubmission(
            writer = CaptureWriter { events += "persist:${it.id.value}" },
            interpreter = ProviderCaptureInterpreter(
                provider = provider,
                validator = CapturePlanValidator(),
                timeZoneIdProvider = { "America/Mexico_City" },
            ),
            idProvider = { CaptureId("persist-first") },
            capturedAtProvider = { capturedAt },
        )

        val result = submission.submit("Guardar esto") as CaptureSubmissionResult.Saved

        assertEquals(listOf("persist:persist-first", "provider:Guardar esto"), events)
        assertTrue(result.interpretation is CaptureInterpretationResult.Success)
    }

    @Test
    fun providerFailureLeavesPersistedCaptureAvailable() = runBlocking {
        val savedIds = mutableListOf<String>()
        val submission = CaptureSubmission(
            writer = CaptureWriter { savedIds += it.id.value },
            interpreter = ProviderCaptureInterpreter(
                provider = AIProvider {
                    throw AIProviderException(AIProviderFailureReason.NETWORK_UNAVAILABLE)
                },
                validator = CapturePlanValidator(),
                timeZoneIdProvider = { "America/Mexico_City" },
            ),
            idProvider = { CaptureId("survives-network-failure") },
            capturedAtProvider = { capturedAt },
        )

        val result = submission.submitVoice("Revisar el PR") as CaptureSubmissionResult.Saved

        assertEquals(listOf("survives-network-failure"), savedIds)
        val failure = result.interpretation as CaptureInterpretationResult.ProviderFailure
        assertEquals(
            AIProviderFailureReason.NETWORK_UNAVAILABLE,
            (failure.cause as AIProviderException).reason,
        )
    }
}

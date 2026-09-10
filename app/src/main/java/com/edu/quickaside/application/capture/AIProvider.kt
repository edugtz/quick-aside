package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.CapturePlanActionDraft

/**
 * Provider-neutral interpretation boundary. Implementations return only
 * untrusted draft actions; Capture provenance belongs to the interpreter.
 */
fun interface AIProvider {
    suspend fun interpret(request: AIInterpretationRequest): AIInterpretationCandidate
}

/** The only input needed by a provider to interpret one Capture. */
data class AIInterpretationRequest(
    val inputText: String,
)

/**
 * Untrusted provider output. It deliberately has no source Capture ID and
 * uses draft actions until CapturePlanValidator accepts them.
 */
data class AIInterpretationCandidate(
    val actions: List<CapturePlanActionDraft>,
)

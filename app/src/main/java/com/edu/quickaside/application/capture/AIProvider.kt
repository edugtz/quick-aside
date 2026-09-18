package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.CapturePlanActionDraft
import java.time.Instant

/**
 * Provider-neutral interpretation boundary. Implementations return only
 * untrusted draft actions; Capture provenance belongs to the interpreter.
 */
fun interface AIProvider {
    suspend fun interpret(request: AIInterpretationRequest): AIInterpretationCandidate
}

/** Trusted interpretation context. Local Capture identity is deliberately absent. */
data class AIInterpretationRequest(
    val inputText: String,
    val capturedAt: Instant,
    val timeZone: String,
)

/**
 * Untrusted provider output. It deliberately has no source Capture ID and
 * uses draft actions until CapturePlanValidator accepts them.
 */
data class AIInterpretationCandidate(
    val actions: List<CapturePlanActionDraft>,
)

class AIProviderException(
    val reason: AIProviderFailureReason,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

enum class AIProviderFailureReason {
    NETWORK_UNAVAILABLE,
    DNS_FAILURE,
    TLS_FAILURE,
    TIMEOUT,
    AUTHENTICATION_FAILED,
    REQUEST_TOO_LARGE,
    INVALID_REQUEST,
    RATE_LIMITED,
    PROVIDER_INVALID_OUTPUT,
    PROVIDER_UNAVAILABLE,
    PROVIDER_TIMEOUT,
    UNEXPECTED_RESPONSE,
    MALFORMED_RESPONSE,
}

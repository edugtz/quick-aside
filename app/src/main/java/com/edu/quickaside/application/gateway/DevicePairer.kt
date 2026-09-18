package com.edu.quickaside.application.gateway

fun interface DevicePairer {
    suspend fun pair(pairingCode: String): DevicePairingResult
}

sealed interface DevicePairingResult {
    data class Success(
        val deviceId: String,
    ) : DevicePairingResult

    data class Failure(
        val reason: DevicePairingFailureReason,
    ) : DevicePairingResult
}

enum class DevicePairingFailureReason {
    NETWORK_UNAVAILABLE,
    DNS_FAILURE,
    TLS_FAILURE,
    TIMEOUT,
    PAIRING_FAILED,
    REQUEST_TOO_LARGE,
    INVALID_REQUEST,
    RATE_LIMITED,
    UNEXPECTED_RESPONSE,
    MALFORMED_RESPONSE,
}

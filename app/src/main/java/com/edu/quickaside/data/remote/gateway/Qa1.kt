package com.edu.quickaside.data.remote.gateway

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

internal interface Qa1DeviceIdentity {
    val deviceId: String
    fun publicKeyPem(): String
    fun sign(canonicalRequest: ByteArray): ByteArray
}

internal class Qa1RequestAuthenticator(
    private val identity: Qa1DeviceIdentity,
    private val nonceSource: () -> String = { secureNonce() },
    private val timestampSource: () -> Long = { Instant.now().epochSecond },
) {
    fun headers(
        method: String,
        path: String,
        body: ByteArray,
    ): Map<String, String> {
        val timestamp = timestampSource().toString()
        val nonce = nonceSource()
        val canonical = buildQa1CanonicalRequest(
            method = method,
            path = path,
            body = body,
            deviceId = identity.deviceId,
            timestamp = timestamp,
            nonce = nonce,
        )
        val signature = base64UrlNoPadding(identity.sign(canonical))
        return linkedMapOf(
            "X-QA-Device-Id" to identity.deviceId,
            "X-QA-Timestamp" to timestamp,
            "X-QA-Nonce" to nonce,
            "X-QA-Signature" to signature,
        )
    }
}

internal fun buildQa1CanonicalRequest(
    method: String,
    path: String,
    body: ByteArray,
    deviceId: String,
    timestamp: String,
    nonce: String,
): ByteArray {
    val bodyHash = sha256(body).joinToString(separator = "") { byte -> "%02x".format(byte) }
    return listOf(
        "QA1",
        method.uppercase(),
        path,
        deviceId,
        timestamp,
        nonce,
        bodyHash,
    ).joinToString("\n").toByteArray(StandardCharsets.UTF_8)
}

internal fun base64UrlNoPadding(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

internal fun secureNonce(random: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    return base64UrlNoPadding(bytes)
}

private fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

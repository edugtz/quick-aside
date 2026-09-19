package com.edu.quickaside.data.remote.gateway

import com.edu.quickaside.application.capture.AIInterpretationRequest
import com.edu.quickaside.application.capture.AIProviderException
import com.edu.quickaside.application.capture.AIProviderFailureReason
import com.edu.quickaside.application.capture.CaptureInterpretationResult
import com.edu.quickaside.application.capture.CapturePlanValidator
import com.edu.quickaside.application.capture.ProviderCaptureInterpreter
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.common.CaptureId
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class QuickAsideGatewayAIProviderTest {
    private val request = AIInterpretationRequest(
        inputText = "Guardar esto",
        capturedAt = Instant.parse("2026-09-18T20:00:00Z"),
        timeZone = "America/Mexico_City",
    )

    @Test
    fun transmittedBodyIsExactlyTheBodyThatWasSigned() = runBlocking {
        val identity = RecordingIdentity()
        val transport = RecordingTransport(
            GatewayHttpResponse(200, """{"actions":[{"type":"CreateNote","text":"Nota"}]}""".toByteArray()),
        )
        val provider = QuickAsideGatewayAIProvider(
            transport,
            Qa1RequestAuthenticator(identity, { "YWJjZGVmZ2hpamtsbW5vcA" }, { 1_000L }),
        )

        provider.interpret(request)

        val sentBody = checkNotNull(transport.body)
        val expectedCanonical = buildQa1CanonicalRequest(
            "POST",
            INTERPRET_PATH,
            sentBody,
            identity.deviceId,
            "1000",
            "YWJjZGVmZ2hpamtsbW5vcA",
        )
        assertArrayEquals(expectedCanonical, identity.signed)
    }

    @Test
    fun documentedHttpFailuresMapToProviderNeutralReasons() = runBlocking {
        val expected = linkedMapOf(
            401 to AIProviderFailureReason.AUTHENTICATION_FAILED,
            413 to AIProviderFailureReason.REQUEST_TOO_LARGE,
            422 to AIProviderFailureReason.INVALID_REQUEST,
            429 to AIProviderFailureReason.RATE_LIMITED,
            502 to AIProviderFailureReason.PROVIDER_INVALID_OUTPUT,
            503 to AIProviderFailureReason.PROVIDER_UNAVAILABLE,
            504 to AIProviderFailureReason.PROVIDER_TIMEOUT,
            418 to AIProviderFailureReason.UNEXPECTED_RESPONSE,
        )

        expected.forEach { (status, reason) ->
            val failure = runCatching { provider(RecordingTransport(GatewayHttpResponse(status, ByteArray(0)))).interpret(request) }
                .exceptionOrNull() as AIProviderException
            assertEquals(reason, failure.reason)
        }
    }

    @Test
    fun transportFailuresMapExplicitly() = runBlocking {
        val cases = listOf(
            UnknownHostException() to AIProviderFailureReason.DNS_FAILURE,
            SSLHandshakeException("tls") to AIProviderFailureReason.TLS_FAILURE,
            SocketTimeoutException() to AIProviderFailureReason.TIMEOUT,
            GatewayRequestTooLargeException() to AIProviderFailureReason.REQUEST_TOO_LARGE,
            ConnectException() to AIProviderFailureReason.NETWORK_UNAVAILABLE,
        )
        cases.forEach { (transportFailure, reason) ->
            val failure = runCatching {
                provider(ThrowingTransport(transportFailure)).interpret(request)
            }.exceptionOrNull() as AIProviderException
            assertEquals(reason, failure.reason)
        }
    }

    @Test
    fun malformedSuccessMapsToMalformedResponse() = runBlocking {
        val failure = runCatching {
            provider(RecordingTransport(GatewayHttpResponse(200, "bad".toByteArray()))).interpret(request)
        }.exceptionOrNull() as AIProviderException
        assertEquals(AIProviderFailureReason.MALFORMED_RESPONSE, failure.reason)
    }

    @Test
    fun nonStringContractValueCannotReachAValidatedPlan() = runBlocking {
        val gatewayProvider = provider(
            RecordingTransport(
                GatewayHttpResponse(
                    200,
                    """{"actions":[{"type":"CreateNote","text":42}]}""".toByteArray(),
                ),
            ),
        )
        val interpreter = ProviderCaptureInterpreter(
            provider = gatewayProvider,
            validator = CapturePlanValidator(),
            timeZoneIdProvider = { "America/Mexico_City" },
        )

        val result = interpreter.interpret(
            Capture(
                id = CaptureId("structural-boundary"),
                originalInput = CaptureInput.Text("Guardar esto"),
                capturedAt = request.capturedAt,
            ),
        )

        val failure = result as CaptureInterpretationResult.ProviderFailure
        assertEquals(
            AIProviderFailureReason.MALFORMED_RESPONSE,
            (failure.cause as AIProviderException).reason,
        )
    }

    @Test
    fun cancellationPropagates() {
        val cancellation = CancellationException("cancelled")
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { provider(ThrowingTransport(cancellation)).interpret(request) }
        }
        assertSame(cancellation, thrown)
    }

    private fun provider(transport: GatewayTransport): QuickAsideGatewayAIProvider =
        QuickAsideGatewayAIProvider(
            transport,
            Qa1RequestAuthenticator(RecordingIdentity(), { "YWJjZGVmZ2hpamtsbW5vcA" }, { 1_000L }),
        )

    private class RecordingTransport(
        private val response: GatewayHttpResponse,
    ) : GatewayTransport {
        var body: ByteArray? = null
        override suspend fun post(path: String, body: ByteArray, headers: Map<String, String>): GatewayHttpResponse {
            this.body = body.copyOf()
            return response
        }
    }

    private class ThrowingTransport(
        private val failure: Exception,
    ) : GatewayTransport {
        override suspend fun post(path: String, body: ByteArray, headers: Map<String, String>): GatewayHttpResponse {
            throw failure
        }
    }

    private class RecordingIdentity : Qa1DeviceIdentity {
        override val deviceId = "qa-test"
        var signed: ByteArray? = null
        override fun publicKeyPem(): String = error("unused")
        override fun sign(canonicalRequest: ByteArray): ByteArray {
            signed = canonicalRequest.copyOf()
            return byteArrayOf(1, 2, 3)
        }
    }
}

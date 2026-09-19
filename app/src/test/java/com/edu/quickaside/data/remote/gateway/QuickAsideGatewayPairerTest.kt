package com.edu.quickaside.data.remote.gateway

import com.edu.quickaside.application.gateway.DevicePairingFailureReason
import com.edu.quickaside.application.gateway.DevicePairingResult
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.Assert.assertThrows

class QuickAsideGatewayPairerTest {
    private val identity = TestIdentity()

    @Test
    fun matchingActiveDeviceIdIsTheOnlySuccessfulPairingResponse() = runBlocking {
        val transport = RespondingTransport(pairResponse())

        val result = pairer(transport).pair("pairing-code-for-test")

        assertEquals(DevicePairingResult.Success(identity.deviceId), result)
        assertEquals(
            identity.deviceId,
            JSONObject(checkNotNull(transport.requestBody).toString(Charsets.UTF_8))
                .getString("deviceId"),
        )
    }

    @Test
    fun mismatchedDeviceIdIsRejectedAsMalformedAndDoesNotBecomeSuccess() = runBlocking {
        val result = pairer(RespondingTransport(pairResponse(deviceId = "other-device")))
            .pair("pairing-code-for-test")

        assertEquals(
            DevicePairingResult.Failure(DevicePairingFailureReason.MALFORMED_RESPONSE),
            result,
        )
    }

    @Test
    fun blankDeviceIdIsRejectedAsMalformed() = runBlocking {
        val result = pairer(RespondingTransport(pairResponse(deviceId = "")))
            .pair("pairing-code-for-test")

        assertEquals(
            DevicePairingResult.Failure(DevicePairingFailureReason.MALFORMED_RESPONSE),
            result,
        )
    }

    @Test
    fun invalidStatusIsRejectedAsMalformed() = runBlocking {
        val result = pairer(RespondingTransport(pairResponse(status = "pending")))
            .pair("pairing-code-for-test")

        assertEquals(
            DevicePairingResult.Failure(DevicePairingFailureReason.MALFORMED_RESPONSE),
            result,
        )
    }

    @Test
    fun malformedPairingResponsesAreRejected() = runBlocking {
        listOf(
            "not-json",
            "{}",
            """{"deviceId":"${identity.deviceId}"}""",
            """{"deviceId":12,"status":"active"}""",
        ).forEach { body ->
            val result = pairer(
                RespondingTransport(GatewayHttpResponse(200, body.toByteArray())),
            ).pair("pairing-code-for-test")

            assertEquals(
                DevicePairingResult.Failure(DevicePairingFailureReason.MALFORMED_RESPONSE),
                result,
            )
        }
    }

    @Test
    fun documentedHttpFailureMappingsRemainExplicit() = runBlocking {
        val expected = linkedMapOf(
            400 to DevicePairingFailureReason.PAIRING_FAILED,
            413 to DevicePairingFailureReason.REQUEST_TOO_LARGE,
            422 to DevicePairingFailureReason.INVALID_REQUEST,
            429 to DevicePairingFailureReason.RATE_LIMITED,
            500 to DevicePairingFailureReason.UNEXPECTED_RESPONSE,
        )

        expected.forEach { (status, reason) ->
            val result = pairer(
                RespondingTransport(GatewayHttpResponse(status, ByteArray(0))),
            ).pair("pairing-code-for-test")

            assertEquals(DevicePairingResult.Failure(reason), result)
        }
    }

    @Test
    fun documentedTransportFailureMappingsRemainExplicit() = runBlocking {
        val cases = listOf(
            UnknownHostException() to DevicePairingFailureReason.DNS_FAILURE,
            SSLHandshakeException("tls") to DevicePairingFailureReason.TLS_FAILURE,
            SocketTimeoutException() to DevicePairingFailureReason.TIMEOUT,
            ConnectException() to DevicePairingFailureReason.NETWORK_UNAVAILABLE,
            NoRouteToHostException() to DevicePairingFailureReason.NETWORK_UNAVAILABLE,
            IOException() to DevicePairingFailureReason.NETWORK_UNAVAILABLE,
        )

        cases.forEach { (failure, reason) ->
            val result = pairer(FailingTransport(failure)).pair("pairing-code-for-test")

            assertEquals(DevicePairingResult.Failure(reason), result)
        }
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingPairingFailure() {
        val cancellation = CancellationException("cancelled")

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                pairer(FailingTransport(cancellation)).pair("pairing-code-for-test")
            }
        }

        assertSame(cancellation, thrown)
    }

    private fun pairer(transport: GatewayTransport): QuickAsideGatewayPairer =
        QuickAsideGatewayPairer(
            transport = transport,
            identity = identity,
        )

    private fun pairResponse(
        deviceId: String = identity.deviceId,
        status: String = "active",
    ): GatewayHttpResponse = GatewayHttpResponse(
        statusCode = 200,
        body = """{"deviceId":"$deviceId","status":"$status"}""".toByteArray(),
    )

    private class TestIdentity : Qa1DeviceIdentity {
        override val deviceId: String = "qa-local-device"

        override fun publicKeyPem(): String = "public-key-not-secret-test-fixture"

        override fun sign(canonicalRequest: ByteArray): ByteArray =
            error("pairing does not sign requests")
    }

    private class RespondingTransport(
        private val response: GatewayHttpResponse,
    ) : GatewayTransport {
        var requestBody: ByteArray? = null

        override suspend fun post(
            path: String,
            body: ByteArray,
            headers: Map<String, String>,
        ): GatewayHttpResponse {
            requestBody = body.copyOf()
            return response
        }
    }

    private class FailingTransport(
        private val failure: Exception,
    ) : GatewayTransport {
        override suspend fun post(
            path: String,
            body: ByteArray,
            headers: Map<String, String>,
        ): GatewayHttpResponse = throw failure
    }
}

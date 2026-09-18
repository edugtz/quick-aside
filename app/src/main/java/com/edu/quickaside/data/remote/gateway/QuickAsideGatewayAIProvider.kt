package com.edu.quickaside.data.remote.gateway

import com.edu.quickaside.application.capture.AIInterpretationCandidate
import com.edu.quickaside.application.capture.AIInterpretationRequest
import com.edu.quickaside.application.capture.AIProvider
import com.edu.quickaside.application.capture.AIProviderException
import com.edu.quickaside.application.capture.AIProviderFailureReason
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class QuickAsideGatewayAIProvider(
    private val transport: GatewayTransport,
    private val authenticator: Qa1RequestAuthenticator,
) : AIProvider {
    override suspend fun interpret(request: AIInterpretationRequest): AIInterpretationCandidate {
        val body = GatewayJsonCodec.encodeInterpretRequest(request)
        val headers = authenticator.headers(
            method = "POST",
            path = INTERPRET_PATH,
            body = body,
        )
        val response = try {
            transport.post(INTERPRET_PATH, body, headers)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            throw mapTransportFailure(failure)
        }

        if (response.statusCode != 200) throw mapHttpFailure(response.statusCode)
        return try {
            GatewayJsonCodec.decodeInterpretResponse(response.body)
        } catch (failure: GatewayMalformedResponseException) {
            throw AIProviderException(AIProviderFailureReason.MALFORMED_RESPONSE, failure)
        }
    }
}

internal fun mapTransportFailure(failure: Exception): AIProviderException = when (failure) {
    is UnknownHostException -> AIProviderException(AIProviderFailureReason.DNS_FAILURE, failure)
    is SSLException -> AIProviderException(AIProviderFailureReason.TLS_FAILURE, failure)
    is SocketTimeoutException -> AIProviderException(AIProviderFailureReason.TIMEOUT, failure)
    is GatewayRequestTooLargeException ->
        AIProviderException(AIProviderFailureReason.REQUEST_TOO_LARGE, failure)
    is GatewayResponseTooLargeException ->
        AIProviderException(AIProviderFailureReason.MALFORMED_RESPONSE, failure)
    is ConnectException,
    is NoRouteToHostException,
    is IOException,
    -> AIProviderException(AIProviderFailureReason.NETWORK_UNAVAILABLE, failure)

    else -> AIProviderException(AIProviderFailureReason.NETWORK_UNAVAILABLE, failure)
}

internal fun mapHttpFailure(statusCode: Int): AIProviderException = AIProviderException(
    when (statusCode) {
        401 -> AIProviderFailureReason.AUTHENTICATION_FAILED
        413 -> AIProviderFailureReason.REQUEST_TOO_LARGE
        422 -> AIProviderFailureReason.INVALID_REQUEST
        429 -> AIProviderFailureReason.RATE_LIMITED
        502 -> AIProviderFailureReason.PROVIDER_INVALID_OUTPUT
        503 -> AIProviderFailureReason.PROVIDER_UNAVAILABLE
        504 -> AIProviderFailureReason.PROVIDER_TIMEOUT
        else -> AIProviderFailureReason.UNEXPECTED_RESPONSE
    },
)

package com.edu.quickaside.data.remote.gateway

import com.edu.quickaside.application.gateway.DevicePairer
import com.edu.quickaside.application.gateway.DevicePairingFailureReason
import com.edu.quickaside.application.gateway.DevicePairingResult
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

internal class QuickAsideGatewayPairer(
    private val transport: GatewayTransport,
    private val identity: Qa1DeviceIdentity,
    private val label: String = "Quick Aside Android",
) : DevicePairer {
    override suspend fun pair(pairingCode: String): DevicePairingResult {
        val body = GatewayJsonCodec.encodePairRequest(
            pairingCode = pairingCode,
            deviceId = identity.deviceId,
            label = label,
            publicKeyPem = identity.publicKeyPem(),
        )
        val response = try {
            transport.post(PAIR_PATH, body, emptyMap())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return DevicePairingResult.Failure(mapPairTransportFailure(failure))
        }

        if (response.statusCode != 200) {
            return DevicePairingResult.Failure(
                when (response.statusCode) {
                    400 -> DevicePairingFailureReason.PAIRING_FAILED
                    413 -> DevicePairingFailureReason.REQUEST_TOO_LARGE
                    422 -> DevicePairingFailureReason.INVALID_REQUEST
                    429 -> DevicePairingFailureReason.RATE_LIMITED
                    else -> DevicePairingFailureReason.UNEXPECTED_RESPONSE
                },
            )
        }

        return try {
            val returnedDeviceId = GatewayJsonCodec.decodePairResponse(response.body)
            if (returnedDeviceId != identity.deviceId) {
                DevicePairingResult.Failure(DevicePairingFailureReason.MALFORMED_RESPONSE)
            } else {
                DevicePairingResult.Success(returnedDeviceId)
            }
        } catch (_: GatewayMalformedResponseException) {
            DevicePairingResult.Failure(DevicePairingFailureReason.MALFORMED_RESPONSE)
        }
    }
}

private fun mapPairTransportFailure(failure: Exception): DevicePairingFailureReason = when (failure) {
    is UnknownHostException -> DevicePairingFailureReason.DNS_FAILURE
    is SSLException -> DevicePairingFailureReason.TLS_FAILURE
    is SocketTimeoutException -> DevicePairingFailureReason.TIMEOUT
    is GatewayRequestTooLargeException -> DevicePairingFailureReason.REQUEST_TOO_LARGE
    is GatewayResponseTooLargeException -> DevicePairingFailureReason.MALFORMED_RESPONSE
    is ConnectException,
    is NoRouteToHostException,
    is IOException,
    -> DevicePairingFailureReason.NETWORK_UNAVAILABLE

    else -> DevicePairingFailureReason.NETWORK_UNAVAILABLE
}

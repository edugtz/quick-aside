package com.edu.quickaside.data.remote.gateway

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class GatewayTransportTest {
    @Test
    fun oversizedRequestIsRejectedBeforeOpeningConnection() {
        val transport = HttpsUrlConnectionGatewayTransport(
            baseUrl = "https://127.0.0.1",
            maxRequestBytes = 1,
        )

        assertThrows(GatewayRequestTooLargeException::class.java) {
            runBlocking {
                transport.post(
                    path = INTERPRET_PATH,
                    body = byteArrayOf(1, 2),
                    headers = emptyMap(),
                )
            }
        }
    }
}

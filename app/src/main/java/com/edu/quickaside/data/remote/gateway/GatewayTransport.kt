package com.edu.quickaside.data.remote.gateway

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal const val QUICK_ASIDE_GATEWAY_BASE_URL = "https://quickaside.taildc9db9.ts.net"
internal const val PAIR_PATH = "/v1/pair"
internal const val INTERPRET_PATH = "/v1/interpret"
internal const val MAX_GATEWAY_REQUEST_BYTES = 32 * 1024

internal data class GatewayHttpResponse(
    val statusCode: Int,
    val body: ByteArray,
)

internal fun interface GatewayTransport {
    suspend fun post(
        path: String,
        body: ByteArray,
        headers: Map<String, String>,
    ): GatewayHttpResponse
}

internal class HttpsUrlConnectionGatewayTransport(
    baseUrl: String = QUICK_ASIDE_GATEWAY_BASE_URL,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 30_000,
    private val maxRequestBytes: Int = MAX_GATEWAY_REQUEST_BYTES,
    private val maxResponseBytes: Int = 128 * 1024,
) : GatewayTransport {
    init {
        require(connectTimeoutMillis > 0) { "connect timeout must be positive" }
        require(readTimeoutMillis > 0) { "read timeout must be positive" }
        require(maxRequestBytes > 0) { "maximum request size must be positive" }
        require(maxResponseBytes > 0) { "maximum response size must be positive" }
    }

    private val baseUrl = URL(baseUrl).also { url ->
        require(url.protocol == "https") { "Quick Aside gateway requires HTTPS" }
        require(url.userInfo == null) { "Quick Aside gateway URL must not contain user info" }
        require(url.query == null && url.ref == null) {
            "Quick Aside gateway base URL must not contain query or fragment"
        }
    }.toExternalForm().removeSuffix("/")

    override suspend fun post(
        path: String,
        body: ByteArray,
        headers: Map<String, String>,
    ): GatewayHttpResponse = withContext(Dispatchers.IO) {
        require(path.startsWith("/")) { "Gateway path must be absolute" }
        if (body.size > maxRequestBytes) throw GatewayRequestTooLargeException()
        val connection = URL(baseUrl + path).openConnection() as HttpsURLConnection
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion {
            connection.disconnect()
        }
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            connection.outputStream.use { output -> output.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            GatewayHttpResponse(
                statusCode = status,
                body = stream?.use { readBounded(it, maxResponseBytes) } ?: ByteArray(0),
            )
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }
}

internal class GatewayRequestTooLargeException : Exception("gateway request too large")
internal class GatewayResponseTooLargeException : Exception("gateway response too large")

private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw GatewayResponseTooLargeException()
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

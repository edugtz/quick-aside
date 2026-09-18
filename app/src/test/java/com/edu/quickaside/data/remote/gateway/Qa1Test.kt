package com.edu.quickaside.data.remote.gateway

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qa1Test {
    @Test
    fun canonicalRequestMatchesServerContractExactly() {
        val body = "{\"inputText\":\"Comprar leche\"}".toByteArray()
        val canonical = buildQa1CanonicalRequest(
            method = "post",
            path = "/v1/interpret",
            body = body,
            deviceId = "oppo-test-1",
            timestamp = "1000",
            nonce = "YWJjZGVmZ2hpamtsbW5vcA",
        )

        assertArrayEquals(
            (
                "QA1\n" +
                    "POST\n" +
                    "/v1/interpret\n" +
                    "oppo-test-1\n" +
                    "1000\n" +
                    "YWJjZGVmZ2hpamtsbW5vcA\n" +
                    "f97e47d8a7370c6d0b30a433f6be4e24ddec4c7d7f2646ebc03c08714a0a6663"
                ).toByteArray(),
            canonical,
        )
    }

    @Test
    fun canonicalBodyHashUsesUtf8BytesForNonAsciiText() {
        val body = "{\"inputText\":\"Mañana\"}".toByteArray(Charsets.UTF_8)

        val canonical = buildQa1CanonicalRequest(
            method = "POST",
            path = "/v1/interpret",
            body = body,
            deviceId = "qa-test",
            timestamp = "1000",
            nonce = "nonce",
        ).toString(Charsets.UTF_8)

        assertTrue(canonical.endsWith("101e944c2d5ac3552779533b30028e14892f18b7a0f5f6a3d18df659c9561a82"))
    }

    @Test
    fun requestHeadersSignTheExactBodyAndUseUnpaddedBase64Url() {
        val identity = RecordingIdentity()
        val authenticator = Qa1RequestAuthenticator(
            identity = identity,
            nonceSource = { "YWJjZGVmZ2hpamtsbW5vcA" },
            timestampSource = { 1_000L },
        )
        val body = "{\"inputText\":\"Comprar leche\"}".toByteArray()

        val headers = authenticator.headers("POST", "/v1/interpret", body)

        assertEquals("qa-test", headers["X-QA-Device-Id"])
        assertEquals("1000", headers["X-QA-Timestamp"])
        assertEquals("YWJjZGVmZ2hpamtsbW5vcA", headers["X-QA-Nonce"])
        assertFalse(checkNotNull(headers["X-QA-Signature"]).contains('='))
        assertArrayEquals(
            buildQa1CanonicalRequest(
                "POST",
                "/v1/interpret",
                body,
                "qa-test",
                "1000",
                "YWJjZGVmZ2hpamtsbW5vcA",
            ),
            identity.signed,
        )
    }

    @Test
    fun base64UrlEncodingHasNoPaddingOrStandardAlphabet() {
        val encoded = base64UrlNoPadding(byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xef.toByte()))
        assertFalse(encoded.contains('='))
        assertFalse(encoded.contains('+'))
        assertFalse(encoded.contains('/'))
        assertTrue(Base64.getUrlDecoder().decode(encoded).contentEquals(byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xef.toByte())))
    }

    @Test
    fun secureNonceIsUnpaddedBase64UrlWithAtLeast16DecodedBytes() {
        val values = List(32) { secureNonce() }
        assertEquals(values.size, values.toSet().size)
        values.forEach { value ->
            assertFalse(value.contains('='))
            assertTrue(value.matches(Regex("^[A-Za-z0-9_-]+$")))
            assertTrue(Base64.getUrlDecoder().decode(value).size >= 16)
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

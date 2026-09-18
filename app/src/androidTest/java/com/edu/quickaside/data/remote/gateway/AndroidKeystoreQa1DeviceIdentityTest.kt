package com.edu.quickaside.data.remote.gateway

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreQa1DeviceIdentityTest {
    @Test
    fun keystoreIdentityIsStableNonExportableP256AndProducesVerifiableDerSignature() {
        val first = AndroidKeystoreQa1DeviceIdentity()
        val second = AndroidKeystoreQa1DeviceIdentity()
        val canonical = "QA1\nPOST\n/v1/interpret\nfixture".toByteArray()

        val signature = first.sign(canonical)
        assertEquals(first.deviceId, second.deviceId)
        assertEquals(first.publicKeyPem(), second.publicKeyPem())

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(QA1_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        assertNull(entry.privateKey.encoded)
        assertEquals(256, (entry.certificate.publicKey as java.security.interfaces.ECPublicKey).params.curve.field.fieldSize)

        val pemBody = first.publicKeyPem()
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .filterNot(Char::isWhitespace)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(pemBody)),
        )
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(canonical)
        assertTrue(verifier.verify(signature))
        assertArrayEquals(entry.certificate.publicKey.encoded, publicKey.encoded)
    }
}

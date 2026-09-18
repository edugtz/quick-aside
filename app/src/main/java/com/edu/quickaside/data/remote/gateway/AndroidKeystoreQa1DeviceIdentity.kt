package com.edu.quickaside.data.remote.gateway

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

internal const val QA1_KEY_ALIAS = "quickaside.qa1.device.p256.v1"

internal class AndroidKeystoreQa1DeviceIdentity : Qa1DeviceIdentity {
    private val entry: KeyStore.PrivateKeyEntry by lazy(::loadOrCreateEntry)

    override val deviceId: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest(entry.certificate.publicKey.encoded)
        "qa-android-${base64UrlNoPadding(digest).take(32)}"
    }

    override fun publicKeyPem(): String {
        val base64 = Base64.getEncoder().encodeToString(entry.certificate.publicKey.encoded)
        val body = base64.chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n"
    }

    override fun sign(canonicalRequest: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(entry.privateKey)
            update(canonicalRequest)
            sign()
        }

    private fun loadOrCreateEntry(): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(QA1_KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore",
            )
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    QA1_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generator.generateKeyPair()
        }

        val loaded = keyStore.getEntry(QA1_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: error("QA1 Android Keystore entry is not a private key")
        val publicKey = loaded.certificate.publicKey as? ECPublicKey
            ?: error("QA1 Android Keystore public key is not EC")
        require(publicKey.params.curve.field.fieldSize == 256) { "QA1 key must be P-256" }
        require(loaded.privateKey.encoded == null) { "QA1 private key must be non-exportable" }
        return loaded
    }
}

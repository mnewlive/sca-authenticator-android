/*
 * Copyright (c) 2021 Salt Edge Inc.
 */
@file:Suppress("DEPRECATION")

package com.saltedge.authenticator.core.tools.secure

import android.util.Base64
import com.saltedge.authenticator.core.tools.encodeToPemBase64String
import timber.log.Timber
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.KeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec

const val DEFAULT_KEY_SIZE = 2048

object KeyAlgorithm {
    const val RSA = "RSA"
    const val AES = "AES"
}

/**
 * Convert private key from asymmetric key pair to pem string
 *
 * @receiver KeyPair object
 * @return private key as String
 */
fun KeyPair.privateKeyToPem(): String {
    val encodedKey = encodeToPemBase64String(this.private.encoded)
    return "-----BEGIN PRIVATE KEY-----\n$encodedKey\n-----END PRIVATE KEY-----\n"
}

/**
 * Convert public key from asymmetric key pair to pem string
 *
 * @receiver KeyPair object
 * @return public key as String
 */
fun KeyPair.publicKeyToPem(): String = this.public.publicKeyToPem()

/**
 * Convert public key to pem string
 *
 * @receiver RSA PublicKey
 * @return public key as String
 */
fun PublicKey.publicKeyToPem(): String {
    val encodedKey = encodeToPemBase64String(this.encoded)
    return "-----BEGIN PUBLIC KEY-----\n$encodedKey\n-----END PUBLIC KEY-----\n"
}

/**
 * Converts string which contains public key in PKCS#8 PEM format to PublicKey object
 *
 * @receiver public key in PKCS#8 PEM format
 * @return PublicKey or null if invalid
 */
fun String.pemToPublicKey(algorithm: String): PublicKey? {
    return try {
        val cleanedKeyContent = this
            .replace("\\r\\n", "")
            .replace("\\n", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
        val keySpec: KeySpec = X509EncodedKeySpec(Base64.decode(cleanedKeyContent, Base64.NO_WRAP))

        KeyFactory.getInstance(algorithm).generatePublic(keySpec)
    } catch (e: Exception) {
        Timber.e(e, "Invalid PEM key: $this")
        null
    }
}

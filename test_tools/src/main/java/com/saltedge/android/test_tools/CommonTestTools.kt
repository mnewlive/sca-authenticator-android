/*
 * Copyright (c) 2020 Salt Edge Inc.
 */
package com.saltedge.android.test_tools

import com.google.gson.Gson
import com.saltedge.authenticator.core.api.model.ConsentData
import com.saltedge.authenticator.core.api.model.EncryptedData
import com.saltedge.authenticator.core.model.ConnectionAbs
import com.saltedge.authenticator.core.tools.encodeToPemBase64String
import com.saltedge.authenticator.sdk.api.model.authorization.AuthorizationData
import com.saltedge.authenticator.sdk.v2.api.model.authorization.AuthorizationResponseData
import com.saltedge.authenticator.sdk.v2.api.model.authorization.AuthorizationV2Data
import com.saltedge.authenticator.sdk.v2.tools.CryptoToolsV2
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CommonTestTools {
    val aesKey = byteArrayOf(
        65, 1, 2, 23, 4, 5, 6, 7, 32, 21, 10, 11, 12, 13, 84, 45,
        65, 1, 2, 23, 4, 5, 6, 7, 32, 21, 10, 11, 12, 13, 84, 45
    )
    val aesIV = byteArrayOf(65, 1, 2, 23, 4, 5, 6, 7, 32, 21, 10, 11, 12, 13, 84, 45)
    val testPrivateKey: PrivateKey = loadTestPrivateKey()
    val testPublicKey: PublicKey = loadTestPublicKey()
}

fun AuthorizationData.encryptWithTestKey(): EncryptedData {
    return encryptWithTestKey(
        id = this.id,
        connectionId = this.connectionId,
        jsonString = this.toJsonString(),
        publicKey = CommonTestTools.testPublicKey
    )
}

fun AuthorizationV2Data.encryptWithTestKey(): AuthorizationResponseData {
    val jsonString = this.toJsonString()
    val publicKey = CommonTestTools.testPublicKey
    return AuthorizationResponseData(
        id = this.authorizationID!!,
        connectionId = this.connectionID!!,
        status = this.status!!,
        key = CryptoToolsV2.rsaEncrypt(CommonTestTools.aesKey, publicKey)!!,
        iv = CryptoToolsV2.rsaEncrypt(CommonTestTools.aesIV, publicKey)!!,
        data = encryptAesCBCString(jsonString, CommonTestTools.aesKey, CommonTestTools.aesIV)!!,
        finishedAt = this.finishedAt
    )
}

fun ConsentData.encryptWithTestKey(): EncryptedData {
    return encryptWithTestKey(
        id = this.id,
        connectionId = this.connectionId,
        jsonString = this.toJsonString(),
        publicKey = CommonTestTools.testPublicKey
    )
}

fun Any.toJsonString(): String = Gson().toJson(this)

@Throws(Exception::class)
fun getDefaultTestConnection(): ConnectionAbs =
    TestConnection(
        id = "333",
        guid = "test",
        connectUrl = "https://localhost",
        accessToken = "accessToken"
    )

fun encryptAesCBCString(text: String, key: ByteArray, iv: ByteArray): String? {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return encodeToPemBase64String(cipher.doFinal(text.toByteArray()))
}

private fun encryptWithTestKey(
    id: String,
    connectionId: String?,
    jsonString: String,
    publicKey: PublicKey
): EncryptedData {
    return EncryptedData(
        id = id,
        connectionId = connectionId ?: "",
        algorithm = "AES-256-CBC",
        key = CryptoToolsV2.rsaEncrypt(CommonTestTools.aesKey, publicKey)!!,
        iv = CryptoToolsV2.rsaEncrypt(CommonTestTools.aesIV, publicKey)!!,
        data = encryptAesCBCString(jsonString, CommonTestTools.aesKey, CommonTestTools.aesIV)!!
    )
}

/*
 * Copyright (c) 2019 Salt Edge Inc.
 */
package com.saltedge.authenticator.tools

import android.content.Context
import com.saltedge.authenticator.core.tools.secure.KeyManager
import com.saltedge.authenticator.models.repository.PreferenceRepository
import com.saltedge.authenticator.sdk.v2.tools.CryptoToolsV2

private const val PASSCODE_SECURE_KEY_ALIAS = "base_alias_for_pin"

interface PasscodeToolsAbs {
    fun replacePasscodeKey(context: Context)
    fun savePasscode(passcode: String): Boolean
    fun getPasscode(): String
}

object PasscodeTools : PasscodeToolsAbs {

    /**
     * Replace KeyPair, destined for Passcode encryption, with new in KeyStoreManager
     */
    override fun replacePasscodeKey(context: Context) {
        KeyManager.createOrReplaceAesKey(PASSCODE_SECURE_KEY_ALIAS)
    }

    /**
     * Encrypts new passcode and save it in preferences
     *
     * @see PreferenceRepository.encryptedPasscode
     * @param passcode
     * @return boolean, true if PreferenceRepository.encryptedPasscode is equal encryptedPasscode
     */
    override fun savePasscode(passcode: String): Boolean {
        val encryptedPasscode = KeyManager.getSecretKey(PASSCODE_SECURE_KEY_ALIAS)?.let { key ->
            CryptoToolsV2.aesGcmEncrypt(input = passcode, key = key)
        }
        PreferenceRepository.encryptedPasscode = encryptedPasscode ?: return false
        return true
    }

    /**
     * Read passcode from preferences and decrypt it
     *
     * @return decrypted passcode
     * @see PreferenceRepository.encryptedPasscode
     */
    override fun getPasscode(): String {
        val encryptedPasscode = PreferenceRepository.encryptedPasscode
        if (encryptedPasscode.isBlank()) return ""
        return KeyManager.getSecretKey(PASSCODE_SECURE_KEY_ALIAS)?.let { key ->
            CryptoToolsV2.aesGcmDecrypt(encryptedPasscode, key)
        } ?: return ""
    }
}

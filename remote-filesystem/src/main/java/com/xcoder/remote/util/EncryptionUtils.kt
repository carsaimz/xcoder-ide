package com.xcoder.remote.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts and decrypts sensitive strings (passwords, passphrases)
 * using Android's Keystore-backed AES-256-GCM.
 *
 * Data at rest is: IV (12 bytes) || ciphertext || auth tag (16 bytes),
 * all Base64-encoded for safe storage in DataStore or JSON.
 */
@Singleton
class EncryptionUtils @Inject constructor() {

    companion object {
        private const val KEY_ALIAS = "xcoder_remote_credentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
    }

    private fun getOrCreateKey(): SecretKey {
 val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * Encrypt plaintext. Returns a Base64 string containing IV + ciphertext + tag.
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isBlank()) return ""
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    /**
     * Decrypt a value previously encrypted with [encrypt].
     */
    fun decrypt(ciphertext: String): String {
        if (ciphertext.isBlank()) return ""
        try {
            val key = getOrCreateKey()
            val combined = android.util.Base64.decode(ciphertext, android.util.Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decrypted = cipher.doFinal(encrypted)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("Decryption failed. The key may have been invalidated.", e)
        }
    }

    /**
     * Check if the keystore key exists.
     */
    fun hasKey(): Boolean {
        return try {
            keyStore.getKey(KEY_ALIAS, null) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Delete the keystore key, rendering all previously encrypted data unreadable.
     */
    fun destroyKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) { }
    }
}

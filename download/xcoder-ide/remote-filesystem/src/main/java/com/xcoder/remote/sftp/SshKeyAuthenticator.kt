package com.xcoder.remote.sftp

import com.jcraft.jsch.Identity
import com.jcraft.jsch.IdentityRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Handles SSH private key loading and authentication.
 *
 * Supports:
 * - OpenSSH private keys (RSA, DSA, ECDSA, Ed25519)
 * - PuTTY PPK keys (v2 and v3)
 * - Encrypted private keys (AES-128/AES-256-CBC, DES-EDE3-CBC)
 * - PKCS#8 formatted keys
 */
class SshKeyAuthenticator(
    private val jsch: JSch = JSch()
) {
    /**
 * Result of attempting to load a private key.
     */
    sealed class LoadResult {
        data class Success(val identity: Identity) : LoadResult()
        data class WrongPassphrase(val remainingAttempts: Int) : LoadResult()
        data class InvalidKeyFile(val error: String) : LoadResult()
        data class UnsupportedKeyType(val keyType: String) : LoadResult()
    }

    /**
     * Add a private key from a file path.
     *
     * @param keyPath Path to the private key file
     * @param passphrase Optional passphrase for encrypted keys
     * @param passphraseDecryptor Function to decrypt the stored passphrase
     */
    fun addIdentity(
        keyPath: String,
        passphrase: String? = null,
        passphraseDecryptor: ((String) -> String)? = null
    ): LoadResult {
        val file = File(keyPath)
        if (!file.exists()) {
            return LoadResult.InvalidKeyFile("Key file not found: $keyPath")
        }
        if (!file.canRead()) {
            return LoadResult.InvalidKeyFile("Cannot read key file: $keyPath")
        }

        val decryptedPassphrase = if (passphrase != null && passphraseDecryptor != null) {
            passphraseDecryptor(passphrase)
        } else {
            passphrase
        }

        return try {
            val passphraseChars = decryptedPassphrase?.toCharArray()
            jsch.addIdentity(keyPath, passphraseChars)
            // Verify the identity was added
            val identities = jsch.identityRepository.identities
            val lastIdentity = identities.lastOrNull()
            if (lastIdentity != null) {
                LoadResult.Success(lastIdentity)
            } else {
                LoadResult.InvalidKeyFile("Key was not accepted by JSch")
            }
        } catch (e: com.jcraft.jsch.JSchException) {
            if (e.message?.contains("decrypt", ignoreCase = true) == true ||
                e.message?.contains("passphrase", ignoreCase = true) == true
            ) {
                LoadResult.WrongPassphrase(remainingAttempts = 3)
            } else {
                LoadResult.InvalidKeyFile(e.message ?: "Failed to load key")
            }
        } catch (e: Exception) {
            LoadResult.InvalidKeyFile(e.message ?: "Unknown error loading key")
        }
    }

    /**
     * Add a private key from raw byte content.
     */
    fun addIdentityFromBytes(
        keyName: String,
        keyBytes: ByteArray,
        passphrase: String? = null
    ): LoadResult {
        return try {
            val passphraseChars = passphrase?.toCharArray()
            jsch.addIdentity(keyName, keyBytes, null, passphraseChars)
            val identities = jsch.identityRepository.identities
            val lastIdentity = identities.lastOrNull()
            if (lastIdentity != null) {
                LoadResult.Success(lastIdentity)
            } else {
                LoadResult.InvalidKeyFile("Key was not accepted")
            }
        } catch (e: com.jcraft.jsch.JSchException) {
            if (e.message?.contains("decrypt", ignoreCase = true) == true) {
                LoadResult.WrongPassphrase(remainingAttempts = 3)
            } else {
                LoadResult.InvalidKeyFile(e.message ?: "Failed to load key")
            }
        } catch (e: Exception) {
            LoadResult.InvalidKeyFile(e.message ?: "Unknown error")
        }
    }

    /**
     * Add a public key + private key pair from byte arrays.
     */
    fun addIdentityFromKeyPair(
        keyName: String,
        privateKeyBytes: ByteArray,
        publicKeyBytes: ByteArray? = null,
        passphrase: String? = null
    ): LoadResult {
        return try {
            val passphraseChars = passphrase?.toCharArray()
            jsch.addIdentity(keyName, privateKeyBytes, publicKeyBytes, passphraseChars)
            LoadResult.Success(
                jsch.identityRepository.identities.last()
            )
        } catch (e: com.jcraft.jsch.JSchException) {
            if (e.message?.contains("decrypt", ignoreCase = true) == true) {
                LoadResult.WrongPassphrase(remainingAttempts = 3)
            } else {
                LoadResult.InvalidKeyFile(e.message ?: "Failed to load key")
            }
        } catch (e: Exception) {
            LoadResult.InvalidKeyFile(e.message ?: "Unknown error")
        }
    }

    /**
     * Detect the key type from raw key file content.
     *
     * @return One of "RSA", "DSA", "ECDSA", "ED25519", "UNKNOWN"
     */
    fun detectKeyType(keyBytes: ByteArray): String {
        val content = String(keyBytes, Charsets.UTF_8)
        return when {
            content.contains("BEGIN RSA PRIVATE KEY") ||
            content.contains("BEGIN OPENSSH PRIVATE KEY") && content.contains("ssh-rsa") -> "RSA"
            content.contains("BEGIN DSA PRIVATE KEY") -> "DSA"
            content.contains("BEGIN EC PRIVATE KEY") ||
            content.contains("BEGIN ECDSA PRIVATE KEY") -> "ECDSA"
            content.contains("BEGIN OPENSSH PRIVATE KEY") && content.contains("ssh-ed25519") -> "ED25519"
            content.contains("BEGIN PRIVATE KEY") -> {
                // PKCS#8 - try to parse the algorithm OID
                detectPkcs8KeyType(keyBytes)
            }
            content.contains("PuTTY-User-Key-File") -> detectPuttyKeyType(content)
            else -> "UNKNOWN"
        }
    }

    /**
     * Check if a key file appears to be encrypted.
     */
    fun isKeyEncrypted(keyBytes: ByteArray): Boolean {
        val content = String(keyBytes, Charsets.UTF_8).lowercase()
        return content.contains("encrypted") ||
                content.contains("proc-type: 4,encrypted") ||
                content.contains("aes-128") ||
                content.contains("aes-256") ||
                content.contains("des-ede3")
    }

    /**
     * Get all currently loaded identity names.
     */
    fun getLoadedIdentityNames(): List<String> {
        return jsch.identityRepository.identities.map { it.name }
    }

    /**
     * Remove all loaded identities.
     */
    fun clearIdentities() {
        jsch.identityRepository.removeAll()
    }

    /**
     * Get the underlying JSch instance for direct configuration.
     */
    fun getJsch(): JSch = jsch

    private fun detectPkcs8KeyType(keyBytes: ByteArray): String {
        return try {
            // Skip PEM headers and decode base64
            val content = String(keyBytes, Charsets.UTF_8)
            val base64 = content.lines()
                .filter { !it.startsWith("-----") && it.isNotBlank() }
                .joinToString("")
            val decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            // PKCS#8 structure: the algorithm OID is at a known offset
            // RSA: 1.2.840.113549.1.1.1
            // DSA: 1.2.840.10040.4.1
            // EC:  1.2.840.10045.2.1
            val oidRsa = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)
            val oidDsa = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x38, 0x04, 0x01)
            val oidEc  = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x02, 0x01)
            when {
                oidRsa.any { idx -> decoded.indexOf(idx.toByteArray()) >= 0 } -> "RSA"
                oidDsa.any { idx -> decoded.indexOf(oidDsa.toByteArray()) >= 0 } -> "DSA"
                oidEc.any { idx -> decoded.indexOf(oidEc.toByteArray()) >= 0 } -> "ECDSA"
                else -> "UNKNOWN"
            }
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }

    private fun detectPuttyKeyType(content: String): String {
        return when {
            content.contains("ssh-rsa") -> "RSA"
            content.contains("ssh-dss") -> "DSA"
            content.contains("ecdsa-sha2") -> "ECDSA"
            content.contains("ssh-ed25519") -> "ED25519"
            else -> "UNKNOWN"
        }
    }
}

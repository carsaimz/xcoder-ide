package com.xcoder.remote.sftp

import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KnownHosts
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Manages SSH host key verification for SFTP connections.
 *
 * Maintains a local `known_hosts` file and can operate in three modes:
 * 1. **Strict**: Reject unknown or changed host keys.
 * 2. **Accept new**: Accept first-seen keys, reject changes (Acode-style default).
 * 3. **Trust all**: Accept any key (insecure, for development only).
 *
 * The known_hosts file is stored in the app's internal storage.
 */
class HostKeyVerifier(
    private val knownHostsFile: File,
    private val mode: VerificationMode = VerificationMode.ACCEPT_NEW
) {
    enum class VerificationMode {
        STRICT,
        ACCEPT_NEW,
        TRUST_ALL
    }

    /**
     * Result of host key verification.
     */
    sealed class VerifyResult {
        object Accepted : VerifyResult()
        data class NewKey(val fingerprint: String, val keyType: String) : VerifyResult()
        data class Changed(val oldFingerprint: String, val newFingerprint: String, val keyType: String) : VerifyResult()
        data class Rejected(val reason: String) : VerifyResult()
    }

    private val repository: KnownHosts by lazy {
        KnownHosts().also {
            if (knownHostsFile.exists()) {
                try {
                    it.addHostKeys(knownHostsFile.absolutePath)
                } catch (_: Exception) { }
            }
        }
    }

    /**
     * Verify a host key from a JSch session.
     */
    fun verify(host: String, port: Int, keyType: String, fingerprint: String): VerifyResult {
        if (mode == VerificationMode.TRUST_ALL) {
            return VerifyResult.Accepted
        }

        val hostEntry = "$host:$port"
        val existing = repository.getHost(hostEntry, keyType)

        if (existing == null) {
            if (mode == VerificationMode.STRICT) {
                return VerifyResult.Rejected("Host key not found for $hostEntry ($keyType)")
            }
            // ACCEPT_NEW mode: return the new key info so the caller can prompt the user
            return VerifyResult.NewKey(fingerprint, keyType)
        }

        val existingFingerprint = fingerprintForKey(existing.key)
        if (existingFingerprint != fingerprint) {
            return VerifyResult.Changed(existingFingerprint, fingerprint, keyType)
        }

        return VerifyResult.Accepted
    }

    /**
     * Add a verified host key to the local known_hosts file.
     */
    fun addHostKey(host: String, port: Int, keyType: String, key: ByteArray) {
        try {
            val hostEntry = "$host:$port"
            repository.add(hostEntry, keyType, key, null)
            persistKnownHosts()
        } catch (_: Exception) { }
    }

    /**
     * Remove a host key entry, typically after the user accepts a changed key.
     */
    fun removeHostKey(host: String, port: Int, keyType: String) {
        try {
            val hostEntry = "$host:$port"
            repository.remove(hostEntry, keyType)
            persistKnownHosts()
        } catch (_: Exception) { }
    }

    /**
     * Get a human-readable fingerprint for a host key.
     */
    fun getFingerprint(key: ByteArray, algorithm: String = "SHA-256"): String {
        val digest = try {
            MessageDigest.getInstance(algorithm)
        } catch (_: Exception) {
            MessageDigest.getInstance("SHA-1")
        }
        val hash = digest.digest(key)
        return if (algorithm == "SHA-256") {
            "SHA-256:${android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)}"
        } else {
            hash.joinToString(":") { "%02x".format(it) }
        }
    }

    /**
     * Create a JSch [KnownHosts] repository configured with our known_hosts file.
     */
    fun createJschKnownHosts(): KnownHosts = repository

    private fun fingerprintForKey(key: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(key)
            "SHA-256:${android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)}"
        } catch (_: Exception) {
            ""
        }
    }

    private fun persistKnownHosts() {
        try {
            knownHostsFile.parentFile?.mkdirs()
            FileOutputStream(knownHostsFile).use { out ->
                out.write(repository.getHostKey())
            }
        } catch (_: Exception) { }
    }
}

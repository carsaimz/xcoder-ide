package com.xcoder.core.git

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.URIish
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Singleton
class GitCredentials @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences("xcoder_git_credentials", Context.MODE_PRIVATE)

    private val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val alias = "xcoder_git_credential_store"

    suspend fun storeHttpsCredentials(host: String, username: String, token: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val key = getOrCreateEncryptionKey()
                val encrypted = encrypt(token, key)
                prefs.edit()
                    .putString("https_$host", "${username}::${Base64.getEncoder().encodeToString(encrypted)}")
                    .apply()
                true
            } catch (_: Exception) {
                false
            }
        }

    suspend fun getHttpsCredentials(host: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val stored = prefs.getString("https_$host", null) ?: return@withContext null
            val separatorIndex = stored.indexOf("::")
            if (separatorIndex < 0) return@withContext null
            val username = stored.substring(0, separatorIndex)
            val encryptedToken = Base64.getDecoder().decode(stored.substring(separatorIndex + 2))
            val key = getOrCreateEncryptionKey()
            val token = decrypt(encryptedToken, key)
            Pair(username, token)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun storeSshKey(host: String, privateKeyPem: String, passphrase: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val key = getOrCreateEncryptionKey()
                val encryptedKey = encrypt(privateKeyPem, key)
                prefs.edit()
                    .putString(
                        "ssh_$host",
                        "${Base64.getEncoder().encodeToString(encryptedKey)}::$passphrase"
                    )
                    .apply()
                true
            } catch (_: Exception) {
                false
            }
        }

    suspend fun getSshKey(host: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val stored = prefs.getString("ssh_$host", null) ?: return@withContext null
            val separatorIndex = stored.indexOf("::")
            val encryptedKey = Base64.getDecoder().decode(stored.substring(0, separatorIndex))
            val passphrase = stored.substring(separatorIndex + 2)
            val key = getOrCreateEncryptionKey()
            val privateKeyPem = decrypt(encryptedKey, key)
            Pair(privateKeyPem, passphrase)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun storeDefaultSshKey(privateKeyPem: String, passphrase: String = ""): Boolean =
        storeSshKey("default", privateKeyPem, passphrase)

    suspend fun getDefaultSshKey(): Pair<String, String>? = getSshKey("default")

    suspend fun removeCredentials(host: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove("https_$host")
            .remove("ssh_$host")
            .apply()
    }

    suspend fun removeDefaultSshKey() = removeCredentials("default")

    fun getKnownHosts(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith("https_") || it.startsWith("ssh_") }
            .map { it.substringAfter("_") }
            .toSet()
    }

    fun hasCredentialsForHost(host: String): Boolean {
        return prefs.contains("https_$host") || prefs.contains("ssh_$host")
    }

    fun getCredentialProvider(): CredentialsProvider {
        return object : CredentialsProvider() {
            override fun isInteractive(): Boolean = true

            override fun supports(items: Array<out CredentialItem>?): Boolean {
                return items?.any { item ->
                    item is CredentialItem.Username ||
                        item is CredentialItem.Password ||
                        item is CredentialItem.StringType
                } == true
            }

            override fun get(uri: URIish, items: Array<out CredentialItem>): Boolean {
                val host = uri.host ?: return false
                for (item in items) {
                    when (item) {
                        is CredentialItem.Username -> {
                            val creds = runBlocking { getHttpsCredentials(host) }
                            if (creds != null) {
                                item.value = creds.first
                            }
                        }
                        is CredentialItem.Password -> {
                            val creds = runBlocking { getHttpsCredentials(host) }
                            if (creds != null) {
                                item.value = creds.second.toCharArray()
                            }
                        }
                        is CredentialItem.StringType -> {
                            if (item.promptText.contains("password", ignoreCase = true) ||
                                item.promptText.contains("token", ignoreCase = true)
                            ) {
                                val creds = runBlocking { getHttpsCredentials(host) }
                                if (creds != null) {
                                    item.value = creds.second
                                }
                            }
                        }
                    }
                }
                return true
            }
        }
    }

    fun createSshSessionFactory(sshKey: String? = null, passphrase: String? = null): SshSessionFactory {
        val jsch = JSch()
        val keyToUse = sshKey ?: runBlocking { getDefaultSshKey()?.first }
        val passToUse = passphrase ?: runBlocking { getDefaultSshKey()?.second }
        if (keyToUse != null) {
            jsch.addIdentity("xcoder_key", keyToUse.toByteArray(), null, passToUse?.toByteArray())
        }
        val knownHostsFile = java.io.File(context.filesDir, ".ssh/known_hosts")
        knownHostsFile.parentFile?.mkdirs()
        if (knownHostsFile.exists()) {
            jsch.setKnownHosts(knownHostsFile.absolutePath)
        }

        val configuredJsch = jsch
        val configuredPass = passToUse

        return object : SshSessionFactory() {
            override fun getSession(uri: URIish?, credentialsProvider: CredentialsProvider?, fs: org.eclipse.jgit.util.FS?, tms: Int): org.eclipse.jgit.transport.RemoteSession {
                val port = uri?.port ?: 22
                val session = configuredJsch.getSession(uri?.user, uri?.host, port)
                session.setUserInfo(object : UserInfo {
                    override fun getPassphrase(): String = configuredPass ?: ""
                    override fun getPassword(): String = ""
                    override fun promptPassword(message: String?): Boolean = true
                    override fun promptPassphrase(message: String?): Boolean = true
                    override fun promptYesNo(message: String?): Boolean = true
                    override fun showMessage(message: String?) {}
                })
                session.connect()
                @Suppress("UNCHECKED_CAST")
                return session as org.eclipse.jgit.transport.RemoteSession
            }
        }
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        if (keystore.containsAlias(alias)) {
            val entry = keystore.getEntry(alias, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return iv + encrypted
    }

    private fun decrypt(ciphertext: ByteArray, key: SecretKey): String {
        val iv = ciphertext.copyOfRange(0, 12)
        val encrypted = ciphertext.copyOfRange(12, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

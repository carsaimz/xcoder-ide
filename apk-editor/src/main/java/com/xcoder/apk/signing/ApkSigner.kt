package com.xcoder.apk.signing

import android.util.Log
import java.io.*
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import java.util.zip.*

/**
 * APK signing implementation using com.android.apksig library.
 *
 * Provides:
 * - Custom keystore loading (JKS/PKCS12)
 * - V1 (JAR), V2 (APK Signature Scheme V2), V3 (APK Signature Scheme V3) signing
 * - Signature verification
 * - Signing certificate extraction
 *
 * The apksig library is the same one used by the Android build system,
 * ensuring maximum compatibility with Android's verification logic.
 */
class ApkSigner() {

    companion object {
        private const val TAG = "ApkSigner"
        private const val DEFAULT_KEY_ALIAS = "xcoder"
        private const val DEFAULT_KEY_PASSWORD = "xcoder123"
    }

    // ── Data classes ───────────────────────────────────────────────

    /** Signing key loaded from a keystore. */
    data class SigningKey(
        val alias: String,
        val privateKey: PrivateKey,
        val certificates: List<X509Certificate>,
        val keystoreType: String = "PKCS12",
        val password: String = ""
    ) {
        val certificate: X509Certificate get() = certificates.first()
        val issuer: String get() = certificate.issuerX500Principal.name
        val subject: String get() = certificate.subjectX500Principal.name
        val serialNumber: String get() = certificate.serialNumber.toString(16)
        val algorithm: String get() = certificate.sigAlgName
        val version: String get() = "v${certificate.version}"
        val validFrom: String
            get() = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(certificate.notBefore)
        val validUntil: String
            get() = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(certificate.notAfter)
    }

    /** Result of a signing operation. */
    data class SigningResult(
        val success: Boolean,
        val signedApkPath: String? = null,
        val error: String? = null,
        val signingTimeMs: Long = 0,
        val v1Signed: Boolean = false,
        val v2Signed: Boolean = false,
        val v3Signed: Boolean = false
    )

    /** Signing configuration options. */
    data class SigningConfig(
        val minSdkVersion: Int = 21,
        val v1Enabled: Boolean = true,
        val v2Enabled: Boolean = true,
        val v3Enabled: Boolean = true,
        val debuggable: Boolean = false,
        val deterministicDsaSigning: Boolean = true
    )

    // ── Keystore loading ───────────────────────────────────────────

    /**
     * Load a signing key from a keystore file.
     *
     * @param keystorePath path to the keystore file
     * @param alias the key alias to load
     * @param password the keystore and key password
     * @param keystoreType "PKCS12" or "JKS" (auto-detected if null)
     * @return the loaded [SigningKey]
     * @throws Exception if the keystore cannot be loaded
     */
    @Throws(Exception::class)
    fun loadKeystore(
        keystorePath: String,
        alias: String,
        password: String,
        keystoreType: String? = null
    ): SigningKey {
        val ksType = keystoreType
            ?: if (keystorePath.endsWith(".jks")) "JKS" else "PKCS12"
        val pwdChars = password.toCharArray()

        val ks = java.security.KeyStore.getInstance(ksType)
        java.io.FileInputStream(keystorePath).use { fis ->
            ks.load(fis, pwdChars)
        }

        val key = ks.getKey(alias, pwdChars) as PrivateKey
        val certChain = ks.getCertificateChain(alias)
            ?: throw IllegalArgumentException("No certificate chain found for alias '$alias'")

        val certs = certChain.map { cert ->
            CertificateFactory.getInstance("X.509")
                .generateCertificate(java.io.ByteArrayInputStream(cert.encoded)) as X509Certificate
        }

        return SigningKey(
            alias = alias,
            privateKey = key,
            certificates = certs,
            keystoreType = ksType,
            password = password
        )
    }

    // ── Signing ────────────────────────────────────────────────────

    /**
     * Sign an APK with a custom signing key.
     *
     * Uses com.android.apksig library to produce a properly signed APK with:
     * - V1 signing (JAR signature)
     * - V2 signing (APK Signature Scheme V2, Android 7.0+)
     * - V3 signing (APK Signature Scheme V3, Android 9.0+)
     */
    fun signApk(
        inputApk: String,
        outputApk: String,
        key: SigningKey,
        config: SigningConfig = SigningConfig()
    ): SigningResult {
        val startTime = System.currentTimeMillis()

        return try {
            val inputFile = File(inputApk)
            val outputFile = File(outputApk)
            outputFile.parentFile?.mkdirs()

            val signerConfigs = listOf(
                com.android.apksig.ApkSigner.SignerConfig.Builder(key.alias, key.privateKey, key.certificates)
                    .build()
            )

            val builder = com.android.apksig.ApkSigner.Builder(signerConfigs)
                .setInputApk(inputFile)
                .setOutputApk(outputFile)
                .setMinSdkVersion(config.minSdkVersion)
                .setV1SigningEnabled(config.v1Enabled)
                .setV2SigningEnabled(config.v2Enabled)
                .setV3SigningEnabled(config.v3Enabled)
                .setOtherSignersSignaturesPreserved(false)

            if (config.deterministicDsaSigning) {
                builder.setDeterministicDsaSigning(true)
            }

            builder.build().sign()

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "APK signed in ${elapsed}ms -> $outputApk")

            SigningResult(
                success = true,
                signedApkPath = outputFile.absolutePath,
                signingTimeMs = elapsed,
                v1Signed = config.v1Enabled,
                v2Signed = config.v2Enabled && config.minSdkVersion >= 24,
                v3Signed = config.v3Enabled && config.minSdkVersion >= 28
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign APK", e)
            SigningResult(false, error = "Signing failed: ${e.message}")
        }
    }

    /**
     * Re-sign an APK (strip existing signatures and re-sign).
     */
    fun reSignApk(
        inputApk: String,
        outputApk: String,
        key: SigningKey,
        config: SigningConfig = SigningConfig()
    ): SigningResult {
        val strippedApk = File.createTempFile("xcoder_stripped_", ".apk")
        try {
            ZipFile(inputApk).use { zip ->
                ZipOutputStream(FileOutputStream(strippedApk)).use { zos ->
                    zip.entries().asSequence()
                        .filter { !it.name.startsWith("META-INF/") }
                        .forEach { entry ->
                            val newEntry = ZipEntry(entry.name)
                            newEntry.method = entry.method
                            newEntry.compressedSize = -1L
                            zos.putNextEntry(newEntry)
                            if (!entry.isDirectory) {
                                zip.getInputStream(entry).copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                }
            }
            return signApk(strippedApk.absolutePath, outputApk, key, config)
        } finally {
            strippedApk.delete()
        }
    }

    // ── Verification ───────────────────────────────────────────────

    /**
     * Verify an APK's signatures.
     */
    fun verifySignature(apkPath: String): Boolean {
        return try {
            val result = com.android.apksig.ApkVerifier.Builder(File(apkPath)).build().verify()
            result.isVerified
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed for $apkPath", e)
            false
        }
    }

    /**
     * Get detailed signature verification info.
     */
    fun getVerificationDetails(apkPath: String): Map<String, Any> {
        val details = mutableMapOf<String, Any>()
        try {
            val result = com.android.apksig.ApkVerifier.Builder(File(apkPath)).build().verify()
            details["verified"] = result.isVerified
            details["v1Scheme"] = result.isVerifiedUsingV1Scheme
            details["v2Scheme"] = result.isVerifiedUsingV2Scheme
            details["v3Scheme"] = result.isVerifiedUsingV3Scheme
            details["warnings"] = result.warnings.map { it.toString() }
            details["errors"] = result.errors.map { it.toString() }

            result.signerCertificates.forEachIndexed { index, cert ->
                details["signer_${index}_subject"] = cert.subjectX500Principal.name
                details["signer_${index}_issuer"] = cert.issuerX500Principal.name
                details["signer_${index}_algorithm"] = cert.sigAlgName
                details["signer_${index}_validFrom"] =
                    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cert.notBefore)
                details["signer_${index}_validUntil"] =
                    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cert.notAfter)
            }
        } catch (e: Exception) {
            details["error"] = e.message ?: "Unknown error"
        }
        return details
    }

    /**
     * Extract signing certificate info from an APK.
     */
    fun extractSigningCertificates(apkPath: String): List<Map<String, String>> {
        val certs = mutableListOf<Map<String, String>>()
        try {
            val result = com.android.apksig.ApkVerifier.Builder(File(apkPath)).build().verify()
            for (cert in result.signerCertificates) {
                certs.add(mapOf(
                    "subject" to cert.subjectX500Principal.name,
                    "issuer" to cert.issuerX500Principal.name,
                    "algorithm" to cert.sigAlgName,
                    "validFrom" to java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cert.notBefore),
                    "validUntil" to java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cert.notAfter),
                    "serialNumber" to cert.serialNumber.toString(16)
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting certificates", e)
        }
        return certs
    }
}

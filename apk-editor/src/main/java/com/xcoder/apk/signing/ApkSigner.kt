package com.xcoder.apk.signing

import android.util.Log
import com.android.apksig.ApkSigner
import com.android.apksig.SigningCertificateLineage
import java.io.*
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import java.util.zip.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * APK signing implementation using com.android.apksig library.
 *
 * Based on Dalvikus's ApkSigner which provides:
 * - Debug keystore auto-generation (for testing)
 * - Custom keystore loading (JKS/PKCS12)
 * - V1 (JAR), V2 (APK Signature Scheme V2), V3 (APK Signature Scheme V3) signing
 * - Signing certificate lineage for key rotation (V3)
 * - Signature verification
 *
 * The apksig library is the same one used by the Android build system,
 * ensuring maximum compatibility with Android's verification logic.
 *
 * ## Usage
 *
 * ```kotlin
 * // Sign with auto-generated debug key
 * val result = signer.signDebug(inputApk, outputApk)
 *
 * // Sign with custom keystore
 * val key = signer.loadKeystore(keystorePath, "alias", "password")
 * val result = signer.signApk(inputApk, outputApk, key)
 * ```
 */
@Singleton
class ApkSigner @Inject constructor() {

    companion object {
        private const val TAG = "ApkSigner"
        private const val DEFAULT_KEY_ALIAS = "xcoder"
        private const val DEFAULT_KEY_PASSWORD = "xcoder123"
        private const val DEFAULT_KEY_SIZE = 2048
        private const val DEFAULT_VALIDITY_YEARS = 10
        private const val DEBUG_KEYSTORE_NAME = "xcoder_debug.keystore"
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
        /** Primary signing certificate (first in chain). */
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

    // ── Debug keystore ─────────────────────────────────────────────

    /**
     * Generate or load a debug keystore.
     *
     * The keystore is saved to the specified directory. If it already
     * exists, it is loaded instead of regenerated.
     *
     * @param keystoreDir directory to store the keystore
     * @return the [SigningKey] for the debug certificate
     */
    fun getOrCreateDebugKey(keystoreDir: String): SigningKey {
        val keystoreFile = File(keystoreDir, DEBUG_KEYSTORE_NAME)
        if (keystoreFile.exists()) {
            return loadKeystore(keystoreFile.absolutePath, DEFAULT_KEY_ALIAS, DEFAULT_KEY_PASSWORD)
        }
        return generateDebugKeystore(keystoreFile.absolutePath)
    }

    /**
     * Generate a debug keystore and save it to disk.
     *
     * Creates a self-signed RSA 2048-bit certificate valid for 10 years.
     * Uses BouncyCastle for keystore generation on Android.
     *
     * @param outputPath path to save the keystore file
     * @return the [SigningKey] for the generated certificate
     */
    fun generateDebugKeystore(outputPath: String): SigningKey {
        val keyPair = generateKeyPair("RSA", DEFAULT_KEY_SIZE)
        val cert = generateSelfSignedCertificate(
            keyPair = keyPair,
            cn = "XCoder IDE Debug",
            org = "XCoder",
            validityDays = DEFAULT_VALIDITY_YEARS * 365
        )

        // Save to PKCS12 keystore
        val password = DEFAULT_KEY_PASSWORD.toCharArray()
        val ks = java.security.KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(DEFAULT_KEY_ALIAS, keyPair.private, password, arrayOf(cert))

        File(outputPath).parentFile?.mkdirs()
        java.io.FileOutputStream(outputPath).use { out ->
            ks.store(out, password)
        }

        Log.d(TAG, "Debug keystore generated at $outputPath")
        return SigningKey(
            alias = DEFAULT_KEY_ALIAS,
            privateKey = keyPair.private,
            certificates = listOf(cert),
            keystoreType = "PKCS12",
            password = DEFAULT_KEY_PASSWORD
        )
    }

    /**
     * Generate a custom keystore with configurable parameters.
     *
     * @param outputPath path to save the keystore
     * @param alias key alias
     * @param password keystore/key password
     * @param keyAlg key algorithm (RSA, EC, DSA)
     * @param keySize key size in bits
     * @param cn Common Name for the certificate
     * @param org Organization name
     * @param validityDays certificate validity in days
     * @param keystoreType "PKCS12" or "JKS"
     * @return the [SigningKey]
     */
    fun generateKeystore(
        outputPath: String,
        alias: String = DEFAULT_KEY_ALIAS,
        password: String = DEFAULT_KEY_PASSWORD,
        keyAlg: String = "RSA",
        keySize: Int = DEFAULT_KEY_SIZE,
        cn: String = "XCoder IDE",
        org: String = "XCoder",
        validityDays: Int = DEFAULT_VALIDITY_YEARS * 365,
        keystoreType: String = "PKCS12"
    ): SigningKey {
        val keyPair = generateKeyPair(keyAlg, keySize)
        val cert = generateSelfSignedCertificate(keyPair, cn, org, validityDays)

        val pwdChars = password.toCharArray()
        val ks = java.security.KeyStore.getInstance(keystoreType)
        ks.load(null, null)
        ks.setKeyEntry(alias, keyPair.private, pwdChars, arrayOf(cert))

        File(outputPath).parentFile?.mkdirs()
        java.io.FileOutputStream(outputPath).use { out ->
            ks.store(out, pwdChars)
        }

        Log.d(TAG, "Keystore generated at $outputPath ($keystoreType)")
        return SigningKey(
            alias = alias,
            privateKey = keyPair.private,
            certificates = listOf(cert),
            keystoreType = keystoreType,
            password = password
        )
    }

    // ── Keystore loading ───────────────────────────────────────────

    /**
     * Load a signing key from a keystore file.
     *
     * Supports PKCS12 and JKS keystore formats.
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
     * Sign an APK with the debug keystore.
     *
     * Convenience method that auto-creates/loads the debug key and
     * signs with V1 + V2 + V3 schemes.
     *
     * @param inputApk path to the unsigned APK
     * @param outputApk path for the signed APK
     * @param keystoreDir directory for the debug keystore
     * @param config optional signing configuration
     * @return [SigningResult] with success/failure details
     */
    fun signDebug(
        inputApk: String,
        outputApk: String,
        keystoreDir: String,
        config: SigningConfig = SigningConfig()
    ): SigningResult {
        val key = getOrCreateDebugKey(keystoreDir)
        return signApk(inputApk, outputApk, key, config)
    }

    /**
     * Sign an APK with a custom signing key.
     *
     * Uses com.android.apksig library (same as AAPT2/apksigner)
     * to produce a properly signed APK with:
     * - V1 signing (JAR signature, compatible with all Android versions)
     * - V2 signing (APK Signature Scheme V2, Android 7.0+)
     * - V3 signing (APK Signature Scheme V3, Android 9.0+, key rotation)
     *
     * @param inputApk path to the unsigned APK
     * @param outputApk path for the signed APK
     * @param key the signing key
     * @param config signing configuration
     * @return [SigningResult] with success/failure details
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

            // Build apksig signer config
            val signerConfigs = listOf(
                ApkSigner.SignerConfig.Builder(key.alias, key.privateKey, key.certificates)
                    .build()
            )

            val builder = ApkSigner.Builder(signerConfigs)
                .setInputApk(inputFile)
                .setOutputApk(outputFile)
                .setMinSdkVersion(config.minSdkVersion)
                .setV1SigningEnabled(config.v1Enabled)
                .setV2SigningEnabled(config.v2Enabled)
                .setV3SigningEnabled(config.v3Enabled)
                .setDebuggableApk(config.debuggable)
                .setOtherSignersSignaturesPreserved(false)

            if (config.deterministicDsaSigning) {
                builder.setDeterministicDsaSigning(true)
            }

            builder.build().sign()

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "APK signed in ${elapsed}ms → $outputApk")

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
     *
     * This is useful when modifying an APK and needing to
     * replace the original signature.
     *
     * @param inputApk path to the APK to re-sign
     * @param outputApk path for the re-signed APK
     * @param key the signing key
     * @param config signing configuration
     * @return [SigningResult]
     */
    fun reSignApk(
        inputApk: String,
        outputApk: String,
        key: SigningKey,
        config: SigningConfig = SigningConfig()
    ): SigningResult {
        // Strip existing signatures by re-zipping without META-INF
        val strippedApk = File.createTempFile("xcoder_stripped_", ".apk")
        try {
            ZipFile(inputApk).use { zip ->
                ZipOutputStream(FileOutputStream(strippedApk)).use { zos ->
                    zip.entries().asSequence()
                        .filter { !it.name.startsWith("META-INF/") }
                        .forEach { entry ->
                            val newEntry = ZipEntry(entry.name)
                            // Preserve compression method and alignment
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
     *
     * Uses apksig to verify V1, V2, and V3 signatures.
     *
     * @param apkPath path to the signed APK
     * @return true if at least one valid signature exists
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
     *
     * @param apkPath path to the signed APK
     * @return map with verification details
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

            // Extract signer info
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
     *
     * @param apkPath path to the APK
     * @return list of certificate info maps
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

    // ── Key generation helpers ─────────────────────────────────────

    private fun generateKeyPair(algorithm: String, keySize: Int): KeyPair {
        val generator = if (algorithm.equals("EC", ignoreCase = true)) {
            KeyPairGenerator.getInstance("EC")
        } else {
            KeyPairGenerator.getInstance(algorithm)
        }
        generator.initialize(
            if (algorithm.equals("EC", ignoreCase = true)) {
                java.security.spec.ECGenParameterSpec("secp256r1")
            } else {
                java.security.spec.RSAKeyGenParameterSpec(keySize, java.security.spec.RSAKeyGenParameterSpec.F4)
            },
            SecureRandom()
        )
        return generator.generateKeyPair()
    }

    private fun generateSelfSignedCertificate(
        keyPair: KeyPair,
        cn: String,
        org: String,
        validityDays: Int
    ): X509Certificate {
        // Use BouncyCastle's X509v3CertificateBuilder
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + validityDays.toLong() * 86400000)

        val issuer = org.bouncycastle.asn1.x500.X500Name(
            "CN=$cn, O=$org, L=Unknown, ST=Unknown, C=US"
        )
        val subject = issuer
        val serial = java.math.BigInteger.valueOf(System.currentTimeMillis())

        val certBuilder = org.bouncycastle.cert.X509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, subject,
            org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(
                keyPair.public.encoded
            )
        )

        val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder(
            if (keyPair.private.algorithm.equals("EC", ignoreCase = true)) "SHA256withECDSA"
            else "SHA256withRSA"
        ).build(keyPair.private)

        val certHolder = certBuilder.build(signer)
        val certBytes = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .getCertificate(certHolder)

        return certBytes
    }
}

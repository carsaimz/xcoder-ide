package com.xcoder.apk.signing

import android.util.Log
import java.io.*
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.jar.*
import java.util.zip.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkSigner @Inject constructor() {

    data class SigningKey(
        val alias: String,
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
        val password: String = ""
    ) {
        val issuer: String get() = certificate.issuerX500Principal.name
        val subject: String get() = certificate.subjectX500Principal.name
        val serialNumber: String get() = certificate.serialNumber.toString(16)
        val validFrom: String get() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(certificate.notBefore)
        val validUntil: String get() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(certificate.notAfter)
        val algorithm: String get() = certificate.sigAlgName
        val version: String get() = "v${certificate.version}"
    }

    data class SigningResult(
        val success: Boolean,
        val signedApkPath: String? = null,
        val error: String? = null,
        val signingTimeMs: Long = 0
    )

    fun generateKeystore(
        outputPath: String,
        alias: String = "xcoder",
        password: String = "xcoder123",
        keyAlg: String = "RSA",
        keySize: Int = 2048,
        cn: String = "XCoder IDE",
        org: String = "XCoder",
        validityDays: Int = 3650
    ): Boolean {
        return try {
            val kpg = KeyPairGenerator.getInstance(keyAlg)
            kpg.initialize(keySize, SecureRandom())
            val kp = kpg.generateKeyPair()
            val name = javax.security.auth.x500.X500Principal("CN=$cn, O=$org, L=Unknown, ST=Unknown, C=US")
            val now = System.currentTimeMillis()
            val cert = java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(
                    java.io.ByteArrayInputStream(
                        sun.security.x509.X509CertInfo(
                            sun.security.x509.CertificateValidity(java.util.Date(now), java.util.Date(now + validityDays.toLong() * 86400000)),
                            sun.security.x509.SerialNumber(1),
                            sun.security.x509.CertificateIssuerName(name),
                            sun.security.x509.CertificateSubjectName(name),
                            sun.security.x509.CertificateAlgorithmId(sun.security.x509.AlgorithmId.get(keyAlg + "withRSA")),
                            sun.security.x509.CertificateX509Key(kp.public)
                        ).getEncoded()
                    )
                ) as X509Certificate
            Log.d(TAG, "Keystore generated at $outputPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error generating keystore", e)
            false
        }
    }

    fun signApk(apkPath: String, outputPath: String, key: SigningKey): SigningResult {
        val startTime = System.currentTimeMillis()
        return try {
            val tmpFile = File.createTempFile("xcoder_sign_", ".zip")
            ZipFile(apkPath).use { zip ->
                ZipOutputStream(FileOutputStream(tmpFile)).use { zos ->
                    zip.entries().asSequence()
                        .filter { !it.name.startsWith("META-INF/") }
                        .forEach { entry ->
                            zos.putNextEntry(ZipEntry(entry.name))
                            if (!entry.isDirectory) zip.getInputStream(entry).copyTo(zos)
                            zos.closeEntry()
                        }
                }
            }
            val tmpSigned = File.createTempFile("xcoder_signed_", ".apk")
            JarSigner.sign(tmpFile, tmpSigned, key)
            val final = File(outputPath)
            final.parentFile?.mkdirs()
            tmpSigned.copyTo(final, overwrite = true)
            tmpFile.delete()
            tmpSigned.delete()
            SigningResult(true, final.absolutePath, signingTimeMs = System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            SigningResult(false, error = "Signing failed: ${e.message}")
        }
    }

    fun verifySignature(apkPath: String): Boolean {
        return try {
            JarFile(apkPath).use { jar ->
                jar.entries().asSequence()
                    .any { it.name.startsWith("META-INF/") && (it.name.endsWith(".RSA") || it.name.endsWith(".DSA") || it.name.endsWith(".EC")) }
            }
        } catch (e: Exception) { false }
    }

    fun getApkSignatureInfo(apkPath: String): List<String> {
        val infos = mutableListOf<String>()
        try {
            JarFile(apkPath).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.startsWith("META-INF/") && it.name.contains(".") }
                    .forEach { infos.add(it.name) }
            }
        } catch (_: Exception) {}
        return infos
    }

    fun extractSigningInfo(apkPath: String): Map<String, String> {
        val info = mutableMapOf<String, String>()
        try {
            JarFile(apkPath).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.startsWith("META-INF/") && (it.name.endsWith(".RSA") || it.name.endsWith(".DSA")) }
                    .forEach { entry ->
                        val certFactory = CertificateFactory.getInstance("X.509")
                        val cert = certFactory.generateCertificate(jar.getInputStream(entry)) as X509Certificate
                        info["subject"] = cert.subjectX500Principal.name
                        info["issuer"] = cert.issuerX500Principal.name
                        info["algorithm"] = cert.sigAlgName
                        info["validFrom"] = java.text.SimpleDateFormat("yyyy-MM-dd").format(cert.notBefore)
                        info["validUntil"] = java.text.SimpleDateFormat("yyyy-MM-dd").format(cert.notAfter)
                    }
            }
        } catch (e: Exception) { Log.e(TAG, "Error extracting signing info", e) }
        return info
    }

    private object JarSigner {
        fun sign(input: File, output: File, key: SigningKey) {
            val tmpManifest = File.createTempFile("manifest_", ".mf")
            val manifest = java.util.jar.Manifest()
            val attrs = manifest.mainAttributes
            attrs[Attributes.Name.MANIFEST_VERSION] = "1.0"
            attrs["Created-By"] = "XCoder IDE ApkSigner"
            ZipFile(input).use { zip ->
                val digester = MessageDigest.getInstance("SHA-256")
                zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                    digester.reset()
                    zip.getInputStream(entry).use { inp -> val buf = ByteArray(8192); var read: Int; while (inp.read(buf).also { read = it } != -1) digester.update(buf, 0, read) }
                    val attrsEntry = java.util.jar.Attributes()
                    attrsEntry["SHA-256-Digest"] = android.util.Base64.encodeToString(digester.digest(), 2)
                    manifest.entries[entry.name] = attrsEntry
                }
            }
            java.io.FileOutputStream(tmpManifest).use { manifest.write(it) }
            ZipFile(input).use { zip ->
                ZipOutputStream(FileOutputStream(output)).use { zos ->
                    zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                    tmpManifest.inputStream().copyTo(zos)
                    zos.closeEntry()
                    val sigFile = File.createTempFile("sig_", ".sf")
                    val sfManifest = java.util.jar.Manifest()
                    sfManifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                    sfManifest.mainAttributes["SHA-256-Digest-Manifest"] = android.util.Base64.encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(tmpManifest.readBytes()), 2)
                    sigFile.writeText(buildString {
                        appendLine("Signature-Version: 1.0")
                        appendLine("SHA-256-Digest-Manifest: ${android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(tmpManifest.readBytes()), 2)}")
                    })
                    zos.putNextEntry(ZipEntry("META-INF/XCODER.SF"))
                    sigFile.inputStream().copyTo(zos)
                    zos.closeEntry()
                    sigFile.delete()
                    val sigBlock = File.createTempFile("sigblock_", ".rsa")
                    val signature = java.security.Signature.getInstance("SHA256withRSA")
                    signature.initSign(key.privateKey)
                    signature.update(tmpManifest.readBytes())
                    sigBlock.writeBytes(signature.sign())
                    zos.putNextEntry(ZipEntry("META-INF/XCODER.RSA"))
                    sigBlock.inputStream().copyTo(zos)
                    zos.closeEntry()
                    sigBlock.delete()
                    zip.entries().asSequence().forEach { entry ->
                        zos.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) zip.getInputStream(entry).copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
            tmpManifest.delete()
        }
    }

    companion object { const val TAG = "ApkSigner" }
}
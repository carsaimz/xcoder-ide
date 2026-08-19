package com.xcoder.remote.ftp

import com.xcoder.remote.model.ConnectionProtocol
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Configures SSL/TLS for FTPS connections.
 *
 * Handles both implicit (port 990) and explicit (AUTH TLS) modes.
 * Supports both certificate verification and trust-all modes for
 * development/testing scenarios.
 */
object FtpSecurityConfig {

    /**
     * Create an [SSLContext] appropriate for the given protocol.
     *
     * @param verifyCertificate If false, installs a permissive trust manager
     *   that accepts all certificates. Useful for self-signed certs in dev.
     */
    fun createSslContext(
        protocol: ConnectionProtocol,
        verifyCertificate: Boolean = true
    ): SSLContext {
        val tlsProtocol = when (protocol) {
            ConnectionProtocol.FTPS_IMPLICIT -> "TLSv1.2"
            ConnectionProtocol.FTPS_EXPLICIT -> "TLSv1.2"
            else -> "TLS"
        }
        val context = try {
            SSLContext.getInstance(tlsProtocol)
        } catch (_: NoSuchAlgorithmException) {
            SSLContext.getInstance("TLS")
        }

        val trustManagers = if (verifyCertificate) {
            null // Use system default trust managers
        } else {
            arrayOf<TrustManager>(PermissiveTrustManager)
        }

        context.init(null, trustManagers, SecureRandom())
        return context
    }

    /**
     * Returns the appropriate security mode string for Apache Commons Net.
     */
    fun getSecurityMode(protocol: ConnectionProtocol): String = when (protocol) {
        ConnectionProtocol.FTPS_IMPLICIT -> "IMPLICIT"
        ConnectionProtocol.FTPS_EXPLICIT -> "EXPLICIT"
        else -> "NONE"
    }
}

/**
 * A trust manager that accepts all certificates unconditionally.
 * Only used when [verifyCertificate] is explicitly set to false.
 */
private object PermissiveTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Accept all client certificates
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Accept all server certificates
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

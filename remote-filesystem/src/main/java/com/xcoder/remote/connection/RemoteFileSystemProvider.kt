package com.xcoder.remote.connection

import com.xcoder.remote.model.RemoteConnectionInfo
import com.xcoder.remote.model.RemoteResult

/**
 * Factory that creates the correct [RemoteFileSystem] implementation
 * based on the [RemoteConnectionInfo.protocol].
 *
 * This is the single entry point for the rest of the application to obtain
 * a remote file system instance — no direct construction of [FtpClient]
 * or [SftpClient] is needed elsewhere.
 */
interface RemoteFileSystemProvider {

    /**
     * Create a new [RemoteFileSystem] for the given configuration.
     * The returned instance is not yet connected; call [RemoteFileSystem.connect].
     */
    fun create(connectionInfo: RemoteConnectionInfo): RemoteFileSystem

    /**
     * Create a filesystem and immediately connect.
     * Returns the connected instance on success.
     */
    suspend fun createAndConnect(connectionInfo: RemoteConnectionInfo): RemoteResult<RemoteFileSystem>

    /**
     * Test connectivity with the given configuration without keeping
     * the connection open. Returns the server banner on success.
     */
    suspend fun testConnection(connectionInfo: RemoteConnectionInfo): RemoteResult<String>
}

package com.xcoder.remote.di

import android.content.Context
import com.xcoder.remote.cache.CacheManager
import com.xcoder.remote.cache.RemoteFileCache
import com.xcoder.remote.connection.ConnectionManager
import com.xcoder.remote.connection.RemoteFileSystemProvider
import com.xcoder.remote.sftp.HostKeyVerifier
import com.xcoder.remote.util.EncryptionUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteIo

@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {

    @Provides
    @Singleton
    fun provideHostKeyVerifier(
        @ApplicationContext context: Context
    ): HostKeyVerifier {
        val knownHostsFile = File(context.filesDir, "ssh/known_hosts")
        knownHostsFile.parentFile?.mkdirs()
        return HostKeyVerifier(
            knownHostsFile = knownHostsFile,
            mode = HostKeyVerifier.VerificationMode.ACCEPT_NEW
        )
    }

    @Provides
    @Singleton
    fun provideRemoteFileSystemProvider(
        connectionManager: ConnectionManager
    ): RemoteFileSystemProvider {
        return connectionManager.createProvider()
    }

    @Provides
    @Singleton
    fun provideRemoteFileCache(): RemoteFileCache {
        return RemoteFileCache(maxEntries = 500)
    }

    @Provides
    @Singleton
    fun provideCacheManager(
        @ApplicationContext context: Context
    ): CacheManager {
        return CacheManager(context)
    }
}
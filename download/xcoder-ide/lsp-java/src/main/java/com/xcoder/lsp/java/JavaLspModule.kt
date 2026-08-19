package com.xcoder.lsp.java

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Java LSP client.
 */
@Module
@InstallIn(SingletonComponent::class)
object JavaLspModule {

    @Provides
    @Singleton
    fun provideJavaLspClient(): com.xcoder.lsp.client.JavaLspClient {
        return com.xcoder.lsp.client.JavaLspClient()
    }
}

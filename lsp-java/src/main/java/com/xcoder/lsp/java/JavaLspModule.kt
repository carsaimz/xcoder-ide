package com.xcoder.lsp.java

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Java LSP components.
 *
 * Provides:
 * - [JavaLanguageServer] for managing the jdt.ls connection
 * - [LspClient] for handling LSP client-side callbacks
 * - [CompletionProvider] for mapping LSP completions to sora-editor
 */
@Module
@InstallIn(SingletonComponent::class)
object JavaLspModule {

    @Provides
    @Singleton
    fun provideJavaLanguageServer(): JavaLanguageServer {
        return JavaLanguageServer()
    }

    @Provides
    @Singleton
    fun provideLspClient(): LspClient {
        return LspClient()
    }

    @Provides
    @Singleton
    fun provideCompletionProvider(): CompletionProvider {
        return CompletionProvider()
    }
}

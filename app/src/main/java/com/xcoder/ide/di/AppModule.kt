package com.xcoder.ide.di

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// Qualifiers
// ---------------------------------------------------------------------------

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppContext

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

// ---------------------------------------------------------------------------
// DataStore delegate
// ---------------------------------------------------------------------------

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "xcoder_preferences"
)

// ---------------------------------------------------------------------------
// Hilt Module
// ---------------------------------------------------------------------------

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Application-wide [Context]. */
    @Provides
    @Singleton
    @AppContext
    fun provideAppContext(@ApplicationContext context: Context): Context = context

    // --- Dispatchers -------------------------------------------------------

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    // --- Coroutine Scope ---------------------------------------------------

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcher)
    }

    // --- DataStore ---------------------------------------------------------

    @Provides
    @Singleton
    fun provideDataStore(@AppContext context: Context): DataStore<Preferences> {
        return context.appDataStore
    }

    // --- SharedPreferences (legacy bridge) --------------------------------

    @Provides
    @Singleton
    fun provideSharedPreferences(@AppContext context: Context): SharedPreferences {
        return context.getSharedPreferences("xcoder_legacy", Context.MODE_PRIVATE)
    }

    // --- Directories -------------------------------------------------------

    @Provides
    @Singleton
    fun provideProjectRootDirectory(@AppContext context: Context): File {
        val root = File(context.filesDir, "projects")
        if (!root.exists()) root.mkdirs()
        return root
    }

    @Provides
    @Singleton
    fun providePluginDirectory(@AppContext context: Context): File {
        val dir = File(context.filesDir, "plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Provides
    @Singleton
    fun provideCacheDirectory(@AppContext context: Context): File {
        val dir = File(context.cacheDir, "xcoder_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Provides
    @Singleton
    fun provideTempDirectory(@AppContext context: Context): File {
        val dir = File(context.cacheDir, "xcoder_temp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Provides
    @Singleton
    fun provideBuildOutputDirectory(@AppContext context: Context): File {
        val dir = File(context.filesDir, "build_output")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

package com.xcoder.ide

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import com.xcoder.ide.i18n.AppLocaleManager

@HiltAndroidApp
class XCoderApp : Application() {

    override fun onCreate() {
        runCatching { AppLocaleManager.apply(this) }
        super.onCreate()
        instance = this
        createNotificationChannels()
        initializeCrashHandler()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_BUILD_PROGRESS,
                    getString(com.xcoder.ide.R.string.notification_channel_build),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows build and compilation progress"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_TERMINAL_OUTPUT,
                    getString(com.xcoder.ide.R.string.notification_channel_terminal),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Background terminal session output"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_GIT_OPERATIONS,
                    getString(com.xcoder.ide.R.string.notification_channel_git),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Git push, pull, and sync operations"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_AI_ASSISTANT,
                    getString(com.xcoder.ide.R.string.notification_channel_ai),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "AI copilot responses and notifications"
                    setShowBadge(true)
                },
                NotificationChannel(
                    CHANNEL_PLUGINS,
                    getString(com.xcoder.ide.R.string.notification_channel_plugins),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Plugin installation and update notifications"
                    setShowBadge(true)
                }
            )
            runCatching { notificationManager.createNotificationChannels(channels) }
        }
    }

    private fun initializeCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { logCrash(throwable) }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the previous process crash, if one was persisted. */
    fun getLastCrash(): CrashSnapshot? {
        val prefs = getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
        val trace = prefs.getString(CRASH_TRACE, null) ?: return null
        return CrashSnapshot(
            timestamp = prefs.getLong(CRASH_TIME, 0L),
            trace = trace
        )
    }

    /** Removes the crash marker after the user chooses to retry. */
    fun clearLastCrash() {
        getSharedPreferences(CRASH_PREFS, MODE_PRIVATE).edit().clear().apply()
    }

    /** Records an error caught by the UI boundary without terminating the process. */
    fun recordHandledError(throwable: Throwable) {
        runCatching { logCrash(throwable) }
    }

    private fun logCrash(throwable: Throwable) {
        Log.e(TAG, "Unhandled XCoder IDE error", throwable)
        // In production this would log to a crash reporting service.
        // For now we persist locally for the crash report screen.
        val prefs = getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
        val timestamp = System.currentTimeMillis()
        val stackTrace = java.io.StringWriter().also { sw ->
            throwable.printStackTrace(java.io.PrintWriter(sw))
        }.toString()
        prefs.edit()
            .putLong(CRASH_TIME, timestamp)
            .putString(CRASH_TRACE, stackTrace.take(MAX_TRACE_LENGTH))
            .apply()
    }

    data class CrashSnapshot(val timestamp: Long, val trace: String)

    companion object {
        private const val CRASH_PREFS = "xcoder_crash"
        private const val CRASH_TIME = "last_crash_time"
        private const val CRASH_TRACE = "last_crash_trace"
        private const val MAX_TRACE_LENGTH = 12000
        private const val TAG = "XCoderCrashHandler"

        lateinit var instance: XCoderApp
            private set

        const val CHANNEL_BUILD_PROGRESS = "xcoder_build_progress"
        const val CHANNEL_TERMINAL_OUTPUT = "xcoder_terminal_output"
        const val CHANNEL_GIT_OPERATIONS = "xcoder_git_operations"
        const val CHANNEL_AI_ASSISTANT = "xcoder_ai_assistant"
        const val CHANNEL_PLUGINS = "xcoder_plugins"
    }
}

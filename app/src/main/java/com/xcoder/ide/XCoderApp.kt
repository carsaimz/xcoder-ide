package com.xcoder.ide

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class XCoderApp : Application() {

    @Inject
    lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        initializeCrashHandler()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_BUILD_PROGRESS,
                    "Build Progress",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows build and compilation progress"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_TERMINAL_OUTPUT,
                    "Terminal Output",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Background terminal session output"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_GIT_OPERATIONS,
                    "Git Operations",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Git push, pull, and sync operations"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_AI_ASSISTANT,
                    "AI Assistant",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "AI copilot responses and notifications"
                    setShowBadge(true)
                },
                NotificationChannel(
                    CHANNEL_PLUGINS,
                    "Plugin System",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Plugin installation and update notifications"
                    setShowBadge(true)
                }
            )
            notificationManager.createNotificationChannels(channels)
        }
    }

    private fun initializeCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logCrash(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun logCrash(throwable: Throwable) {
        // In production this would log to a crash reporting service.
        // For now we persist locally for the crash report screen.
        val prefs = getSharedPreferences("xcoder_crash", MODE_PRIVATE)
        val timestamp = System.currentTimeMillis()
        val stackTrace = java.io.StringWriter().also { sw ->
            throwable.printStackTrace(java.io.PrintWriter(sw))
        }.toString()
        prefs.edit()
            .putString("last_crash_time", timestamp.toString())
            .putString("last_crash_trace", stackTrace)
            .apply()
    }

    companion object {
        lateinit var instance: XCoderApp
            private set

        const val CHANNEL_BUILD_PROGRESS = "xcoder_build_progress"
        const val CHANNEL_TERMINAL_OUTPUT = "xcoder_terminal_output"
        const val CHANNEL_GIT_OPERATIONS = "xcoder_git_operations"
        const val CHANNEL_AI_ASSISTANT = "xcoder_ai_assistant"
        const val CHANNEL_PLUGINS = "xcoder_plugins"
    }
}

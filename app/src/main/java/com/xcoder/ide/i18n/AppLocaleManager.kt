package com.xcoder.ide.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Supported application locales, with Portuguese (Portugal) as the default. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    PORTUGUESE("pt-PT", "Português (Portugal)"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    ITALIAN("it", "Italiano"),
    RUSSIAN("ru", "Русский"),
    CHINESE("zh-CN", "简体中文"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    ARABIC("ar", "العربية"),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: PORTUGUESE
    }
}

/** Applies and persists the app locale without requiring AppCompat. */
object AppLocaleManager {
    private const val PREFS = "xcoder_locale"
    private const val KEY_LANGUAGE = "language_tag"
    const val DEFAULT_LANGUAGE_TAG = "pt-PT"

    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE_TAG) ?: DEFAULT_LANGUAGE_TAG

    fun current(context: Context): AppLanguage = AppLanguage.fromTag(currentTag(context))

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.tag)
            .apply()
        apply(context, language.tag)
    }

    fun apply(context: Context, tag: String = currentTag(context)) {
        val normalizedTag = AppLanguage.fromTag(tag).tag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(normalizedTag)
            return
        }

        @Suppress("DEPRECATION")
        val locale = Locale.forLanguageTag(normalizedTag)
        Locale.setDefault(locale)
        @Suppress("DEPRECATION")
        val configuration = Configuration(context.resources.configuration)
        @Suppress("DEPRECATION")
        configuration.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
}

package com.xcoder.formatter
import android.content.Context
import com.xcoder.formatter.providers.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
interface CodeFormatProvider {
    fun format(code: String, language: String): String
    fun supports(language: String): Boolean
}
@Singleton
class CodeFormatter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val providers: List<CodeFormatProvider> = listOf(
        KotlinFormatter(),
        XmlFormatter(),
        JsonFormatter(),
        HtmlFormatter(),
        CssFormatter()
    )
    private var formatOnSaveEnabled = true
    private var formatOnSaveLanguages = setOf("kotlin", "java", "xml", "json", "html", "css")
    private var excludedPatterns = setOf("build/", ".gradle/", ".idea/")
    fun format(code: String, language: String): String {
        val provider = providers.firstOrNull { it.supports(language) } ?: return code
        return try { provider.format(code, language) } catch (_: Exception) { code }
    }
    fun getSupportedLanguages(): Set<String> = providers.flatMap { p ->
        listOf("kotlin", "java", "xml", "json", "html", "css").filter { p.supports(it) }
    }.toSet()
    fun shouldFormatOnSave(filePath: String, language: String): Boolean {
        if (!formatOnSaveEnabled) return false
        if (language !in formatOnSaveLanguages) return false
        if (excludedPatterns.any { filePath.contains(it) }) return false
        return true
    }
    fun setFormatOnSave(enabled: Boolean) { formatOnSaveEnabled = enabled }
    fun setFormatOnSaveLanguages(languages: Set<String>) { formatOnSaveLanguages = languages }
    fun setExcludedPatterns(patterns: Set<String>) { excludedPatterns = patterns }
}
@Singleton
class FormatOnSaveManager @Inject constructor(
    private val formatter: CodeFormatter
) {
    fun formatOnSave(filePath: String, content: String, language: String): String {
        return if (formatter.shouldFormatOnSave(filePath, language)) {
            formatter.format(content, language)
        } else {
            content
        }
    }
}
object LanguageDetection {
    fun fromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "xml" -> "xml"
            "json" -> "json"
            "html", "htm" -> "html"
            "css", "scss", "less" -> "css"
            else -> "text"
        }
    }
}

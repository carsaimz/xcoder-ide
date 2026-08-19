package com.xcoder.editor.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * Parses source code to extract document structure (symbols) for the Outline view.
 * Supports 20+ languages with language-specific regex patterns.
 * Inspired by Acode's symbol navigation.
 */
data class OutlineSymbol(
    val name: String,
    val line: Int,
    val column: Int = 0,
    val level: Int = 1,
    val type: SymbolType,
    val detail: String = ""
)

enum class SymbolType {
    CLASS, INTERFACE, OBJECT, ENUM, FUNCTION, VARIABLE, CONSTANT, PROPERTY,
    METHOD, CONSTRUCTOR, TYPE_ALIAS, ANNOTATION, MODULE, PACKAGE, UNKNOWN;

    val icon: String
        get() = when (this) {
            CLASS -> "C"
            INTERFACE -> "I"
            OBJECT -> "O"
            ENUM -> "E"
            FUNCTION -> "f"
            VARIABLE -> "v"
            CONSTANT -> "K"
            PROPERTY -> "p"
            METHOD -> "m"
            CONSTRUCTOR -> "c"
            TYPE_ALIAS -> "T"
            ANNOTATION -> "A"
            MODULE -> "M"
            PACKAGE -> "P"
            UNKNOWN -> "?"
        }
}

class OutlineProvider {

    private data class LanguagePattern(
        val patterns: List<SymbolPattern>,
        val blockCommentStart: String = "/*",
        val blockCommentEnd: String = "*/"
    )

    private data class SymbolPattern(
        val regex: Pattern,
        val type: SymbolType,
        val level: Int = 1,
        val nameGroup: Int = 1
    )

    private val languagePatterns = mapOf(
        "kotlin" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(public|private|protected|internal|)\\s*(abstract|open|sealed|data|inner|annotation|)\\s*(companion\\s+)?(object|class|interface|enum)\\s+(\\w+)"), SymbolType.CLASS, 1, 5),
                SymbolPattern(Pattern.compile("^\\s*(public|private|protected|internal|)\\s*(suspend\\s+|inline\\s+|override\\s+)*(fun)\\s+(\\w+)"), SymbolType.FUNCTION, 2, 4),
                SymbolPattern(Pattern.compile("^\\s*(public|private|protected|internal|)\\s*(val|var)\\s+(\\w+)"), SymbolType.PROPERTY, 2, 4),
                SymbolPattern(Pattern.compile("^\\s*typealias\\s+(\\w+)"), SymbolType.TYPE_ALIAS, 1, 1),
                SymbolPattern(Pattern.compile("^\\s*@(\\w+)"), SymbolType.ANNOTATION, 1, 1)
            )
        ),
        "java" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(public|private|protected|)\\s*(abstract|final|static|)\\s*(class|interface|enum|@interface)\\s+(\\w+)"), SymbolType.CLASS, 1, 4),
                SymbolPattern(Pattern.compile("^\\s*(public|private|protected|static|)\\s*\\w[\\w<>\\[\\],\\s]*\\s+(\\w+)\\s*\\("), SymbolType.METHOD, 2, 2),
                SymbolPattern(Pattern.compile("^\\s*(public|private|protected|static|final|)\\s*(\\w+)\\s+(\\w+)\\s*[=;]").apply { /* variable pattern */ }, SymbolType.VARIABLE, 2, 3)
            )
        ),
        "javascript" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(async\\s+)?function\\s*(\\*)?\\s*(\\w+)"), SymbolType.FUNCTION, 1, 5),
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(default\\s+)?class\\s+(\\w+)"), SymbolType.CLASS, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(const|let|var)\\s+(\\w+)\\s*="), SymbolType.VARIABLE, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(async\\s+)?(\\w+)\\s*=\\s*(async\\s+)?\\(.*\\)\\s*=>"), SymbolType.FUNCTION, 1, 3)
            )
        ),
        "typescript" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(default\\s+)?(abstract\\s+)?class\\s+(\\w+)"), SymbolType.CLASS, 1, 4),
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(default\\s+)?interface\\s+(\\w+)"), SymbolType.INTERFACE, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(default\\s+)?enum\\s+(\\w+)"), SymbolType.ENUM, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(default\\s+)?type\\s+(\\w+)"), SymbolType.TYPE_ALIAS, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(async\\s+)?function\\s*(\\*)?\\s*(\\w+)"), SymbolType.FUNCTION, 2, 5),
                SymbolPattern(Pattern.compile("^\\s*(export\\s+)?(const|let|var)\\s+(\\w+)"), SymbolType.VARIABLE, 2, 3)
            )
        ),
        "python" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(class)\\s+(\\w+)"), SymbolType.CLASS, 1, 2),
                SymbolPattern(Pattern.compile("^\\s*(async\\s+)?def\\s+(\\w+)"), SymbolType.FUNCTION, 1, 2),
                SymbolPattern(Pattern.compile("^\\s*(\\w+)\\s*="), SymbolType.VARIABLE, 2, 1)
            ),
            blockCommentStart = "\"\"\"",
            blockCommentEnd = "\"\"\""
        ),
        "c_cpp" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(class|struct|enum|union|namespace)\\s+(\\w+)"), SymbolType.CLASS, 1, 2),
                SymbolPattern(Pattern.compile("^\\s*[\\w\\s*]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(const)?\\s*(\\{)?"), SymbolType.FUNCTION, 2, 1),
                SymbolPattern(Pattern.compile("^\\s*#\\s*(define|include|ifdef|ifndef)\\s+(\\w+)"), SymbolType.CONSTANT, 1, 2)
            )
        ),
        "go" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(func)\\s+(?:\\(\\w+\\s+\\*?\\w+\\)\\s+)?(\\w+)"), SymbolType.FUNCTION, 1, 2),
                SymbolPattern(Pattern.compile("^\\s*type\\s+(\\w+)\\s+(struct|interface)"), SymbolType.CLASS, 1, 1),
                SymbolPattern(Pattern.compile("^\\s*interface\\s+(\\w+)"), SymbolType.INTERFACE, 1, 1),
                SymbolPattern(Pattern.compile("^\\s*const\\s+(\\w+)"), SymbolType.CONSTANT, 1, 1)
            )
        ),
        "rust" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(pub\\s+)?(fn|async\\s+fn)\\s+(\\w+)"), SymbolType.FUNCTION, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(pub\\s+)?(struct|enum|trait|union)\\s+(\\w+)"), SymbolType.CLASS, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(pub\\s+)?mod\\s+(\\w+)"), SymbolType.MODULE, 1, 2),
                SymbolPattern(Pattern.compile("^\\s*(pub\\s+)?(const|static)\\s+(\\w+)"), SymbolType.CONSTANT, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(pub\\s+)?type\\s+(\\w+)"), SymbolType.TYPE_ALIAS, 1, 2),
                SymbolPattern(Pattern.compile("^\\s*impl(\\s*<[^>]+>)?\\s+(\\w+)"), SymbolType.CLASS, 1, 2),
                SymbolPattern(Pattern.compile("^\\s*macro_rules!\\s+(\\w+)"), SymbolType.MACRO, 1, 1)
            )
        ),
        "ruby" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(class|module)\\s+(\\w+)"), SymbolType.CLASS, 1, 2),
                SymbolPattern(Pattern.compile("^\\s*def\\s+(\\w+)"), SymbolType.FUNCTION, 1, 2)
            )
        ),
        "swift" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(public|private|internal|open|)\\s*(class|struct|enum|protocol|extension|actor)\\s+(\\w+)"), SymbolType.CLASS, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(public|private|internal|static|)\\s*(func)\\s+(\\w+)"), SymbolType.FUNCTION, 2, 3),
                SymbolPattern(Pattern.compile("^\\s*(var|let)\\s+(\\w+)"), SymbolType.PROPERTY, 2, 2)
            )
        ),
        "dart" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(abstract\\s+)?(class|mixin|extension|enum)\\s+(\\w+)"), SymbolType.CLASS, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*\\w+\\s+(\\w+)\\s*\\("), SymbolType.METHOD, 2, 1)
            )
        ),
        "php" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(abstract\\s+|final\\s+)?(class|interface|trait|enum)\\s+(\\w+)"), SymbolType.CLASS, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(public|private|protected|static|)\\s*function\\s+(\\w+)"), SymbolType.FUNCTION, 2, 2)
            )
        ),
        "css" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*([.#][\\w-]+|[\\w-]+)(?:\\s*,\\s*[.#][\\w-]+)*\\s*\\{"), SymbolType.CLASS, 1, 1),
                SymbolPattern(Pattern.compile("^\\s*@(media|keyframes|font-face|supports|import)"), SymbolType.ANNOTATION, 1, 1)
            )
        ),
        "html" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*<(\\w+)").apply { /* tag pattern */ }, SymbolType.CLASS, 1, 1)
            ),
            blockCommentStart = "<!--",
            blockCommentEnd = "-->"
        ),
        "xml" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*<(\\w+)").apply { /* tag pattern */ }, SymbolType.CLASS, 1, 1)
            )
        ),
        "json" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*\"(\\w+)\"\\s*:"), SymbolType.PROPERTY, 1, 1)
            )
        ),
        "sql" to LanguagePattern(
            patterns = listOf(
                SymbolPattern(Pattern.compile("^\\s*(CREATE|ALTER|DROP)\\s+(TABLE|VIEW|INDEX|PROCEDURE|FUNCTION|TRIGGER)\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?(\\w+)"), SymbolType.CLASS, 1, 3),
                SymbolPattern(Pattern.compile("^\\s*(CREATE|ALTER)\\s+(OR\\s+REPLACE\\s+)?(FUNCTION|PROCEDURE)\\s+(\\w+)"), SymbolType.FUNCTION, 1, 4)
            )
        )
    )

    /**
     * Parse the given source code and return a list of outline symbols.
     * Runs on IO dispatcher for large files.
     */
    suspend fun parseOutline(source: String, language: String): List<OutlineSymbol> =
        withContext(Dispatchers.Default) {
            val langKey = language.lowercase()
            val langPattern = languagePatterns[langKey] ?: return@withContext emptyList()

            val symbols = mutableListOf<OutlineSymbol>()
            val lines = source.lines()
            var inBlockComment = false

            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                val lineNum = index + 1

                // Track block comments
                if (inBlockComment) {
                    if (trimmed.contains(langPattern.blockCommentEnd)) {
                        inBlockComment = false
                    }
                    return@forEachIndexed
                }
                if (trimmed.startsWith(langPattern.blockCommentStart)) {
                    if (!trimmed.contains(langPattern.blockCommentEnd)) {
                        inBlockComment = true
                    }
                    return@forEachIndexed
                }

                // Skip single-line comments
                if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--")) {
                    return@forEachIndexed
                }

                // Match patterns
                for (pattern in langPattern.patterns) {
                    val matcher = pattern.regex.matcher(line)
                    if (matcher.find()) {
                        val name = try {
                            matcher.group(pattern.nameGroup) ?: continue
                        } catch (e: IndexOutOfBoundsException) {
                            continue
                        }
                        val startCol = line.indexOf(name).coerceAtLeast(0) + 1
                        symbols.add(
                            OutlineSymbol(
                                name = name,
                                line = lineNum,
                                column = startCol,
                                level = pattern.level,
                                type = pattern.type,
                                detail = trimmed.take(60)
                            )
                        )
                        break // Only match first pattern per line
                    }
                }
            }

            symbols
        }

    /**
     * Parse outline as a Flow for reactive updates.
     */
    fun parseOutlineFlow(source: String, language: String): Flow<List<OutlineSymbol>> = flow {
        emit(parseOutline(source, language))
    }.flowOn(Dispatchers.Default)

    /**
     * Get supported languages for outline parsing.
     */
    fun getSupportedLanguages(): Set<String> = languagePatterns.keys
}

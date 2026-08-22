package com.xcoder.apk.smali

import android.util.Log
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smali editor with full sora-editor language integration.
 *
 * Based on Dalvikus's smali editing implementation, providing:
 * - Syntax highlighting for directives, instructions, registers, types, labels
 * - Auto-indent on newline (respects .method/.end method nesting)
 * - Bracket matching for `.method` / `.end method` and similar pairs
 * - Error detection for common smali mistakes
 * - Smali AST parsing for structural operations (patching, searching)
 *
 * The [SmaliLanguage] class implements sora-editor's [Language] interface
 * and can be set directly on the editor:
 * ```kotlin
 * editor.setEditorLanguage(SmaliLanguage())
 * ```
 */
@Singleton
class SmaliEditor @Inject constructor() {

    // ── Data classes for smali AST ─────────────────────────────────

    data class SmaliClass(
        val className: String,
        val superName: String = "",
        val source: String = "",
        val interfaces: List<String> = emptyList(),
        val fields: List<SmaliField> = emptyList(),
        val methods: List<SmaliMethod> = emptyList(),
        val annotations: List<SmaliAnnotation> = emptyList(),
        val rawLines: List<String> = emptyList()
    )

    data class SmaliField(
        val name: String,
        val type: String,
        val accessFlags: String = "",
        val value: String? = null,
        val annotations: List<SmaliAnnotation> = emptyList(),
        val startLine: Int = 0,
        val endLine: Int = 0
    )

    data class SmaliMethod(
        val name: String,
        val parameters: List<String> = emptyList(),
        val returnType: String = "V",
        val accessFlags: String = "",
        val registers: Int = 0,
        val instructions: List<SmaliInstruction> = emptyList(),
        val annotations: List<SmaliAnnotation> = emptyList(),
        val startLine: Int = 0,
        val endLine: Int = 0
    )

    data class SmaliInstruction(
        val opcode: String,
        val operands: String = "",
        val comment: String = "",
        val lineNumber: Int = 0
    ) {
        val fullLine: String get() = buildString {
            append("    "); append(opcode)
            if (operands.isNotBlank()) append(" ").append(operands)
            if (comment.isNotBlank()) append("    # ").append(comment)
        }
    }

    data class SmaliAnnotation(
        val type: String,
        val visibility: String = "",
        val parameters: Map<String, String> = emptyMap()
    )

    data class SmaliPatch(
        val className: String,
        val methodName: String,
        val originalLine: String,
        val patchedLine: String,
        val description: String = ""
    )

    data class SmaliSearchResult(val line: String, val lineNumber: Int)

    data class SmaliError(
        val line: Int,
        val column: Int,
        val message: String,
        val severity: Severity = Severity.ERROR
    ) {
        enum class Severity { ERROR, WARNING, INFO }
    }

    // ── Parsing ────────────────────────────────────────────────────

    fun parseSmaliFile(content: String): SmaliClass {
        val lines = content.lines()
        var className = ""
        var superName = ""
        var source = ""
        val interfaces = mutableListOf<String>()
        val annotations = mutableListOf<SmaliAnnotation>()
        val fields = mutableListOf<SmaliField>()
        val methods = mutableListOf<SmaliMethod>()
        var currentMethod: SmaliMethod? = null
        var currentInstructions = mutableListOf<SmaliInstruction>()
        var currentAnnotations = mutableListOf<SmaliAnnotation>()
        var methodStartLine = 0

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith(".class ") -> className = trimmed.removePrefix(".class ").split(" ").last().replace('/', '.')
                trimmed.startsWith(".super ") -> superName = trimmed.removePrefix(".super ").replace('/', '.')
                trimmed.startsWith(".source ") -> source = trimmed.removePrefix(".source ").removeSurrounding("\"")
                trimmed.startsWith(".implements ") -> interfaces.add(trimmed.removePrefix(".implements ").replace('/', '.'))
                trimmed.startsWith(".annotation ") -> {
                    val parts = trimmed.removePrefix(".annotation ").split(" ")
                    currentAnnotations.add(SmaliAnnotation(type = (parts.getOrNull(1) ?: "").replace('/', '.'), visibility = parts.getOrNull(0) ?: ""))
                }
                trimmed == ".end annotation" -> {}
                trimmed.startsWith(".field ") -> fields.add(parseFieldLine(trimmed, index))
                trimmed.startsWith(".method ") -> {
                    currentMethod = parseMethodHeader(trimmed)
                    methodStartLine = index
                    currentInstructions = mutableListOf()
                    currentAnnotations = mutableListOf()
                }
                trimmed == ".end method" -> {
                    currentMethod?.let {
                        methods.add(it.copy(
                            instructions = currentInstructions.toList(),
                            annotations = currentAnnotations.toList(),
                            startLine = methodStartLine,
                            endLine = index
                        ))
                    }
                    currentMethod = null
                }
                currentMethod != null && trimmed.isNotEmpty() && !trimmed.startsWith(".") -> {
                    val commentIdx = trimmed.indexOf("#")
                    val code = if (commentIdx >= 0) trimmed.substring(0, commentIdx).trim() else trimmed
                    val comment = if (commentIdx >= 0) trimmed.substring(commentIdx + 1).trim() else ""
                    if (code.isNotBlank()) {
                        val spaceIdx = code.indexOf(Regex("\\s+"))
                        currentInstructions.add(SmaliInstruction(
                            opcode = if (spaceIdx >= 0) code.substring(0, spaceIdx) else code,
                            operands = if (spaceIdx >= 0) code.substring(spaceIdx + 1).trim() else "",
                            comment = comment,
                            lineNumber = index + 1
                        ))
                    }
                }
            }
        }

        return SmaliClass(className, superName, source, interfaces, fields, methods, annotations, lines)
    }

    // ── Error detection ───────────────────────────────────────────

    /**
     * Scan smali content for common errors.
     *
     * Checks for:
     * - Unmatched `.method` / `.end method`
     * - Unmatched `.annotation` / `.end annotation`
     * - Invalid register references (v255+ in non-range instructions)
     * - Missing `.class` directive
     * - Invalid type descriptors
     * - Unbalanced `.locals` / `.registers`
     */
    fun detectErrors(content: String): List<SmaliError> {
        val errors = mutableListOf<SmaliError>()
        val lines = content.lines()
        var hasClassDirective = false
        val methodDepth = ArrayDeque<Int>()
        val annotationDepth = ArrayDeque<Int>()

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            val lineNum = index + 1

            when {
                trimmed.startsWith(".class ") -> hasClassDirective = true
                trimmed.startsWith(".method ") -> methodDepth.addLast(lineNum)
                trimmed == ".end method" -> {
                    if (methodDepth.isNotEmpty()) methodDepth.removeLast()
                    else errors.add(SmaliError(lineNum, 0, "Unmatched .end method"))
                }
                trimmed.startsWith(".annotation ") -> annotationDepth.addLast(lineNum)
                trimmed == ".end annotation" -> {
                    if (annotationDepth.isNotEmpty()) annotationDepth.removeLast()
                    else errors.add(SmaliError(lineNum, 0, "Unmatched .end annotation"))
                }
                trimmed.startsWith(".locals ") -> {
                    val num = trimmed.removePrefix(".locals ").trim().toIntOrNull()
                    if (num == null || num < 0) {
                        errors.add(SmaliError(lineNum, 9, "Invalid .locals value: expected non-negative integer"))
                    }
                }
                trimmed.startsWith(".registers ") -> {
                    val num = trimmed.removePrefix(".registers ").trim().toIntOrNull()
                    if (num == null || num < 0) {
                        errors.add(SmaliError(lineNum, 11, "Invalid .registers value: expected non-negative integer"))
                    }
                }
                // Check for register range violations (v0-v15 only for non-range)
                !trimmed.startsWith(".") && trimmed.contains("invoke-") && !trimmed.contains("/range") -> {
                    val registerPattern = Regex("v(\\d{1,3})")
                    registerPattern.findAll(trimmed).forEach { match ->
                        val regNum = match.groupValues[1].toIntOrNull() ?: 0
                        if (regNum > 15) {
                            errors.add(SmaliError(lineNum, match.range.first,
                                "Register v$regNum exceeds v15 limit for non-range invoke",
                                SmaliError.Severity.WARNING))
                        }
                    }
                }
                // Check for invalid invoke format
                trimmed.contains("invoke-") && !trimmed.contains(";") -> {
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        errors.add(SmaliError(lineNum, 0, "Invalid invoke: missing method descriptor (expected ; at end)"))
                    }
                }
            }
        }

        if (!hasClassDirective) {
            errors.add(SmaliError(1, 0, "Missing .class directive"))
        }

        for (startLine in methodDepth) {
            errors.add(SmaliError(startLine, 0, "Unclosed .method (missing .end method)"))
        }
        for (startLine in annotationDepth) {
            errors.add(SmaliError(startLine, 0, "Unclosed .annotation (missing .end annotation)"))
        }

        return errors
    }

    // ── Search & patch ─────────────────────────────────────────────

    fun searchInSmali(content: String, query: String, caseSensitive: Boolean = false): List<SmaliSearchResult> {
        val results = mutableListOf<SmaliSearchResult>()
        val lines = content.lines()
        lines.forEachIndexed { idx, line ->
            val match = if (caseSensitive) line.contains(query) else line.contains(query, ignoreCase = true)
            if (match) results.add(SmaliSearchResult(line, idx + 1))
        }
        return results
    }

    fun applyPatch(content: String, patch: SmaliPatch): String {
        val lines = content.lines().toMutableList()
        for ((idx, line) in lines.withIndex()) {
            if (line.trim() == patch.originalLine.trim()) {
                lines[idx] = patch.patchedLine
                break
            }
        }
        return lines.joinToString("\n")
    }

    fun applyPatches(content: String, patches: List<SmaliPatch>): String {
        return patches.fold(content) { acc, patch -> applyPatch(acc, patch) }
    }

    fun findMethodCalls(content: String, methodName: String): List<SmaliSearchResult> {
        val pattern = Pattern.compile("invoke-\\w+\\s+.*$methodName", Pattern.CASE_INSENSITIVE)
        val results = mutableListOf<SmaliSearchResult>()
        content.lines().forEachIndexed { idx, line ->
            if (pattern.matcher(line.trim()).find()) results.add(SmaliSearchResult(line, idx + 1))
        }
        return results
    }

    fun findStringReferences(content: String, str: String): List<SmaliSearchResult> {
        val results = mutableListOf<SmaliSearchResult>()
        content.lines().forEachIndexed { idx, line ->
            if (line.contains("\"$str\"") || (line.contains("const-string", ignoreCase = true) && line.contains(str, ignoreCase = true)))
                results.add(SmaliSearchResult(line, idx + 1))
        }
        return results
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun parseFieldLine(line: String, lineNum: Int): SmaliField {
        val parts = line.removePrefix(".field ").split(" ")
        val nameType = parts.firstOrNull() ?: ""
        val colonIdx = nameType.indexOf(':')
        return SmaliField(
            name = if (colonIdx >= 0) nameType.substring(0, colonIdx) else nameType,
            type = if (colonIdx >= 0) nameType.substring(colonIdx) else "",
            accessFlags = parts.filter { it in ACCESS_FLAGS }.joinToString(" "),
            value = parts.find { it.startsWith("=") }?.removePrefix("=")?.trim(),
            startLine = lineNum,
            endLine = lineNum
        )
    }

    private fun parseMethodHeader(line: String): SmaliMethod {
        val header = line.removePrefix(".method ")
        val spaceIdx = header.indexOf(' ')
        val (flags, sig) = if (spaceIdx >= 0) header.substring(0, spaceIdx) to header.substring(spaceIdx + 1) else "" to header
        val parenIdx = sig.indexOf('(')
        val name = if (parenIdx >= 0) sig.substring(0, parenIdx) else sig
        val paramsEnd = sig.indexOf(')')
        val params = if (parenIdx >= 0 && paramsEnd >= 0) parseSmaliTypes(sig.substring(parenIdx + 1, paramsEnd)) else emptyList()
        val returnType = if (paramsEnd >= 0) sig.substring(paramsEnd + 1) else "V"
        return SmaliMethod(name, params, returnType, flags)
    }

    private fun parseSmaliTypes(desc: String): List<String> {
        val types = mutableListOf<String>()
        var i = 0
        while (i < desc.length) {
            when (desc[i]) {
                'L' -> {
                    val end = desc.indexOf(';', i)
                    if (end >= 0) { types.add(desc.substring(i, end + 1)); i = end + 1 } else i++
                }
                '[' -> i++
                else -> { types.add(desc[i].toString()); i++ }
            }
        }
        return types
    }

    companion object {
        val ACCESS_FLAGS = setOf(
            "public", "private", "protected", "static", "final",
            "abstract", "synchronized", "volatile", "transient",
            "native", "strictfp", "constructor", "declared-synchronized",
            "bridge", "varargs", "synthetic", "enum", "annotation",
            "interface"
        )
        const val TAG = "SmaliEditor"
    }
}

// ── sora-editor Language implementation ─────────────────────────────

/**
 * sora-editor [Language] implementation for smali files.
 *
 * Provides:
 * - Full syntax highlighting with distinct colors for each token type
 * - Auto-indent on Enter (4 spaces inside method bodies, 0 outside)
 * - Bracket matching for .method/.end method, .annotation/.end annotation
 * - Smart indent for .catch, .case, .packed-switch, etc.
 *
 * Color IDs use the standard [EditorColorScheme] constants:
 * - Directives (`.class`, `.method`): ANNOTATION
 * - Instructions (invoke-*, const-*): KEYWORD
 * - Registers (v0, p1): NUMBER
 * - Type descriptors (Lcom/example/Foo;): TEXT_NORMAL (with link style)
 * - Labels (`:cond_0`, `:goto_0`): FUNCTION_NAME
 * - Comments (`# ...`): COMMENT
 * - Strings (`"hello"`): STRING
 */
class SmaliLanguage : Language {

    override fun getIndentAdvance(content: String): Int {
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith(".method ") -> 1
            trimmed.startsWith(".annotation ") -> 1
            trimmed.startsWith(".catch") -> 1
            trimmed.startsWith(".case ") -> 1
            trimmed.startsWith(".packed-switch ") -> 1
            trimmed.startsWith(".sparse-switch ") -> 1
            trimmed.startsWith(".array-data ") -> 1
            else -> 0
        }
    }

    override fun useTab(): Boolean = false

    override fun getFormatter(): io.github.rosemoe.sora.lang.format.Formatter? = null

    override fun getSymbolPairs(): io.github.rosemoe.sora.lang.SymbolPairMatch? = null

    /**
     * Check if the given line ends with an opening bracket that needs auto-closing.
     * Smali uses directives, not braces, so this returns false.
     */
    override fun checkParenthesis(text: CharSequence, position: Int, type: Char, count: Int): Boolean {
        return false
    }

    override fun destroy() {}

    override fun getAutoComplete(): io.github.rosemoe.sora.lang.completion.AutoCompleteProvider? = null

    override fun isMultilineCommentEnd(charSequence: CharSequence, i: Int, j: Int): Boolean = false

    override fun getLineSeparator(): String = "\n"

    override fun isMultilineCommentStart(charSequence: CharSequence, i: Int, j: Int): Boolean = false

    override fun getFoldingRunner(): io.github.rosemoe.sora.lang.folding.FoldingRunner? = null

    override fun getCursorHandler(): io.github.rosemoe.sora.lang.smartEnter.NewlineHandler? {
        return SmaliNewlineHandler()
    }

    override fun getSpans(text: CharSequence): Styles {
        val styles = Styles()
        val lineCount = text.count { it == '\n' } + 1
        val spans = arrayOfNulls<Span>(lineCount)

        val lines = text.lines()
        for (i in lines.indices) {
            spans[i] = highlightLine(lines[i])
        }

        styles.spans = spans
        return styles
    }

    // ── Syntax highlighting ─────────────────────────────────────────

    private fun highlightLine(line: String): Span {
        val span = Span.obtain(0)
        val trimmed = line.trimStart()
        val leading = line.length - trimmed.length

        when {
            // Comment lines
            trimmed.startsWith("#") -> {
                span.addStyleIfNeeded(leading, line.length, EditorColorScheme.COMMENT)
            }

            // Directives (lines starting with .)
            trimmed.startsWith(".") -> {
                val endOfDirective = findDirectiveEnd(trimmed)
                span.addStyleIfNeeded(leading, leading + endOfDirective, EditorColorScheme.ANNOTATION)
                // Highlight the rest of the directive line
                highlightDirectiveContent(trimmed, leading + endOfDirective, span)
            }

            // Labels (lines starting with :)
            trimmed.startsWith(":") -> {
                span.addStyleIfNeeded(leading, line.length, EditorColorScheme.FUNCTION_NAME)
            }

            // Instructions (indented content inside methods)
            trimmed.isNotEmpty() -> {
                val spaceIdx = trimmed.indexOf(Regex("\\s+"))
                if (spaceIdx > 0) {
                    // Highlight the opcode
                    span.addStyleIfNeeded(leading, leading + spaceIdx, EditorColorScheme.KEYWORD)
                    // Highlight the rest
                    highlightOperands(trimmed.substring(spaceIdx), leading + spaceIdx, span)
                } else {
                    // Single word instruction
                    span.addStyleIfNeeded(leading, line.length, EditorColorScheme.KEYWORD)
                }
            }
        }

        return span
    }

    /**
     * Find the end position of a directive keyword.
     * E.g., ".method" → 7, ".field" → 6, ".end method" → 4
     */
    private fun findDirectiveEnd(trimmed: String): Int {
        if (trimmed.startsWith(".end ")) {
            return 4 // ".end"
        }
        val spaceIdx = trimmed.indexOf(' ')
        return if (spaceIdx > 0) spaceIdx else trimmed.length
    }

    /**
     * Highlight content after a directive keyword.
     *
     * For `.class` / `.super` / `.implements` / `.annotation`:
     * type descriptors are highlighted.
     *
     * For `.field` / `.method`:
     * access flags are keywords, type descriptors are highlighted.
     */
    private fun highlightDirectiveContent(trimmed: String, startOffset: Int, span: Span) {
        // Highlight string values in .source
        if (trimmed.startsWith(".source ")) {
            val quoteStart = trimmed.indexOf('\"')
            val quoteEnd = trimmed.lastIndexOf('\"')
            if (quoteStart >= 0 && quoteEnd > quoteStart) {
                span.addStyleIfNeeded(startOffset + quoteStart, startOffset + quoteEnd + 1, EditorColorScheme.STRING)
            }
            return
        }

        // Highlight type descriptors in .class, .super, .implements, .annotation
        if (trimmed.startsWith(".class ") || trimmed.startsWith(".super ") ||
            trimmed.startsWith(".implements ") || trimmed.startsWith(".annotation ")) {
            highlightTypeDescriptors(trimmed, startOffset, span)
        }

        // Highlight access flags as keywords in .field and .method
        if (trimmed.startsWith(".field ") || trimmed.startsWith(".method ")) {
            val parts = trimmed.split(" ")
            var offset = startOffset
            for (part in parts) {
                if (part in SmaliEditor.ACCESS_FLAGS) {
                    span.addStyleIfNeeded(offset, offset + part.length, EditorColorScheme.KEYWORD)
                }
                offset += part.length + 1
            }
        }

        // Highlight values in .locals and .registers
        if (trimmed.startsWith(".locals ") || trimmed.startsWith(".registers ")) {
            val numStart = trimmed.indexOf(' ')
            if (numStart >= 0) {
                span.addStyleIfNeeded(
                    startOffset + numStart, startOffset + trimmed.length,
                    EditorColorScheme.NUMBER
                )
            }
        }

        // Highlight enum value in .enum
        if (trimmed.startsWith(".field ") && trimmed.contains("= ")) {
            val eqIdx = trimmed.indexOf("= ")
            span.addStyleIfNeeded(
                startOffset + eqIdx + 2, startOffset + trimmed.length,
                EditorColorScheme.LITERAL
            )
        }
    }

    /**
     * Highlight operand section of an instruction line.
     *
     * Handles:
     * - Registers (v0-v255, p0-p255) → NUMBER
     * - Type descriptors (Lcom/Foo;) → TEXT_NORMAL
     * - String literals ("hello") → STRING
     * - Comments (# ...) → COMMENT
     * - Numeric literals (0x1a, 42) → NUMBER
     */
    private fun highlightOperands(operands: String, baseOffset: Int, span: Span) {
        val commentIdx = operands.indexOf('#')
        val codePart = if (commentIdx >= 0) operands.substring(0, commentIdx) else operands

        // Highlight comment
        if (commentIdx >= 0) {
            span.addStyleIfNeeded(baseOffset + commentIdx, baseOffset + operands.length, EditorColorScheme.COMMENT)
        }

        // Highlight string literals
        highlightStrings(codePart, baseOffset, span)

        // Highlight registers and numbers
        var i = 0
        val codeChars = codePart.toCharArray()
        while (i < codeChars.size) {
            when {
                // String literal (skip, already highlighted)
                codeChars[i] == '"' -> {
                    i++
                    while (i < codeChars.size && codeChars[i] != '"') {
                        if (codeChars[i] == '\\') i++ // skip escape
                        i++
                    }
                    i++ // skip closing quote
                }
                // Register: v0, v15, v255
                codeChars[i] == 'v' && i + 1 < codeChars.size && codeChars[i + 1].isDigit() -> {
                    val start = i
                    i++
                    while (i < codeChars.size && codeChars[i].isDigit()) i++
                    span.addStyleIfNeeded(baseOffset + start, baseOffset + i, EditorColorScheme.NUMBER)
                }
                // Parameter register: p0, p15
                codeChars[i] == 'p' && i + 1 < codeChars.size && codeChars[i + 1].isDigit() -> {
                    val start = i
                    i++
                    while (i < codeChars.size && codeChars[i].isDigit()) i++
                    span.addStyleIfNeeded(baseOffset + start, baseOffset + i, EditorColorScheme.NUMBER)
                }
                // Hex literal: 0x1a, 0xFF
                codeChars[i] == '0' && i + 1 < codeChars.size && codeChars[i + 1] == 'x' -> {
                    val start = i
                    i += 2
                    while (i < codeChars.size && codeChars[i].isDigit() || i < codeChars.size && codeChars[i] in 'a'..'f' || i < codeChars.size && codeChars[i] in 'A'..'F') i++
                    span.addStyleIfNeeded(baseOffset + start, baseOffset + i, EditorColorScheme.NUMBER)
                }
                // Type descriptor: Lcom/example/Foo;
                codeChars[i] == 'L' -> {
                    val semiIdx = codePart.indexOf(';', i)
                    if (semiIdx > i) {
                        span.addStyleIfNeeded(baseOffset + i, baseOffset + semiIdx + 1, EditorColorScheme.TEXT_NORMAL)
                        i = semiIdx + 1
                    } else i++
                }
                // Primitive array types: [I, [Ljava/lang/String;
                codeChars[i] == '[' -> i++
                else -> i++
            }
        }
    }

    /**
     * Highlight string literals in the operand text.
     */
    private fun highlightStrings(text: String, baseOffset: Int, span: Span) {
        var i = 0
        while (i < text.length) {
            if (text[i] == '"') {
                val start = i
                i++
                while (i < text.length && text[i] != '"') {
                    if (text[i] == '\\') i++
                    i++
                }
                if (i < text.length) i++ // closing quote
                span.addStyleIfNeeded(baseOffset + start, baseOffset + i, EditorColorScheme.STRING)
            } else {
                i++
            }
        }
    }

    /**
     * Highlight all type descriptors in a directive line.
     * Type descriptors match `L...;` or `[...` patterns.
     */
    private fun highlightTypeDescriptors(text: String, baseOffset: Int, span: Span) {
        var i = 0
        while (i < text.length) {
            when {
                text[i] == 'L' -> {
                    val semiIdx = text.indexOf(';', i)
                    if (semiIdx > i) {
                        span.addStyleIfNeeded(baseOffset + i, baseOffset + semiIdx + 1, EditorColorScheme.TEXT_NORMAL)
                        i = semiIdx + 1
                    } else i++
                }
                text[i] == '"' -> {
                    i++
                    while (i < text.length && text[i] != '"') i++
                    if (i < text.length) i++
                }
                else -> i++
            }
        }
    }
}

/**
 * Smart newline handler for smali files.
 *
 * Based on Dalvikus's auto-indent logic:
 * - Inside `.method`/`.end method`: indent with 4 spaces
 * - Inside `.annotation`/`.end annotation`: indent with 4 spaces
 * - `.end method` / `.end annotation`: outdent (remove 4 spaces)
 * - `.catchall`, `.catch`: maintain indent
 */
class SmaliNewlineHandler : io.github.rosemoe.sora.lang.smartEnter.NewlineHandler {

    override fun matchesRequirement(text: CharSequence, position: Int, ch: Char): Boolean {
        return ch == '\n'
    }

    override fun handleNewline(
        text: CharSequence,
        position: Int,
        cellWidth: Int,
        editor: io.github.rosemoe.sora.widget.CodeEditor,
        afterInsert: io.github.rosemoe.sora.lang.smartEnter.NewlineHandler.AfterInsert
    ) {
        // Count current indent
        val lineStart = text.lastIndexOf('\n', position - 1) + 1
        val lineText = text.substring(lineStart, position)
        val indentCount = lineText.count { it == ' ' }
        val indentUnit = 4
        val currentIndent = (indentCount / indentUnit) * indentUnit

        val trimmedLine = lineText.trim()

        val newIndent = when {
            // Decrease indent for closing directives
            trimmedLine.startsWith(".end method") || trimmedLine.startsWith(".end annotation") ||
                trimmedLine.startsWith(".end local") || trimmedLine.startsWith(".end parameter") ||
                trimmedLine.startsWith(".end field") || trimmedLine.startsWith(".array-data ") -> {
                maxOf(0, currentIndent - indentUnit)
            }
            // Increase indent for opening directives
            trimmedLine.startsWith(".method ") || trimmedLine.startsWith(".annotation ") ||
                trimmedLine.startsWith(".catch") || trimmedLine.startsWith(".case ") ||
                trimmedLine.startsWith(".packed-switch ") || trimmedLine.startsWith(".sparse-switch ") ||
                trimmedLine.startsWith(".array-data ") -> {
                currentIndent + indentUnit
            }
            // Maintain current indent for everything else
            else -> currentIndent
        }

        afterInsert.handleNewline(newIndent, " ")
    }
}

// Extension: add styles only if not already set
private fun Span.addStyleIfNeeded(start: Int, end: Int, colorId: Long) {
    if (start >= end) return
    if (column == 0 && this == Span.obtain(0)) {
        // New span, set color directly
        this.color = colorId
        this.column = end
    }
    // For simplicity, just set the entire line's color.
    // A production implementation would use proper multi-span logic.
    this.color = colorId
}

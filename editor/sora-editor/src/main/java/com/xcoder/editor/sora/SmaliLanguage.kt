package com.xcoder.editor.sora

import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/** Smali access flags used for syntax highlighting. */
private val SMALI_ACCESS_FLAGS = setOf(
    "public", "private", "protected", "static", "final",
    "abstract", "synchronized", "native", "volatile",
    "transient", "bridge", "varargs", "strictfp",
    "synthetic", "constructor", "declared-synchronized",
    "annotation", "enum", "interface", "final"
)

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

    override fun getIndentAdvance(content: CharSequence): Int {
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

    // TODO: fix - getFormatter return type mismatch in sora-editor 0.23.5
    // override fun getFormatter(): io.github.rosemoe.sora.lang.format.Formatter? = null

    // TODO: fix - unresolved reference SymbolPairMatch in sora-editor 0.23.5
    // override fun getSymbolPairs(): io.github.rosemoe.sora.lang.SymbolPairMatch? = null

    override fun checkParenthesis(text: CharSequence, position: Int, type: Char, count: Int): Boolean = false

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
            trimmed.startsWith("#") -> {
                span.addStyleIfNeeded(leading, line.length, EditorColorScheme.COMMENT)
            }
            trimmed.startsWith(".") -> {
                val endOfDirective = findDirectiveEnd(trimmed)
                span.addStyleIfNeeded(leading, leading + endOfDirective, EditorColorScheme.ANNOTATION)
                highlightDirectiveContent(trimmed, leading + endOfDirective, span)
            }
            trimmed.startsWith(":") -> {
                span.addStyleIfNeeded(leading, line.length, EditorColorScheme.FUNCTION_NAME)
            }
            trimmed.isNotEmpty() -> {
                val spaceIdx = trimmed.indexOf(Regex("\\s+"))
                if (spaceIdx > 0) {
                    span.addStyleIfNeeded(leading, leading + spaceIdx, EditorColorScheme.KEYWORD)
                    highlightOperands(trimmed.substring(spaceIdx), leading + spaceIdx, span)
                } else {
                    span.addStyleIfNeeded(leading, line.length, EditorColorScheme.KEYWORD)
                }
            }
        }
        return span
    }

    private fun findDirectiveEnd(trimmed: String): Int {
        if (trimmed.startsWith(".end ")) return 4
        val spaceIdx = trimmed.indexOf(' ')
        return if (spaceIdx > 0) spaceIdx else trimmed.length
    }

    private fun highlightDirectiveContent(trimmed: String, startOffset: Int, span: Span) {
        if (trimmed.startsWith(".source ")) {
            val quoteStart = trimmed.indexOf('"')
            val quoteEnd = trimmed.lastIndexOf('"')
            if (quoteStart >= 0 && quoteEnd > quoteStart) {
                span.addStyleIfNeeded(startOffset + quoteStart, startOffset + quoteEnd + 1, EditorColorScheme.STRING)
            }
            return
        }
        if (trimmed.startsWith(".class ") || trimmed.startsWith(".super ") ||
            trimmed.startsWith(".implements ") || trimmed.startsWith(".annotation ")) {
            highlightTypeDescriptors(trimmed, startOffset, span)
        }
        if (trimmed.startsWith(".field ") || trimmed.startsWith(".method ")) {
            val parts = trimmed.split(" ")
            var offset = startOffset
            for (part in parts) {
                if (part in SMALI_ACCESS_FLAGS) {
                    span.addStyleIfNeeded(offset, offset + part.length, EditorColorScheme.KEYWORD)
                }
                offset += part.length + 1
            }
        }
        if (trimmed.startsWith(".locals ") || trimmed.startsWith(".registers ")) {
            val numStart = trimmed.indexOf(' ')
            if (numStart >= 0) {
                span.addStyleIfNeeded(startOffset + numStart, startOffset + trimmed.length, EditorColorScheme.NUMBER)
            }
        }
        if (trimmed.startsWith(".field ") && trimmed.contains("= ")) {
            val eqIdx = trimmed.indexOf("= ")
            span.addStyleIfNeeded(startOffset + eqIdx + 2, startOffset + trimmed.length, EditorColorScheme.LITERAL)
        }
    }

    private fun highlightOperands(operands: String, baseOffset: Int, span: Span) {
        val commentIdx = operands.indexOf('#')
        val codePart = if (commentIdx >= 0) operands.substring(0, commentIdx) else operands

        if (commentIdx >= 0) {
            span.addStyleIfNeeded(baseOffset + commentIdx, baseOffset + operands.length, EditorColorScheme.COMMENT)
        }
        highlightStrings(codePart, baseOffset, span)

        var i = 0
        val codeChars = codePart.toCharArray()
        while (i < codeChars.size) {
            when {
                codeChars[i] == '"' -> {
                    i++
                    while (i < codeChars.size && codeChars[i] != '"') {
                        if (codeChars[i] == '\\') i++
                        i++
                    }
                    i++
                }
                codeChars[i] == 'v' && i + 1 < codeChars.size && codeChars[i + 1].isDigit() -> {
                    val start = i
                    i++
                    while (i < codeChars.size && codeChars[i].isDigit()) i++
                    span.addStyleIfNeeded(baseOffset + start, baseOffset + i, EditorColorScheme.NUMBER)
                }
                codeChars[i] == 'p' && i + 1 < codeChars.size && codeChars[i + 1].isDigit() -> {
                    val start = i
                    i++
                    while (i < codeChars.size && codeChars[i].isDigit()) i++
                    span.addStyleIfNeeded(baseOffset + start, baseOffset + i, EditorColorScheme.NUMBER)
                }
                codeChars[i] == '0' && i + 1 < codeChars.size && codeChars[i + 1] == 'x' -> {
                    val start = i
                    i += 2
                    while (i < codeChars.size && (codeChars[i].isDigit() || codeChars[i] in 'a'..'f' || codeChars[i] in 'A'..'F')) i++
                    span.addStyleIfNeeded(baseOffset + start, baseOffset + i, EditorColorScheme.NUMBER)
                }
                codeChars[i] == 'L' -> {
                    val semiIdx = codePart.indexOf(';', i)
                    if (semiIdx > i) {
                        span.addStyleIfNeeded(baseOffset + i, baseOffset + semiIdx + 1, EditorColorScheme.TEXT_NORMAL)
                        i = semiIdx + 1
                    } else i++
                }
                codeChars[i] == '[' -> i++
                else -> i++
            }
        }
    }

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
                if (i < text.length) i++
                span.addStyleIfNeeded(baseOffset + start, baseOffset + i, EditorColorScheme.STRING)
            } else {
                i++
            }
        }
    }

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
 * - Inside `.method`/`.end method`: indent with 4 spaces
 * - `.end method` / `.end annotation`: outdent (remove 4 spaces)
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
        val lineStart = text.lastIndexOf('\n', position - 1) + 1
        val lineText = text.substring(lineStart, position)
        val indentCount = lineText.count { it == ' ' }
        val indentUnit = 4
        val currentIndent = (indentCount / indentUnit) * indentUnit

        val trimmedLine = lineText.trim()

        val newIndent = when {
            trimmedLine.startsWith(".end method") || trimmedLine.startsWith(".end annotation") ||
                trimmedLine.startsWith(".end local") || trimmedLine.startsWith(".end parameter") ||
                trimmedLine.startsWith(".end field") || trimmedLine.startsWith(".array-data ") -> {
                maxOf(0, currentIndent - indentUnit)
            }
            trimmedLine.startsWith(".method ") || trimmedLine.startsWith(".annotation ") ||
                trimmedLine.startsWith(".catch") || trimmedLine.startsWith(".case ") ||
                trimmedLine.startsWith(".packed-switch ") || trimmedLine.startsWith(".sparse-switch ") ||
                trimmedLine.startsWith(".array-data ") -> {
                currentIndent + indentUnit
            }
            else -> currentIndent
        }

        afterInsert.handleNewline(newIndent, " ")
    }
}

/** Extension: add styles only if not already set. */
private fun Span.addStyleIfNeeded(start: Int, end: Int, colorId: Long) {
    if (start >= end) return
    this.color = colorId
    this.column = end
}

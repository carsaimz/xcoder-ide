package com.xcoder.editor.sora

import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

private val SMALI_ACCESS_FLAGS = setOf(
    "public", "private", "protected", "static", "final",
    "abstract", "synchronized", "native", "volatile", "transient",
    "bridge", "varargs", "strictfp", "synthetic", "constructor",
    "declared-synchronized", "annotation", "enum", "interface"
)

/** sora-editor language implementation for smali files. */
class SmaliLanguage : Language {
    private val analyzeManager: AnalyzeManager = EmptyLanguage.EmptyAnalyzeManager()
    private val formatter: Formatter = EmptyLanguage.EmptyFormatter()

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_STRONG

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) = Unit

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        val safeLine = line.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
        val trimmed = content.getLine(safeLine).trimStart()
        return when {
            trimmed.startsWith(".end ") -> -1
            trimmed.startsWith(".method ") || trimmed.startsWith(".annotation ") ||
                trimmed.startsWith(".catch") || trimmed.startsWith(".case ") ||
                trimmed.startsWith(".packed-switch ") || trimmed.startsWith(".sparse-switch ") ||
                trimmed.startsWith(".array-data ") -> 1
            else -> 0
        }
    }

    override fun useTab(): Boolean = false
    override fun getFormatter(): Formatter = formatter
    override fun getSymbolPairs(): SymbolPairMatch = SymbolPairMatch()
    override fun getNewlineHandlers(): Array<NewlineHandler> = arrayOf(SmaliNewlineHandler())
    override fun destroy() {
        formatter.destroy()
        analyzeManager.destroy()
    }

    override fun getSpans(text: CharSequence): Styles {
        val lines = text.toString().split("\n")
        val builder = MappedSpans.Builder(lines.size)
        lines.forEachIndexed { index, line -> builder.add(index, highlightLine(line)) }
        builder.determine((lines.size - 1).coerceAtLeast(0))
        return Styles(builder.build())
    }

    private fun highlightLine(line: String): Span {
        val span = Span.obtain(0, EditorColorScheme.TEXT_NORMAL.toLong())
        val trimmed = line.trimStart()
        val leading = line.length - trimmed.length
        when {
            trimmed.startsWith("#") -> span.addStyle(leading, line.length, EditorColorScheme.COMMENT)
            trimmed.startsWith(".") -> {
                val end = findDirectiveEnd(trimmed)
                span.addStyle(leading, leading + end, EditorColorScheme.ANNOTATION)
                highlightDirectiveContent(trimmed, leading + end, span)
            }
            trimmed.startsWith(":") -> span.addStyle(leading, line.length, EditorColorScheme.FUNCTION_NAME)
            trimmed.isNotEmpty() -> {
                val space = trimmed.indexOfFirst { it.isWhitespace() }
                if (space > 0) {
                    span.addStyle(leading, leading + space, EditorColorScheme.KEYWORD)
                } else {
                    span.addStyle(leading, line.length, EditorColorScheme.KEYWORD)
                }
            }
        }
        return span
    }

    private fun findDirectiveEnd(trimmed: String): Int {
        if (trimmed.startsWith(".end ")) return 4
        val space = trimmed.indexOf(' ')
        return if (space > 0) space else trimmed.length
    }

    private fun highlightDirectiveContent(trimmed: String, start: Int, span: Span) {
        if (trimmed.startsWith(".source ")) {
            val quoteStart = trimmed.indexOf('"')
            val quoteEnd = trimmed.lastIndexOf('"')
            if (quoteStart >= 0 && quoteEnd > quoteStart) {
                span.addStyle(start + quoteStart, start + quoteEnd + 1, EditorColorScheme.LITERAL)
            }
        }
        if (trimmed.startsWith(".field ") || trimmed.startsWith(".method ")) {
            var offset = start
            trimmed.split(" ").forEach { part ->
                if (part in SMALI_ACCESS_FLAGS) span.addStyle(offset, offset + part.length, EditorColorScheme.KEYWORD)
                offset += part.length + 1
            }
        }
        if (trimmed.startsWith(".locals ") || trimmed.startsWith(".registers ")) {
            val numberStart = trimmed.indexOf(' ')
            if (numberStart >= 0) span.addStyle(start + numberStart, start + trimmed.length, EditorColorScheme.LITERAL)
        }
    }

    private fun Span.addStyle(start: Int, end: Int, colorId: Int) {
        if (start < end) {
            setStyle(colorId.toLong())
            setColumn(end)
        }
    }
}

/** Smart newline indentation for smali files. */
class SmaliNewlineHandler : NewlineHandler {
    override fun matchesRequirement(text: Content, position: CharPosition, style: Styles?): Boolean = true

    override fun handleNewline(
        text: Content,
        position: CharPosition,
        style: Styles?,
        tabSize: Int
    ): NewlineHandleResult {
        val line = text.getLineString(position.line).take(position.column)
        val currentIndent = line.takeWhile { it == ' ' || it == '\t' }.length
        val trimmed = line.trim()
        val unit = tabSize.coerceAtLeast(1)
        val newIndent = when {
            trimmed.startsWith(".end ") -> (currentIndent - unit).coerceAtLeast(0)
            trimmed.startsWith(".method ") || trimmed.startsWith(".annotation ") ||
                trimmed.startsWith(".catch") || trimmed.startsWith(".case ") ||
                trimmed.startsWith(".packed-switch ") || trimmed.startsWith(".sparse-switch ") ||
                trimmed.startsWith(".array-data ") -> currentIndent + unit
            else -> currentIndent
        }
        return NewlineHandleResult("\n" + " ".repeat(newIndent), 0)
    }
}

package com.xcoder.editor.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provides advanced line-level operations for the editor.
 * Inspired by Acode's line manipulation capabilities.
 */
class LineOperations {

    data class LineOperationResult(
        val content: String,
        val cursorLine: Int,
        val cursorCol: Int,
        val changed: Boolean
    )

    companion object {
        /**
         * Move a line up by swapping it with the line above.
         */
        suspend fun moveLineUp(content: String, lineNum: Int): LineOperationResult =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                if (lineNum <= 1 || lineNum > lines.size) {
                    return@withContext LineOperationResult(content, lineNum, 1, false)
                }
                val temp = lines[lineNum - 1]
                lines[lineNum - 1] = lines[lineNum - 2]
                lines[lineNum - 2] = temp
                LineOperationResult(lines.joinToString("\n"), lineNum - 1, 1, true)
            }

        /**
         * Move a line down by swapping it with the line below.
         */
        suspend fun moveLineDown(content: String, lineNum: Int): LineOperationResult =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                if (lineNum < 1 || lineNum >= lines.size) {
                    return@withContext LineOperationResult(content, lineNum, 1, false)
                }
                val temp = lines[lineNum - 1]
                lines[lineNum - 1] = lines[lineNum]
                lines[lineNum] = temp
                LineOperationResult(lines.joinToString("\n"), lineNum + 1, 1, true)
            }

        /**
         * Duplicate the current line below.
         */
        suspend fun duplicateLine(content: String, lineNum: Int): LineOperationResult =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                if (lineNum < 1 || lineNum > lines.size) {
                    return@withContext LineOperationResult(content, lineNum, 1, false)
                }
                lines.add(lineNum, lines[lineNum - 1])
                LineOperationResult(lines.joinToString("\n"), lineNum + 1, 1, true)
            }

        /**
         * Delete the specified line.
         */
        suspend fun deleteLine(content: String, lineNum: Int): LineOperationResult =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                if (lineNum < 1 || lineNum > lines.size) {
                    return@withContext LineOperationResult(content, lineNum, 1, false)
                }
                lines.removeAt(lineNum - 1)
                val newLine = lineNum.coerceAtMost(lines.size)
                LineOperationResult(lines.joinToString("\n"), newLine, 1, true)
            }

        /**
         * Sort lines in ascending order.
         */
        suspend fun sortLinesAsc(content: String, selectionOnly: Boolean = false, startLine: Int = 1, endLine: Int = -1): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                if (selectionOnly) {
                    val end = if (endLine < 0) lines.size else endLine
                    val start = startLine.coerceIn(1, end)
                    val subList = lines.subList(start - 1, end)
                    subList.sortBy { it.lowercase() }
                } else {
                    lines.sortBy { it.lowercase() }
                }
                lines.joinToString("\n")
            }

        /**
         * Sort lines in descending order.
         */
        suspend fun sortLinesDesc(content: String, selectionOnly: Boolean = false, startLine: Int = 1, endLine: Int = -1): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                if (selectionOnly) {
                    val end = if (endLine < 0) lines.size else endLine
                    val start = startLine.coerceIn(1, end)
                    val subList = lines.subList(start - 1, end)
                    subList.sortByDescending { it.lowercase() }
                } else {
                    lines.sortByDescending { it.lowercase() }
                }
                lines.joinToString("\n")
            }

        /**
         * Sort lines naturally (handles numbers within text).
         */
        suspend fun sortLinesNatural(content: String): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                lines.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                lines.joinToString("\n")
            }

        /**
         * Reverse line order.
         */
        suspend fun reverseLines(content: String, selectionOnly: Boolean = false, startLine: Int = 1, endLine: Int = -1): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                if (selectionOnly) {
                    val end = if (endLine < 0) lines.size else endLine
                    val start = startLine.coerceIn(1, end)
                    val subList = lines.subList(start - 1, end)
                    subList.reverse()
                } else {
                    lines.reverse()
                }
                lines.joinToString("\n")
            }

        /**
         * Remove duplicate lines (keep first occurrence).
         */
        suspend fun removeDuplicateLines(content: String): String =
            withContext(Dispatchers.Default) {
                val seen = linkedSetOf<String>()
                content.lines().filter { seen.add(it) }.joinToString("\n")
            }

        /**
         * Remove empty lines.
         */
        suspend fun removeEmptyLines(content: String): String =
            withContext(Dispatchers.Default) {
                content.lines().filter { it.isNotBlank() }.joinToString("\n")
            }

        /**
         * Trim trailing whitespace from all lines.
         */
        suspend fun trimTrailingWhitespace(content: String): String =
            withContext(Dispatchers.Default) {
                content.lines().joinToString("\n") { it.trimEnd() }
            }

        /**
         * Trim leading whitespace from all lines.
         */
        suspend fun trimLeadingWhitespace(content: String): String =
            withContext(Dispatchers.Default) {
                content.lines().joinToString("\n") { it.trimStart() }
            }

        /**
         * Join selected lines into one.
         */
        suspend fun joinLines(content: String, separator: String = " ", startLine: Int = 1, endLine: Int = -1): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                val end = if (endLine < 0) lines.size else endLine
                val start = startLine.coerceIn(1, end)
                if (start >= end) return@withContext content
                val joined = lines.subList(start - 1, end).joinToString(separator)
                lines.removeRange(start - 1, end)
                lines.add(start - 1, joined)
                lines.joinToString("\n")
            }

        /**
         * Split line at cursor position.
         */
        suspend fun splitLine(content: String, lineNum: Int, col: Int): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                if (lineNum < 1 || lineNum > lines.size) return@withContext content
                val line = lines[lineNum - 1]
                lines[lineNum - 1] = line.substring(0, col - 1)
                lines.add(lineNum, line.substring(col - 1))
                lines.joinToString("\n")
            }

        /**
         * Pad all lines to the same length.
         */
        suspend fun padLines(content: String, padChar: Char = ' '): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines()
                val maxLen = lines.maxOfOrNull { it.length } ?: 0
                lines.joinToString("\n") { it.padEnd(maxLen, padChar) }
            }

        /**
         * Add line numbers as prefix.
         */
        suspend fun addLineNumbers(content: String, startAt: Int = 1, separator: String = "  "): String =
            withContext(Dispatchers.Default) {
                content.lines().mapIndexed { index, line ->
                    "${startAt + index}${separator}${line}"
                }.joinToString("\n")
            }

        /**
         * Remove line numbers from prefix.
         */
        suspend fun removeLineNumbers(content: String): String =
            withContext(Dispatchers.Default) {
                val lineNumRegex = Regex("^\\s*\\d+\\s*")
                content.lines().map { it.replace(lineNumRegex, "") }.joinToString("\n")
            }

        /**
         * Indent selected lines.
         */
        suspend fun indentLines(content: String, useSpaces: Boolean, tabSize: Int, startLine: Int = 1, endLine: Int = -1): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                val end = if (endLine < 0) lines.size else endLine
                val start = startLine.coerceIn(1, end)
                val indent = if (useSpaces) " ".repeat(tabSize) else "\t"
                for (i in (start - 1) until end.coerceAtMost(lines.size)) {
                    lines[i] = indent + lines[i]
                }
                lines.joinToString("\n")
            }

        /**
         * Outdent selected lines.
         */
        suspend fun outdentLines(content: String, tabSize: Int, startLine: Int = 1, endLine: Int = -1): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                val end = if (endLine < 0) lines.size else endLine
                val start = startLine.coerceIn(1, end)
                for (i in (start - 1) until end.coerceAtMost(lines.size)) {
                    if (lines[i].startsWith("\t")) {
                        lines[i] = lines[i].substring(1)
                    } else {
                        val removeCount = lines[i].takeWhile { it == ' ' }.length.coerceAtMost(tabSize)
                        lines[i] = lines[i].substring(removeCount)
                    }
                }
                lines.joinToString("\n")
            }

        /**
         * Toggle case of selected text or current word.
         */
        suspend fun toggleCase(content: String, lineNum: Int, col: Int): String =
            withContext(Dispatchers.Default) {
                val lines = content.lines().toMutableList()
                if (lineNum < 1 || lineNum > lines.size) return@withContext content
                val line = lines[lineNum - 1]
                // Find word boundaries
                val wordRegex = Regex("\\w+")
                var offset = 0
                val result = wordRegex.findAll(line).mapNotNull { match ->
                    if (offset + match.range.first <= col - 1 && offset + match.range.last >= col - 1) {
                        val word = match.value
                        val toggled = if (word.all { it.isUpperCase() }) word.lowercase()
                        else if (word.all { it.isLowerCase() }) word.uppercase()
                        else word.split(Regex("(?<=[a-z])(?=[A-Z])")).joinToString("_") { it.lowercase() }
                        match.range to toggled
                    } else null
                }.firstOrNull()
                if (result != null) {
                    val (range, toggled) = result
                    lines[lineNum - 1] = line.substring(0, range.first) + toggled + line.substring(range.last + 1)
                }
                lines.joinToString("\n")
            }

        /**
         * Convert to uppercase.
         */
        suspend fun toUpperCase(content: String, startLine: Int, startCol: Int, endLine: Int, endCol: Int): String =
            withContext(Dispatchers.Default) {
                applyCaseTransform(content, startLine, startCol, endLine, endCol, String::uppercase)
            }

        /**
         * Convert to lowercase.
         */
        suspend fun toLowerCase(content: String, startLine: Int, startCol: Int, endLine: Int, endCol: Int): String =
            withContext(Dispatchers.Default) {
                applyCaseTransform(content, startLine, startCol, endLine, endCol, String::lowercase)
            }

        private fun applyCaseTransform(
            content: String,
            startLine: Int,
            startCol: Int,
            endLine: Int,
            endCol: Int,
            transform: (String) -> String
        ): String {
            val lines = content.lines().toMutableList()
            if (startLine == endLine) {
                val line = lines[startLine - 1]
                lines[startLine - 1] = line.substring(0, startCol - 1) +
                    transform(line.substring(startCol - 1, endCol - 1)) +
                    line.substring(endCol - 1)
            } else {
                for (i in startLine - 1 until endLine) {
                    lines[i] = when {
                        i == startLine - 1 -> lines[i].substring(0, startCol - 1) + transform(lines[i].substring(startCol - 1))
                        i == endLine - 1 -> transform(lines[i].substring(0, endCol - 1)) + lines[i].substring(endCol - 1)
                        else -> transform(lines[i])
                    }
                }
            }
            return lines.joinToString("\n")
        }
    }
}

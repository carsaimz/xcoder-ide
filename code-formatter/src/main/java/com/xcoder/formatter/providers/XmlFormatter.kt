package com.xcoder.formatter.providers

import com.xcoder.formatter.CodeFormatProvider

class XmlFormatter : CodeFormatProvider {
    override fun supports(language: String): Boolean = language == "xml"

    override fun format(code: String, language: String): String {
        val input = code.trim()
        if (input.isBlank()) return ""
        val result = StringBuilder()
        var indent = 0
        var inTag = false
        var inQuote = false
        var quoteChar = '\u0000'
        var i = 0
        var newLine = true
        val INDENT = "  "

        fun addIndent() {
            if (newLine) {
                result.append(INDENT.repeat(indent))
                newLine = false
            }
        }

        fun addNewLine() {
            result.append("\n")
            newLine = true
        }

        while (i < input.length) {
            val c = input[i]

            if (inQuote) {
                addIndent()
                result.append(c)
                if (c == quoteChar) inQuote = false
                i++
                continue
            }

            if (c == '"' || c == '\'') {
                if (inTag) {
                    addIndent()
                    result.append(c)
                    quoteChar = c
                    inQuote = true
                    i++
                    continue
                }
            }

            if (c == '<') {
                if (i + 1 < input.length && input[i + 1] == '/') {
                    indent = (indent - 1).coerceAtLeast(0)
                }
                if (i > 0 && !newLine) {
                    addNewLine()
                }
                inTag = true
                addIndent()
                result.append(c)
                i++
                continue
            }

            if (c == '>') {
                inTag = false
                result.append(c)
                if (i > 0 && input[i - 1] == '/') {
                    addNewLine()
                } else if (i + 1 < input.length && input[i + 1] != '<') {
                    indent++
                    addNewLine()
                } else {
                    indent++
                    addNewLine()
                }
                i++
                continue
            }

            if (c == '\n' || c == '\r') {
                i++
                continue
            }

            addIndent()
            result.append(c)
            i++
        }

        return result.toString().trimEnd() + "\n"
    }
}

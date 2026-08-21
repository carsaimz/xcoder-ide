package com.xcoder.formatter.providers

import com.xcoder.formatter.CodeFormatProvider

class CssFormatter : CodeFormatProvider {
    override fun supports(language: String): Boolean = language in setOf("css", "scss", "less")

    override fun format(code: String, language: String): String {
        val input = code.trim()
        if (input.isBlank()) return ""
        val result = StringBuilder()
        var indent = 0
        var inString = false
        var inComment = false
        val INDENT = "  "
        var i = 0

        fun writeIndent() {
            result.append(INDENT.repeat(indent))
        }

        while (i < input.length) {
            val c = input[i]

            if (inComment) {
                result.append(c)
                if (c == '*' && i + 1 < input.length && input[i + 1] == '/') {
                    result.append('/')
                    i += 2
                    inComment = false
                    continue
                }
                i++
                continue
            }

            if (c == '/' && i + 1 < input.length && input[i + 1] == '*') {
                if (i > 0 && !result.endsWith("\n")) result.append("\n")
                result.append("/*")
                i += 2
                inComment = true
                continue
            }

            if (c == '"' || c == "'".first()) {
                inString = !inString
                result.append(c)
                i++
                continue
            }

            if (inString) {
                result.append(c)
                i++
                continue
            }

            if (c == '{') {
                result.append(" {\n")
                indent++
                i++
                continue
            }

            if (c == '}') {
                indent = (indent - 1).coerceAtLeast(0)
                result.append("\n")
                writeIndent()
                result.append("}\n")
                i++
                continue
            }

            if (c == ';') {
                result.append(";\n")
                if (i + 1 < input.length && input[i + 1] != '}') {
                    writeIndent()
                }
                i++
                continue
            }

            if (c == '\n' || c == '\r') {
                i++
                continue
            }

            if (c == ' ' || c == '\t') {
                if (i + 1 < input.length && input[i + 1] == '{') {
                    i++
                    continue
                }
            }

            result.append(c)
            i++
        }

        return result.toString().trimEnd() + "\n"
    }
}

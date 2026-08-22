package com.xcoder.formatter.providers

import com.xcoder.formatter.CodeFormatProvider

class HtmlFormatter : CodeFormatProvider {
    override fun supports(language: String): Boolean = language in setOf("html", "htm")

    override fun format(code: String, language: String): String {
        val input = code.trim()
        if (input.isBlank()) return ""
        val voidElements = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
        val result = StringBuilder()
        var indent = 0
        var i = 0
        var inTag = false
        var inScript = false
        var inStyle = false
        var inPre = false
        var inQuote = false
        var quoteChar = '\u0000'
        val INDENT = "  "

        fun writeIndent() {
            result.append(INDENT.repeat(indent))
        }

        while (i < input.length) {
            val c = input[i]

            if (inQuote) {
                result.append(c)
                if (c == quoteChar) inQuote = false
                i++
                continue
            }

            if (inScript || inStyle || inPre) {
                result.append(c)
                val closeTag = when {
                    inScript -> "</script>"
                    inStyle -> "</style>"
                    inPre -> "</pre>"
                    else -> ""
                }
                if (i >= closeTag.length - 1) {
                    val recent = input.substring(i - closeTag.length + 1, i + 1).lowercase()
                    if (recent == closeTag) {
                        inScript = false
                        inStyle = false
                        inPre = false
                    }
                }
                i++
                continue
            }

            if (c == '<') {
                if (i + 1 < input.length && input[i + 1] == '/') {
                    indent = (indent - 1).coerceAtLeast(0)
                    if (i > 0 && input[i - 1] != '\n') result.append("\n")
                    writeIndent()
                } else {
                    if (i > 0 && input[i - 1] != '\n' && input[i - 1] != '<') result.append("\n")
                    writeIndent()
                }
                inTag = true
                result.append(c)
                i++
                continue
            }

            if (inTag) {
                if (c == '"' || c == '\'') {
                    inQuote = true
                    quoteChar = c
                }
                result.append(c)
                if (c == '>') {
                    inTag = false
                    val tagContent = result.substring(result.lastIndexOf('<')).lowercase()
                    val tagName = tagContent.substringAfter('<').substringBefore(' ').substringBefore('>').substringBefore('/')
                    if (tagContent.startsWith("<!")) {
                    } else if (tagContent.startsWith("</")) {
                    } else if (voidElements.contains(tagName)) {
                    } else if (tagName == "script") {
                        inScript = true
                        indent++
                    } else if (tagName == "style") {
                        inStyle = true
                        indent++
                    } else if (tagName == "pre") {
                        inPre = true
                        indent++
                    } else {
                        indent++
                    }
                    result.append("\n")
                }
                i++
                continue
            }

            if (c == '\n' || c == '\r') {
                i++
                continue
            }

            if (c.isWhitespace()) {
                i++
                continue
            }

            if (!inTag) {
                writeIndent()
                while (i < input.length && input[i] != '<') {
                    if (input[i] == '&') {
                        var j = i + 1
                        while (j < input.length && input[j] != ';' && input[j] != '<') j++
                        if (j < input.length && input[j] == ';') j++
                        result.append(input.substring(i, j))
                        i = j
                    } else {
                        result.append(input[i])
                        i++
                    }
                }
                result.append("\n")
            } else {
                result.append(c)
                i++
            }
        }

        return result.toString().trimEnd() + "\n"
    }
}

package com.xcoder.formatter.providers

import com.xcoder.formatter.CodeFormatProvider

class JsonFormatter : CodeFormatProvider {
    override fun supports(language: String): Boolean = language == "json"

    override fun format(code: String, language: String): String {
        val input = code.trim()
        if (input.isBlank()) return ""
        return try {
            val parsed = parseValue(input, IntArray(1))
            buildJsonString(parsed, 0)
        } catch (_: Exception) {
            simpleJsonFormat(input)
        }
    }

    private sealed class JsonValue {
        data class Obj(val entries: List<Pair<String, JsonValue>>) : JsonValue()
        data class Arr(val items: List<JsonValue>) : JsonValue()
        data class Str(val value: String) : JsonValue()
        data class Num(val value: String) : JsonValue()
        data class Bool(val value: Boolean) : JsonValue()
        object Null : JsonValue()
    }

    private fun skipWhitespace(s: String, pos: IntArray) {
        while (pos[0] < s.length && s[pos[0]].isWhitespace()) pos[0]++
    }

    private fun parseValue(s: String, pos: IntArray): JsonValue {
        skipWhitespace(s, pos)
        if (pos[0] >= s.length) throw IllegalArgumentException("Unexpected end")
        return when (val c = s[pos[0]]) {
            '{' -> parseObject(s, pos)
            '[' -> parseArray(s, pos)
            '"' -> JsonValue.Str(parseString(s, pos))
            't', 'f' -> parseBool(s, pos)
            'n' -> parseNull(s, pos)
            else -> parseNumber(s, pos)
        }
    }

    private fun parseObject(s: String, pos: IntArray): JsonValue.Obj {
        pos[0]++ // skip '{'
        val entries = mutableListOf<Pair<String, JsonValue>>()
        skipWhitespace(s, pos)
        if (pos[0] < s.length && s[pos[0]] == '}') { pos[0]++; return JsonValue.Obj(entries) }
        while (true) {
            skipWhitespace(s, pos)
            val key = parseString(s, pos)
            skipWhitespace(s, pos)
            if (s[pos[0]] != ':') throw IllegalArgumentException("Expected ':'")
            pos[0]++
            val value = parseValue(s, pos)
            entries.add(key to value)
            skipWhitespace(s, pos)
            if (pos[0] < s.length && s[pos[0]] == ',') { pos[0]++; continue }
            if (pos[0] < s.length && s[pos[0]] == '}') { pos[0]++; break }
            throw IllegalArgumentException("Expected ',' or '}'")
        }
        return JsonValue.Obj(entries)
    }

    private fun parseArray(s: String, pos: IntArray): JsonValue.Arr {
        pos[0]++ // skip '['
        val items = mutableListOf<JsonValue>()
        skipWhitespace(s, pos)
        if (pos[0] < s.length && s[pos[0]] == ']') { pos[0]++; return JsonValue.Arr(items) }
        while (true) {
            items.add(parseValue(s, pos))
            skipWhitespace(s, pos)
            if (pos[0] < s.length && s[pos[0]] == ',') { pos[0]++; continue }
            if (pos[0] < s.length && s[pos[0]] == ']') { pos[0]++; break }
            throw IllegalArgumentException("Expected ',' or ']'")
        }
        return JsonValue.Arr(items)
    }

    private fun parseString(s: String, pos: IntArray): String {
        if (s[pos[0]] != '"') throw IllegalArgumentException("Expected string")
        pos[0]++
        val sb = StringBuilder()
        while (pos[0] < s.length) {
            val c = s[pos[0]]
            if (c == '\\') {
                pos[0]++
                if (pos[0] >= s.length) break
                val escaped = s[pos[0]]
                sb.append(when (escaped) {
                    'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'b' -> '\b'
                    '"' -> '"'; '\\' -> '\\'; '/' -> '/'
                    'u' -> {
                        pos[0] += 4
                        try { Integer.parseInt(s.substring(pos[0] - 3, pos[0] + 1), 16).toChar() }
                        catch (_: Exception) { 'u' }
                    }
                    else -> escaped
                })
                pos[0]++
                continue
            }
            if (c == '"') { pos[0]++; break }
            sb.append(c)
            pos[0]++
        }
        return sb.toString()
    }

    private fun parseBool(s: String, pos: IntArray): JsonValue.Bool {
        val remaining = s.substring(pos[0])
        return if (remaining.startsWith("true")) {
            pos[0] += 4; JsonValue.Bool(true)
        } else if (remaining.startsWith("false")) {
            pos[0] += 5; JsonValue.Bool(false)
        } else {
            throw IllegalArgumentException("Expected boolean")
        }
    }

    private fun parseNull(s: String, pos: IntArray): JsonValue.Null {
        if (s.substring(pos[0]).startsWith("null")) { pos[0] += 4 }
        return JsonValue.Null
    }

    private fun parseNumber(s: String, pos: IntArray): JsonValue.Num {
        val start = pos[0]
        while (pos[0] < s.length && (s[pos[0]].isDigit() || s[pos[0]] in "-.eE+")) pos[0]++
        return JsonValue.Num(s.substring(start, pos[0]))
    }

    private fun buildJsonString(value: JsonValue, indent: Int): String {
        val pad = "    ".repeat(indent)
        val innerPad = "    ".repeat(indent + 1)
        return when (value) {
            is JsonValue.Obj -> {
                if (value.entries.isEmpty()) return "{}"
                val sb = StringBuilder("{\n")
                value.entries.forEachIndexed { index, (key, v) ->
                    sb.append("$innerPad\"${escapeJson(key)}\": ${buildJsonString(v, indent + 1)}")
                    if (index < value.entries.size - 1) sb.append(",")
                    sb.append("\n")
                }
                sb.append("$pad}")
                sb.toString()
            }
            is JsonValue.Arr -> {
                if (value.items.isEmpty()) return "[]"
                val sb = StringBuilder("[\n")
                value.items.forEachIndexed { index, v ->
                    sb.append("$innerPad${buildJsonString(v, indent + 1)}")
                    if (index < value.items.size - 1) sb.append(",")
                    sb.append("\n")
                }
                sb.append("$pad]")
                sb.toString()
            }
            is JsonValue.Str -> "\"${escapeJson(value.value)}\""
            is JsonValue.Num -> value.value
            is JsonValue.Bool -> if (value.value) "true" else "false"
            is JsonValue.Null -> "null"
        }
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun simpleJsonFormat(input: String): String {
        val result = StringBuilder()
        var indent = 0
        var inString = false
        var escape = false
        for (c in input) {
            if (escape) { result.append(c); escape = false; continue }
            if (c == '\\' && inString) { result.append(c); escape = true; continue }
            if (c == '"') { inString = !inString; result.append(c); continue }
            if (inString) { result.append(c); continue }
            when (c) {
                '{', '[' -> { result.append(c); result.append("\n"); indent++; result.append("    ".repeat(indent)) }
                '}', ']' -> { result.append("\n"); indent = (indent - 1).coerceAtLeast(0); result.append("    ".repeat(indent)); result.append(c) }
                ',' -> { result.append(c); result.append("\n"); result.append("    ".repeat(indent)) }
                ':' -> { result.append(": ") }
                ' ', '\n', '\r', '\t' -> {}
                else -> result.append(c)
            }
        }
        return result.toString().trimEnd() + "\n"
    }
}

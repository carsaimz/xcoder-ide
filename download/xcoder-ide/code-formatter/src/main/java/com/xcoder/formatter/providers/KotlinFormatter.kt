package com.xcoder.formatter.providers

import com.xcoder.formatter.CodeFormatProvider

class KotlinFormatter : CodeFormatProvider {
    override fun supports(language: String): Boolean = language in setOf("kotlin", "kts")

    override fun format(code: String, language: String): String {
        val lines = code.split('\n')
        val result = mutableListOf<String>()
        var indent = 0
        val importBlock = mutableListOf<String>()
        val otherLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("import ") -> importBlock.add(trimmed)
                trimmed.isEmpty() && importBlock.isEmpty() -> {}
                else -> otherLines.add(line)
            }
        }

        val sortedImports = importBlock.sorted().distinct().toMutableList()
        var lastImportPackage = ""
        val sortedImportLines = mutableListOf<String>()
        for (imp in sortedImports) {
            val pkg = imp.removePrefix("import ").substringBefore('.')
            if (pkg != lastImportPackage && sortedImportLines.isNotEmpty()) {
                sortedImportLines.add("")
            }
            sortedImportLines.add(imp)
            lastImportPackage = pkg
        }

        for (line in otherLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.add("")
                continue
            }
            val openBraces = trimmed.count { it == '{' }
            val closeBraces = trimmed.count { it == '}' }
            if (trimmed.startsWith("}") || (trimmed.startsWith(")") && closeBraces > openBraces)) {
                indent = (indent - 1).coerceAtLeast(0)
            }
            val leadingSpaces = line.takeWhile { it == ' ' }.length
            if (leadingSpaces == 0) {
                result.add("    ".repeat(indent) + trimmed)
            } else {
                val currentIndent = leadingSpaces / 4
                if (currentIndent != indent) {
                    result.add("    ".repeat(indent) + trimmed)
                } else {
                    result.add("    ".repeat(indent) + trimmed)
                }
            }
            if (openBraces > closeBraces && !trimmed.endsWith("}")) {
                indent += openBraces - closeBraces
            } else if (closeBraces > openBraces && !trimmed.startsWith("}")) {
                indent = (indent - (closeBraces - openBraces)).coerceAtLeast(0)
            }
        }

        val finalLines = mutableListOf<String>()
        if (sortedImportLines.isNotEmpty()) {
            finalLines.addAll(sortedImportLines)
            finalLines.add("")
        }
        finalLines.addAll(result)
        return finalLines.joinToString("\n").trimEnd() + "\n"
    }
}

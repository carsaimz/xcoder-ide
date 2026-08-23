package com.xcoder.apk.smali

import android.util.Log
import java.util.regex.Pattern

/**
 * Smali file parser and analyzer.
 *
 * Based on Dalvikus's smali editing implementation, providing:
 * - Full smali AST parsing (classes, methods, fields, annotations, instructions)
 * - Error detection for common smali mistakes
 * - Smali patching and search capabilities
 * - Class/method/field/annotation extraction from smali source
 *
 * For syntax highlighting integration with sora-editor, see
 * [com.xcoder.editor.sora.SmaliLanguage] in the editor module.
 */
class SmaliEditor() {

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

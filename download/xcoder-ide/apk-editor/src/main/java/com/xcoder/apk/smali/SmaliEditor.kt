package com.xcoder.apk.smali

import android.util.Log
import java.io.*
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmaliEditor @Inject constructor() {

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

    fun parseSmaliFile(content: String): SmaliClass {
        val lines = content.lines()
        var className = ""
        var superName = ""
        var source = ""
        val interfaces = mutableListOf<String>()
        val annotations = mutableListOf<SmaliAnnotation>()
        val fields = mutableListOf<SmaliField>()
        val methods = mutableListOf<SmaliMethod>()
        var currentField: SmaliField? = null
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
                trimmed.startsWith(".field ") -> {
                    currentField = parseFieldLine(trimmed, index)
                    fields.add(currentField!!)
                }
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
                        val parts = code.split("\s+", limit = 2)
                        currentInstructions.add(SmaliInstruction(
                            opcode = parts[0],
                            operands = parts.getOrElse(1) { "" },
                            comment = comment,
                            lineNumber = index + 1
                        ))
                    }
                }
            }
        }

        return SmaliClass(className, superName, source, interfaces, fields, methods, annotations, lines)
    }

    private fun parseFieldLine(line: String, lineNum: Int): SmaliField {
        val parts = line.removePrefix(".field ").split(" ")
        val nameType = parts.firstOrNull() ?: ""
        val colonIdx = nameType.indexOf(':')
        return SmaliField(
            name = if (colonIdx >= 0) nameType.substring(0, colonIdx) else nameType,
            type = if (colonIdx >= 0) nameType.substring(colonIdx) else "",
            accessFlags = parts.filter { it in ACCESS_FLAGS }.joinToString(" "),
            value = parts.find { it.startsWith("=") }?.removePrefix("= ")?.trim(),
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
                'L' -> { val end = desc.indexOf(';', i); if (end >= 0) { types.add(desc.substring(i, end + 1)); i = end + 1 } else i++ }
                '[' -> i++
                else -> { types.add(desc[i].toString()); i++ }
            }
        }
        return types
    }

    fun searchInSmali(content: String, query: String, caseSensitive: Boolean = false): List<SmaliSearchResult> {
        val results = mutableListOf<SmaliSearchResult>()
        val lines = content.lines()
        lines.forEachIndexed { idx, line ->
            val match = if (caseSensitive) line.contains(query) else line.contains(query, ignoreCase = true)
            if (match) results.add(SmaliSearchResult(line, idx + 1))
        }
        return results
    }

    data class SmaliSearchResult(val line: String, val lineNumber: Int)

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
        val pattern = Pattern.compile("invoke-\w+\\s+.*$methodName", Pattern.CASE_INSENSITIVE)
        val results = mutableListOf<SmaliSearchResult>()
        content.lines().forEachIndexed { idx, line ->
            if (pattern.matcher(line.trim()).find()) results.add(SmaliSearchResult(line, idx + 1))
        }
        return results
    }

    fun findStringReferences(content: String, str: String): List<SmaliSearchResult> {
        val results = mutableListOf<SmaliSearchResult>()
        content.lines().forEachIndexed { idx, line ->
            if (line.contains("\"$str\"") || line.contains("const-string", ignoreCase = true) && line.contains(str, ignoreCase = true))
                results.add(SmaliSearchResult(line, idx + 1))
        }
        return results
    }

    companion object {
        val ACCESS_FLAGS = setOf("public", "private", "protected", "static", "final", "abstract", "synchronized", "volatile", "transient", "native", "strictfp")
        const val TAG = "SmaliEditor"
    }
}
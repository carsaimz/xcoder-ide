package com.xcoder.apk.dex

import android.content.Context
import android.util.Log
import dalvik.system.DexFile
import java.io.*
import java.util.zip.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DexEditor @Inject constructor() {

    data class DexClass(
        val name: String,
        val superClass: String = "",
        val interfaces: List<String> = emptyList(),
        val fields: List<DexField> = emptyList(),
        val methods: List<DexMethod> = emptyList(),
        val accessFlags: String = "",
        val sourceFile: String = ""
    )

    data class DexField(
        val name: String,
        val type: String,
        val accessFlags: String = "",
        val initialValue: String? = null
    )

    data class DexMethod(
        val name: String,
        val returnType: String,
        val parameters: List<String> = emptyList(),
        val accessFlags: String = "",
        val bytecodeSize: Int = 0
    )

    data class DexFile(
        val path: String,
        val classes: List<DexClass> = emptyList(),
        val strings: List<String> = emptyList(),
        val totalMethods: Int = 0,
        val totalFields: Int = 0
    )

    data class PatchResult(
        val success: Boolean,
        val patchedPath: String? = null,
        val changesCount: Int = 0,
        val error: String? = null
    )

    fun parseDexFile(dexPath: String): DexFile {
        val classes = mutableListOf<DexClass>()
        val strings = mutableListOf<String>()
        try {
            val dex = DexFile(dexPath)
            val entries = dex.entries() ?: return DexFile(dexPath)
            while (entries.hasMoreElements()) {
                val className = entries.nextElement()
                classes.add(DexClass(
                    name = className.replace('/', '.'),
                    superClass = extractSuperClass(className, dex),
                    accessFlags = extractAccessFlags(className, dex)
                ))
            }
            dex.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DEX: $dexPath", e)
        }
        return DexFile(
            path = dexPath,
            classes = classes.sortedBy { it.name },
            totalMethods = classes.sumOf { it.methods.size },
            totalFields = classes.sumOf { it.fields.size }
        )
    }

    fun searchInDex(dexPath: String, query: String, caseSensitive: Boolean = false): List<DexClass> {
        val dexFile = parseDexFile(dexPath)
        return if (caseSensitive) dexFile.classes.filter { it.name.contains(query) }
        else dexFile.classes.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun extractStringsFromDex(dexPath: String, filter: String? = null): List<String> {
        val strings = mutableListOf<String>()
        try {
            RandomAccessFile(dexPath, "r").use { raf ->
                val bytes = ByteArray(raf.length().toInt())
                raf.readFully(bytes)
                extractStringsFromBytes(bytes, strings, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting strings from DEX", e)
        }
        return strings.distinct().sorted()
    }

    private fun extractStringsFromBytes(bytes: ByteArray, output: MutableList<String>, filter: String?) {
        val sb = StringBuilder()
        var inString = false
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) { sb.append(c.toChar()); inString = true }
            else if (inString && sb.length >= 4) {
                val s = sb.toString()
                if (filter == null || s.contains(filter, ignoreCase = true)) output.add(s)
                sb.clear(); inString = false
            } else { sb.clear(); inString = false }
        }
        if (sb.length >= 4) {
            val s = sb.toString()
            if (filter == null || s.contains(filter, ignoreCase = true)) output.add(s)
        }
    }

    fun decompileToSmali(dexPath: String, outputDir: String): Boolean {
        return try {
            File(outputDir).mkdirs()
            val dexFile = parseDexFile(dexPath)
            for (cls in dexFile.classes) {
                val pkgPath = cls.name.replace('.', '/')
                val smaliFile = File(outputDir, "$pkgPath.smali")
                smaliFile.parentFile?.mkdirs()
                smaliFile.writeText(generateSmaliStub(cls))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error decompiling to smali", e)
            false
        }
    }

    private fun generateSmaliStub(cls: DexClass): String = buildString {
        appendLine(".class ${cls.accessFlags} ${cls.name.replace('.', '/')}")
        if (cls.superClass.isNotBlank()) appendLine(".super ${cls.superClass.replace('.', '/')}")
        if (cls.sourceFile.isNotBlank()) appendLine(".source \"${cls.sourceFile}\"")
        appendLine()
        cls.interfaces.forEach { appendLine(".implements ${it.replace('.', '/')}") }
        if (cls.interfaces.isNotEmpty()) appendLine()
        cls.fields.forEach { f ->
            appendLine(".field ${f.accessFlags} ${f.name}:${f.type}")
            if (f.initialValue != null) appendLine("    = $f.initialValue")
        }
        if (cls.fields.isNotEmpty()) appendLine()
        cls.methods.forEach { m ->
            appendLine(".method ${m.accessFlags} ${m.name}(${m.parameters.joinToString("")})${m.returnType}")
            appendLine("    .registers ${m.parameters.size + 1}")
            appendLine("    return-void")
            appendLine(".end method")
            appendLine()
        }
    }

    fun batchSearchInApk(apkPath: String, query: String): Map<String, List<DexClass>> {
        val results = mutableMapOf<String, List<DexClass>>()
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "xcoder_dex_${System.currentTimeMillis()}")
        try {
            tmpDir.mkdirs()
            extractDexFromApk(apkPath, tmpDir.absolutePath)
            tmpDir.listFiles()?.filter { it.name.endsWith(".dex") }?.forEach { dexFile ->
                results[dexFile.name] = searchInDex(dexFile.absolutePath, query)
            }
        } finally { tmpDir.deleteRecursively() }
        return results
    }

    fun extractDexFromApk(apkPath: String, outputDir: String): List<String> {
        val extracted = mutableListOf<String>()
        ZipFile(apkPath).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.endsWith(".dex") }
                .forEach { entry ->
                    val outFile = File(outputDir, File(entry.name).name)
                    zip.getInputStream(entry).use { inp -> outFile.outputStream().use { out -> inp.copyTo(out) } }
                    extracted.add(outFile.absolutePath)
                }
        }
        return extracted
    }

    private fun extractSuperClass(className: String, dex: DexFile): String = try {
        dex.loadClass(className.replace('.', '/'), null)?.superclass?.name ?: ""
    } catch (_: Exception) { "" }

    private fun extractAccessFlags(className: String, dex: DexFile): String = try {
        val clazz = dex.loadClass(className.replace('.', '/'), null)
        buildString {
            if (java.lang.reflect.Modifier.isPublic(clazz.modifiers)) append("public ")
            if (java.lang.reflect.Modifier.isFinal(clazz.modifiers)) append("final ")
            if (java.lang.reflect.Modifier.isAbstract(clazz.modifiers)) append("abstract ")
        }.trim()
    } catch (_: Exception) { "" }

    companion object { const val TAG = "DexEditor" }
}
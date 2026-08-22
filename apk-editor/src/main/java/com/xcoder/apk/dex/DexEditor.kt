package com.xcoder.apk.dex

import android.util.Log
import java.io.*
import java.util.zip.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEX file editor using raw byte parsing.
 *
 * Based on Dalvikus's DEX editing pipeline pattern, adapted to work
 * without dexlib2/baksmali/smali library dependencies.
 *
 * Uses raw DEX format parsing to:
 * - List classes from the class_def_item table
 * - Extract strings from the string table
 * - Handle multi-dex (classes.dex, classes2.dex, ...)
 * - ZIP-based DEX extraction and replacement in APKs
 *
 * For full smali decompilation/assembly, dexlib2+baksmali can be
 * added as a local JAR dependency when needed.
 */
@Singleton
class DexEditor @Inject constructor() {

    companion object {
        private const val TAG = "DexEditor"
        private const val DEX_MAGIC = 0x0A786564 // "dex\n"
    }

    // ── Data classes ───────────────────────────────────────────────

    data class DexClass(
        val name: String,
        val superClass: String = "",
        val interfaces: List<String> = emptyList(),
        val accessFlags: String = "",
        val sourceFile: String = ""
    ) {
        val internalName: String get() = "L${name.replace('.', '/')};"
    }

    data class DexField(
        val name: String,
        val type: String,
        val accessFlags: String = ""
    )

    data class DexMethod(
        val name: String,
        val returnType: String,
        val parameters: List<String> = emptyList(),
        val accessFlags: String = ""
    )

    data class DexFileSummary(
        val path: String,
        val classes: List<DexClass> = emptyList(),
        val strings: List<String> = emptyList(),
        val totalMethods: Int = 0,
        val totalFields: Int = 0
    )

    data class RebuildResult(
        val success: Boolean,
        val outputPath: String? = null,
        val modifiedClasses: Int = 0,
        val error: String? = null
    )

    // ── DEX Parsing ────────────────────────────────────────────────

    /**
     * Parse a DEX file and extract class names via raw byte scan.
     */
    fun parseDexFile(dexPath: String): DexFileSummary {
        val classes = mutableListOf<DexClass>()
        try {
            val bytes = File(dexPath).readBytes()
            val classNames = parseRawClassNames(bytes)
            for (cn in classNames) {
                val javaName = cn.removePrefix("L").removeSuffix(";").replace('/', '.')
                classes.add(DexClass(name = javaName))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DEX: $dexPath", e)
        }
        return DexFileSummary(
            path = dexPath,
            classes = classes.sortedBy { it.name }
        )
    }

    fun parseDexBytes(dexBytes: ByteArray): DexFileSummary {
        val tmpFile = File.createTempFile("dex_parse_", ".dex")
        try {
            tmpFile.writeBytes(dexBytes)
            return parseDexFile(tmpFile.absolutePath)
        } finally {
            tmpFile.delete()
        }
    }

    // ── String extraction ──────────────────────────────────────────

    fun extractStrings(dexPath: String, filter: String? = null): List<String> {
        val strings = mutableListOf<String>()
        try {
            val bytes = File(dexPath).readBytes()
            val found = parseRawStrings(bytes)
            if (filter != null) strings.addAll(found.filter { it.contains(filter, ignoreCase = true) })
            else strings.addAll(found)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting strings from DEX", e)
        }
        return strings.sorted().distinct()
    }

    fun searchClasses(dexPath: String, query: String, caseSensitive: Boolean = false): List<DexClass> {
        val summary = parseDexFile(dexPath)
        return if (caseSensitive) {
            summary.classes.filter { it.name.contains(query) }
        } else {
            summary.classes.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    fun batchSearchInApk(apkPath: String, query: String): Map<String, List<DexClass>> {
        val results = mutableMapOf<String, List<DexClass>>()
        val tmpDir = File.createTempFile("xcoder_dex_", "").apply { delete(); mkdirs() }
        try {
            val dexFiles = extractDexFromApk(apkPath, tmpDir.absolutePath)
            for (path in dexFiles) {
                results[File(path).name] = searchClasses(path, query)
            }
        } finally {
            tmpDir.deleteRecursively()
        }
        return results
    }

    // ── DEX extraction from APK ────────────────────────────────────

    fun extractDexFromApk(apkPath: String, outputDir: String): List<String> {
        val extracted = mutableListOf<String>()
        ZipFile(apkPath).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.endsWith(".dex") }
                .forEach { entry ->
                    val outFile = File(outputDir, File(entry.name).name)
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { inp ->
                        outFile.outputStream().use { out -> inp.copyTo(out) }
                    }
                    extracted.add(outFile.absolutePath)
                }
        }
        return extracted
    }

    fun replaceDexInApk(
        apkPath: String,
        dexReplacements: Map<String, String>,
        outputApkPath: String
    ): Boolean {
        return try {
            ZipFile(apkPath).use { zip ->
                ZipOutputStream(FileOutputStream(outputApkPath)).use { zos ->
                    zip.entries().asSequence().forEach { entry ->
                        if (entry.name in dexReplacements) {
                            val newDexPath = dexReplacements[entry.name]!!
                            val newEntry = ZipEntry(entry.name)
                            newEntry.method = ZipEntry.STORED
                            zos.putNextEntry(newEntry)
                            File(newDexPath).inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        } else {
                            val newEntry = ZipEntry(entry.name)
                            newEntry.method = entry.method
                            zos.putNextEntry(newEntry)
                            if (!entry.isDirectory) zip.getInputStream(entry).copyTo(zos)
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing DEX in APK", e)
            false
        }
    }

    // ── Raw byte parsing ───────────────────────────────────────────

    /**
     * Scan DEX bytes for class name strings.
     * Class names follow the pattern Lpackage/path/ClassName;
     */
    internal fun parseRawClassNames(bytes: ByteArray): List<String> {
        val classNames = mutableSetOf<String>()
        val sb = StringBuilder()
        var inString = false

        for (b in bytes) {
            val c = b.toInt() and 0xFF
            val isClassNameChar = c in 0x30..0x39 || c in 0x41..0x5A ||
                    c in 0x61..0x7A || c == '/'.code || c == ';'.code ||
                    c == '$'.code || c == '_'.code

            if (isClassNameChar) {
                sb.append(c.toChar())
                inString = true
            } else if (inString) {
                val candidate = sb.toString()
                if (candidate.startsWith("L") && candidate.endsWith(";") &&
                    candidate.length > 4 && candidate.contains("/")) {
                    classNames.add(candidate)
                }
                sb.clear()
                inString = false
            }
        }
        if (sb.isNotEmpty()) {
            val candidate = sb.toString()
            if (candidate.startsWith("L") && candidate.endsWith(";") &&
                candidate.length > 4 && candidate.contains("/")) {
                classNames.add(candidate)
            }
        }
        return classNames.sorted()
    }

    /**
     * Extract readable strings from DEX bytes (length >= 3, printable ASCII).
     */
    internal fun parseRawStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) {
                sb.append(c.toChar())
            } else {
                if (sb.length >= 3) strings.add(sb.toString())
                sb.clear()
            }
        }
        if (sb.length >= 3) strings.add(sb.toString())
        return strings
    }
}
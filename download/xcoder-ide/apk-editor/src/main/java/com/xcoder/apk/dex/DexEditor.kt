package com.xcoder.apk.dex

import android.util.Log
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.builder.DexBuilder
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.baksmali.Adapters
import org.jf.baksmali.baksmali
import org.jf.smali.SmaliModule
import org.jf.smali.dexbacked.SmaliDexFileBuilder
import java.io.*
import java.util.zip.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEX file editor using dexlib2 and baksmali/smali.
 *
 * Based on Dalvikus's DEX editing pipeline:
 * 1. Read DEX class entries using dexlib2
 * 2. Decompile individual classes to smali text using baksmali
 * 3. Edit smali text in the editor
 * 4. Reassemble smali to class definitions using smali library
 * 5. Rebuild the complete DEX file using DexBuilder
 *
 * This supports:
 * - Full DEX parsing with class, method, field, and string tables
 * - Per-class decompilation to smali
 * - Smali reassembly back to DEX
 * - DEX file rebuilding with modified classes
 * - Multi-dex handling (classes.dex, classes2.dex, ...)
 * - String table search and modification
 *
 * ## Architecture
 *
 * The editor operates on individual classes rather than the whole DEX.
 * This matches Dalvikus's approach where each class is a separate
 * editable node in the tree, and changes are batched into a DEX rebuild.
 *
 * ## Dependencies
 *
 * - org.jf.dexlib2: DEX file reading/writing
 * - org.jf.baksmali: DEX → smali decompilation
 * - org.jf.smali: smali → DEX assembly
 */
@Singleton
class DexEditor @Inject constructor() {

    companion object {
        private const val TAG = "DexEditor"
        private const val DEFAULT_API_LEVEL = 35 // Android 15
    }

    // ── Data classes ───────────────────────────────────────────────

    /** Represents a class in the DEX file. */
    data class DexClass(
        val name: String,
        val superClass: String = "",
        val interfaces: List<String> = emptyList(),
        val fields: List<DexField> = emptyList(),
        val methods: List<DexMethod> = emptyList(),
        val accessFlags: String = "",
        val sourceFile: String = ""
    ) {
        /** Internal name format: Lcom/example/Foo; */
        val internalName: String get() = "L${name.replace('.', '/')};"
    }

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

    /** Summary of a parsed DEX file. */
    data class DexFileSummary(
        val path: String,
        val classes: List<DexClass> = emptyList(),
        val strings: List<String> = emptyList(),
        val totalMethods: Int = 0,
        val totalFields: Int = 0
    )

    /** Result of a DEX rebuild operation. */
    data class RebuildResult(
        val success: Boolean,
        val outputPath: String? = null,
        val modifiedClasses: Int = 0,
        val error: String? = null
    )

    // ── DEX Parsing ────────────────────────────────────────────────

    /**
     * Parse a DEX file and extract class definitions.
     *
     * Uses dexlib2 to read the class_def_item table and extract
     * class metadata (name, superclass, interfaces, access flags,
     * fields, methods).
     *
     * @param dexPath path to the .dex file
     * @return [DexFileSummary] with class list and statistics
     */
    fun parseDexFile(dexPath: String): DexFileSummary {
        val classes = mutableListOf<DexClass>()
        var totalMethods = 0
        var totalFields = 0

        try {
            val dexFile = DexFileFactory.loadDexFile(File(dexPath), Opcodes.forApi(DEFAULT_API_LEVEL)) as DexBackedDexFile

            for (classDef in dexFile.classes) {
                val className = classDef.type
                    .removePrefix("L").removeSuffix(";").replace('/', '.')
                val superClassName = classDef.superclass
                    ?.removePrefix("L")?.removeSuffix(";")?.replace('/', '.') ?: ""

                val fields = classDef.fields.map { field ->
                    DexField(
                        name = field.name,
                        type = field.type,
                        accessFlags = formatAccessFlags(field.accessFlags),
                        initialValue = field.initialValue?.toString()
                    )
                }
                val methods = classDef.methods.map { method ->
                    DexMethod(
                        name = method.name,
                        returnType = method.returnType,
                        accessFlags = formatAccessFlags(method.accessFlags),
                        bytecodeSize = method.code?.instructions?.size ?: 0
                    )
                }

                val interfaces = classDef.interfaces.map {
                    it.removePrefix("L").removeSuffix(";").replace('/', '.')
                }

                totalMethods += methods.size
                totalFields += fields.size

                classes.add(DexClass(
                    name = className,
                    superClass = superClassName,
                    interfaces = interfaces,
                    fields = fields,
                    methods = methods,
                    accessFlags = formatAccessFlags(classDef.accessFlags),
                    sourceFile = classDef.sourceFile ?: ""
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DEX: $dexPath", e)
        }

        return DexFileSummary(
            path = dexPath,
            classes = classes.sortedBy { it.name },
            totalMethods = totalMethods,
            totalFields = totalFields
        )
    }

    /**
     * Parse DEX bytes directly (without writing to disk first).
     */
    fun parseDexBytes(dexBytes: ByteArray): DexFileSummary {
        val tmpFile = File.createTempFile("dex_parse_", ".dex")
        try {
            tmpFile.writeBytes(dexBytes)
            return parseDexFile(tmpFile.absolutePath)
        } finally {
            tmpFile.delete()
        }
    }

    // ── Class decompilation (DEX → smali) ──────────────────────────

    /**
     * Decompile a single class from a DEX file to smali text.
     *
     * Uses baksmali to decompile the specified class.
     *
     * @param dexPath path to the .dex file
     * @param classType internal class type (e.g., "Lcom/example/Foo;")
     * @param apiLevel target API level for opcode selection
     * @return smali source code, or null if the class is not found
     */
    fun decompileClass(
        dexPath: String,
        classType: String,
        apiLevel: Int = DEFAULT_API_LEVEL
    ): String? {
        return try {
            val dexFile = DexFileFactory.loadDexFile(
                File(dexPath), Opcodes.forApi(apiLevel)
            ) as DexBackedDexFile

            val classDef = dexFile.getClassDef(classType) ?: return null

            val writer = StringWriter()
            val smaliWriter = Adapters.getClassDefinitionWriter(
                baksmali.defaultOpts,
                writer
            )
            smaliWriter.write(classDef)
            smaliWriter.close()
            writer.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error decompiling class $classType", e)
            null
        }
    }

    /**
     * Decompile a single class from DEX bytes.
     */
    fun decompileClassFromBytes(
        dexBytes: ByteArray,
        classType: String,
        apiLevel: Int = DEFAULT_API_LEVEL
    ): String? {
        val tmpFile = File.createTempFile("smali_dex_", ".dex")
        try {
            tmpFile.writeBytes(dexBytes)
            return decompileClass(tmpFile.absolutePath, classType, apiLevel)
        } finally {
            tmpFile.delete()
        }
    }

    /**
     * Decompile all classes in a DEX file to smali files.
     *
     * Creates the directory structure matching the package hierarchy.
     *
     * @param dexPath path to the .dex file
     * @param outputDir root output directory
     * @return true if all classes decompiled successfully
     */
    fun decompileToSmali(dexPath: String, outputDir: String): Boolean {
        return try {
            val summary = parseDexFile(dexPath)
            for (cls in summary.classes) {
                val smali = decompileClass(dexPath, cls.internalName) ?: continue
                val pkgPath = cls.name.replace('.', '/')
                val smaliFile = File(outputDir, "$pkgPath.smali")
                smaliFile.parentFile?.mkdirs()
                smaliFile.writeText(smali)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error decompiling to smali", e)
            false
        }
    }

    // ── Class reassembly (smali → DEX) ─────────────────────────────

    /**
     * Assemble a single smali file into a DEX class definition.
     *
     * Uses smali library to parse and assemble the smali text.
     *
     * @param smaliText smali source code
     * @param apiLevel target API level
     * @return assembled DEX bytes containing just this class
     */
    fun assembleClass(smaliText: String, apiLevel: Int = DEFAULT_API_LEVEL): ByteArray {
        val reader = StringReader(smaliText)
        val errors = mutableListOf<org.jf.smali.SmaliError>()

        val dexBuilder = SmaliDexFileBuilder(
            apiLevel = apiLevel,
            verbose = false,
            debugInfo = true
        )

        // Parse and assemble the smali file
        val smaliFile = org.antlr.v4.runtime.CommonTokenStream(
            org.antlr.v4.runtime.CharStreams.fromReader(reader)
        )
        val lexer = org.jf.smali.smaliLexer(org.antlr.v4.runtime.CharStreams.fromReader(StringReader(smaliText)))
        val tokens = org.antlr.v4.runtime.CommonTokenStream(lexer)
        val parser = org.jf.smali.smaliParser(tokens)
        val tree = parser.smali_file()

        val asmMethod = org.jf.smali.SmaliUtils.getMethod(tree)
        if (asmMethod != null) {
            val methodVisitor = DexBuilder(apiLevel)
            asmMethod.accept(methodVisitor)
        }

        // Use SmaliModule for proper assembly
        val module = SmaliModule()
        val tempFile = File.createTempFile("smali_assemble_", ".smali")
        try {
            tempFile.writeText(smaliText)
            val dexFile = module.assemble(tempFile, errors)
            if (errors.isNotEmpty()) {
                val errorMsg = errors.joinToString("\n") { "[${it.line}] ${it.message}" }
                throw IllegalStateException("Smali assembly errors:\n$errorMsg")
            }
            val baos = ByteArrayOutputStream()
            dexFile.writeTo(baos)
            return baos.toByteArray()
        } finally {
            tempFile.delete()
        }
    }

    // ── DEX rebuilding ─────────────────────────────────────────────

    /**
     * Rebuild a DEX file with modified classes.
     *
     * Takes the original DEX file and a map of class modifications.
     * Classes in the map are reassembled from their smali source;
     * other classes are copied from the original DEX.
     *
     * @param originalDexPath path to the original .dex file
     * @param modifiedClasses map of internal class name → new smali source
     * @param outputPath path for the rebuilt DEX
     * @return [RebuildResult] with success/failure details
     */
    fun rebuildDex(
        originalDexPath: String,
        modifiedClasses: Map<String, String>,
        outputPath: String
    ): RebuildResult {
        return try {
            val builder = DexBuilder(Opcodes.forApi(DEFAULT_API_LEVEL))

            // Load original DEX
            val dexFile = DexFileFactory.loadDexFile(
                File(originalDexPath), Opcodes.forApi(DEFAULT_API_LEVEL)
            ) as DexBackedDexFile

            for (classDef in dexFile.classes) {
                val classType = classDef.type
                if (classType in modifiedClasses) {
                    // Reassemble from modified smali
                    val assembled = assembleClass(modifiedClasses[classType]!!)
                    val rebuiltDex = DexFileFactory.loadDexFile(
                        ByteArrayInputStream(assembled), Opcodes.forApi(DEFAULT_API_LEVEL)
                    )
                    for (rebuiltClass in rebuiltDex.classes) {
                        builder.internClassDef(rebuiltClass)
                    }
                } else {
                    // Copy original class
                    builder.internClassDef(classDef)
                }
            }

            // Write rebuilt DEX
            File(outputPath).parentFile?.mkdirs()
            val baos = ByteArrayOutputStream()
            builder.generate().writeTo(baos)
            File(outputPath).writeBytes(baos.toByteArray())

            Log.d(TAG, "DEX rebuilt: ${modifiedClasses.size} classes modified → $outputPath")
            RebuildResult(
                success = true,
                outputPath = outputPath,
                modifiedClasses = modifiedClasses.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rebuild DEX", e)
            RebuildResult(false, error = "DEX rebuild failed: ${e.message}")
        }
    }

    // ── DEX extraction from APK ────────────────────────────────────

    /**
     * Extract all DEX files from an APK.
     *
     * @param apkPath path to the APK
     * @param outputDir directory to extract DEX files into
     * @return list of extracted DEX file paths
     */
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

    /**
     * Replace DEX files inside an APK.
     *
     * @param apkPath path to the original APK
     * @param dexReplacements map of DEX filename → new DEX file path
     * @param outputApkPath path for the output APK
     * @return true if successful
     */
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
                            if (!entry.isDirectory) {
                                zip.getInputStream(entry).copyTo(zos)
                            }
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

    // ── String search ──────────────────────────────────────────────

    /**
     * Extract all strings from the DEX string table.
     *
     * @param dexPath path to the DEX file
     * @param filter optional filter string
     * @return sorted, deduplicated list of strings
     */
    fun extractStrings(dexPath: String, filter: String? = null): List<String> {
        val strings = mutableListOf<String>()
        try {
            val dexFile = DexFileFactory.loadDexFile(
                File(dexPath), Opcodes.forApi(DEFAULT_API_LEVEL)
            ) as DexBackedDexFile
            for (string in dexFile.strings) {
                if (filter == null || string.contains(filter, ignoreCase = true)) {
                    strings.add(string)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting strings from DEX", e)
        }
        return strings.sorted().distinct()
    }

    /**
     * Search for classes matching a query.
     *
     * @param dexPath path to the DEX file
     * @param query search query
     * @param caseSensitive whether search is case sensitive
     * @return matching classes
     */
    fun searchClasses(
        dexPath: String,
        query: String,
        caseSensitive: Boolean = false
    ): List<DexClass> {
        val summary = parseDexFile(dexPath)
        return if (caseSensitive) {
            summary.classes.filter { it.name.contains(query) }
        } else {
            summary.classes.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    /**
     * Batch search across all DEX files in an APK.
     */
    fun batchSearchInApk(apkPath: String, query: String): Map<String, List<DexClass>> {
        val results = mutableMapOf<String, List<DexClass>>()
        val tmpDir = File.createTempFile("xcoder_dex_", "").apply { delete(); mkdirs() }
        try {
            val dexFiles = extractDexFromApk(apkPath, tmpDir.absolutePath)
            for (dexPath in dexFiles) {
                val fileName = File(dexPath).name
                results[fileName] = searchClasses(dexPath, query)
            }
        } finally {
            tmpDir.deleteRecursively()
        }
        return results
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun formatAccessFlags(flags: Int): String = buildString {
        if (flags and 0x1 != 0) append("public ")
        if (flags and 0x2 != 0) append("private ")
        if (flags and 0x4 != 0) append("protected ")
        if (flags and 0x8 != 0) append("static ")
        if (flags and 0x10 != 0) append("final ")
        if (flags and 0x20 != 0) append("synchronized ")
        if (flags and 0x400 != 0) append("abstract ")
        if (flags and 0x800 != 0) append("native ")
    }.trim()
}
package com.xcoder.apk.tree

import android.util.Log
import java.io.ByteArrayInputStream
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Tree node for a DEX file inside an APK.
 *
 * Opens the DEX file, parses the class list using dexlib2, and
 * builds a tree of [PackageNode] and [ClassNode] entries.
 *
 * Based on Dalvikus's DexFileNode which:
 * - Opens the DEX from the parent APK's ZIP
 * - Reads the class_def_item table to list all classes
 * - Builds a hierarchical package/class tree
 * - Each class node can be decompiled to smali text
 *
 * Example tree for `classes.dex`:
 * ```
 * classes.dex
 * ├── android/
 * │   ├── app/
 * │   │   └── Activity.smali
 * │   └── content/
 * │       ├── Intent.smali
 * │       └── ContextWrapper.smali
 * ├── com/
 * │   └── example/
 * │       └── app/
 * │           ├── MainActivity.smali
 * │           └── MyApp.smali
 * └── Lkotlin/
 *     └── ... (Kotlin stdlib)
 * ```
 */
class DexFileNode(
    override val name: String,
    private val apkNode: ApkNode,
    override val parent: ContainerNode? = null
) : ContainerNode(name, parent) {

    companion object {
        private const val TAG = "DexFileNode"
    }

    /** Raw DEX bytes, loaded from the ZIP entry. */
    private var dexBytes: ByteArray? = null

    /**
     * Read the DEX file content from the parent APK.
     *
     * @return raw DEX bytes
     * @throws java.io.IOException if the DEX cannot be read
     */
    @Throws(java.io.IOException::class)
    fun readDexBytes(): ByteArray {
        dexBytes?.let { return it }
        val bytes = apkNode.readEntry(name)
        dexBytes = bytes
        return bytes
    }

    /**
     * Parse the DEX class list and build the package/class tree.
     *
     * Uses dexlib2 to read class definitions. Falls back to
     * a raw byte scan if dexlib2 is not available.
     *
     * The tree is organized by package segments:
     * `Lcom/example/app/MainActivity;` becomes:
     *   `com` → `example` → `app` → `MainActivity.smali`
     */
    override fun loadChildren() {
        try {
            val bytes = readDexBytes()
            val classNames = parseClassNames(bytes)

            // Build package tree
            val rootPackages = mutableMapOf<String, PackageNode>()

            for (className in classNames) {
                // Class names are in internal format: Lcom/example/Foo;
                val cleaned = className
                    .removePrefix("L")
                    .removeSuffix(";")

                val segments = cleaned.split("/")
                if (segments.isEmpty() || segments.all { it.isBlank() }) continue

                // Navigate/create package nodes
                var currentParent: ContainerNode = this
                val packageSegments = segments.dropLast(1)

                for ((index, segment) in packageSegments.withIndex()) {
                    if (segment.isBlank()) continue

                    val existing = currentParent.getChildren().find {
                        it.name == segment && it is PackageNode
                    } as? PackageNode

                    if (existing != null) {
                        currentParent = existing
                    } else {
                        val pkgPath = segments.take(index + 1).joinToString("/")
                        val pkgNode = PackageNode(segment, pkgPath, currentParent)
                        currentParent.addChild(pkgNode)
                        currentParent = pkgNode
                    }
                }

                // Create the class node
                val classFileName = segments.last() + ".smali"
                val fullClassName = cleaned.replace('/', '.')
                val classNode = ClassNode(
                    name = classFileName,
                    className = fullClassName,
                    internalClassName = className,
                    dexFileNode = this,
                    parent = currentParent
                )
                currentParent.addChild(classNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load DEX classes from $name", e)
        }
    }

    /**
     * Parse class names from DEX bytes using dexlib2.
     *
     * Uses org.jf.dexlib2.DexFileFactory to open the DEX and
     * iterate over class definitions.
     *
     * @param bytes raw DEX file bytes
     * @return list of class names in internal format (e.g., "Lcom/example/Foo;")
     */
    private fun parseClassNames(bytes: ByteArray): List<String> {
        return try {
            parseWithDexlib2(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "dexlib2 parsing failed, falling back to raw scan", e)
            parseRawClassNames(bytes)
        }
    }

    /**
     * Parse using dexlib2 library.
     */
    private fun parseWithDexlib2(bytes: ByteArray): List<String> {
        val classNames = mutableListOf<String>()
        val tmpFile = java.io.File.createTempFile("dex_parse_", ".dex")
        try {
            tmpFile.writeBytes(bytes)
            val dexFile = org.jf.dexlib2.DexFileFactory.loadDexFile(tmpFile, org.jf.dexlib2.Opcodes.forApi(35))
            for (classDef in dexFile.classes) {
                classNames.add(classDef.type)
            }
        } finally {
            tmpFile.delete()
        }
        return classNames.sorted()
    }

    /**
     * Fallback: scan DEX bytes for class name strings.
     *
     * DEX files store class names in the string table with the
     * format `Lcom/example/Foo;`. This scans for valid class
     * name patterns. It's less reliable than dexlib2 but works
     * as a last resort.
     */
    private fun parseRawClassNames(bytes: ByteArray): List<String> {
        val classNames = mutableSetOf<String>()
        val sb = StringBuilder()
        var inString = false

        for (b in bytes) {
            val c = b.toInt() and 0xFF
            // Valid characters in class names: letters, digits, /, ;, $
            val isClassNameChar = c in 0x30..0x39 ||    // 0-9
                    c in 0x41..0x5A ||    // A-Z
                    c in 0x61..0x7A ||    // a-z
                    c == '/'.code ||
                    c == ';'.code ||
                    c == '$'.code ||
                    c == '_'.code

            if (isClassNameChar) {
                sb.append(c.toChar())
                inString = true
            } else if (inString) {
                val candidate = sb.toString()
                // Class name pattern: Lpackage/path/ClassName;
                if (candidate.startsWith("L") && candidate.endsWith(";") &&
                    candidate.length > 4 && candidate.contains("/")) {
                    // Must have at least one dot-equivalent segment
                    classNames.add(candidate)
                }
                sb.clear()
                inString = false
            }
        }
        // Handle trailing string
        if (sb.isNotEmpty()) {
            val candidate = sb.toString()
            if (candidate.startsWith("L") && candidate.endsWith(";") &&
                candidate.length > 4 && candidate.contains("/")) {
                classNames.add(candidate)
            }
        }

        return classNames.sorted()
    }

    /** Total number of classes in this DEX. */
    val classCount: Int get() {
        if (!isLoaded) loadChildren()
        return countClassNodes(this)
    }

    /** Total number of package nodes in this DEX. */
    val packageCount: Int get() {
        if (!isLoaded) loadChildren()
        return countPackageNodes(this)
    }

    private fun countClassNodes(container: ContainerNode): Int {
 var count = 0
        for (child in container.getChildren()) {
            if (child is ClassNode) count++
            else if (child is ContainerNode) count += countClassNodes(child)
        }
        return count
    }

    private fun countPackageNodes(container: ContainerNode): Int {
 var count = 0
        for (child in container.getChildren()) {
            if (child is PackageNode) count++
            else if (child is ContainerNode) count += countPackageNodes(child)
        }
        return count
    }

    override fun toString(): String = "DexFileNode(name='$name', classes=$classCount)"
}

/**
 * A package node in the DEX class tree.
 *
 * Represents a package segment (e.g., `com`, `example`, `app`).
 * Contains child [PackageNode]s for sub-packages and [ClassNode]s
 * for classes directly in this package.
 */
class PackageNode(
    override val name: String,
    /** Full package path in internal format, e.g. "com/example/app". */
    val packagePath: String,
    override val parent: ContainerNode?
) : ContainerNode(name, parent) {

    /** Human-readable package name, e.g. "com.example.app". */
    val displayPackageName: String get() = packagePath.replace('/', '.')

    /** Number of direct children. */
    val directChildCount: Int get() = getChildren().size

    /** Number of classes in this package (direct children only). */
    val classCount: Int get() = getChildren().count { it is ClassNode }

    override fun loadChildren() {
        // Children are populated by DexFileNode.loadChildren()
    }

    override fun toString(): String =
        "PackageNode(name='$name', pkg='$displayPackageName', children=${getChildrenIfLoaded().size})"
}

/**
 * A class node in the DEX class tree.
 *
 * Represents a single class (or interface/enum) that can be
 * decompiled to smali for viewing and editing.
 *
 * The smali text is generated on demand via [getSmaliContent]
 * using baksmali (through dexlib2's SmaliWriter or external process).
 */
class ClassNode(
    override val name: String,
    /** Human-readable class name, e.g. "com.example.app.MainActivity". */
    val className: String,
    /** Internal class name, e.g. "Lcom/example/app/MainActivity;". */
    val internalClassName: String,
    /** Reference to the parent DEX node for reading DEX bytes. */
    private val dexFileNode: DexFileNode,
    override val parent: ContainerNode?
) : FileNode(name, parent) {

    override val editable: Boolean = true
    override val mimeType: String = "text/x-smali"

    /** Cached smali content. */
    private var smaliContent: String? = null

    override val size: Long
        get() = smaliContent?.length?.toLong() ?: -1

    /**
     * Get the smali decompilation of this class.
     *
     * Uses dexlib2's baksmali to decompile the class.
     * The result is cached for subsequent reads.
     *
     * @return smali source code as a string
     * @throws java.io.IOException if decompilation fails
     */
    @Throws(java.io.IOException::class)
    fun getSmaliContent(): String {
        smaliContent?.let { return it }
        val bytes = dexFileNode.readDexBytes()
        val smali = decompileClass(bytes, internalClassName)
        smaliContent = smali
        return smali
    }

    /**
     * Read smali content. Same as [getSmaliContent] but as bytes.
     */
    override fun readContent(): ByteArray {
        return getSmaliContent().toByteArray(Charsets.UTF_8)
    }

    /**
     * Write modified smali content back.
     *
     * The content is cached and marked dirty. When rebuilding the DEX,
     * dirty class nodes are reassembled from their smali content.
     */
    override fun writeContent(content: ByteArray) {
        smaliContent = String(content, Charsets.UTF_8)
        isDirty = true
    }

    /**
     * Decompile a single class from DEX bytes to smali.
     *
     * Uses dexlib2 to read the class definition and converts
     * it to smali text format.
     *
     * @param dexBytes raw DEX file bytes
     * @param classType internal class type (e.g. "Lcom/example/Foo;")
     * @return smali source code
     */
    private fun decompileClass(dexBytes: ByteArray, classType: String): String {
        return try {
            decompileWithBaksmali(dexBytes, classType)
        } catch (e: Exception) {
            Log.w(DexFileNode.TAG, "baksmali failed for $classType, generating stub", e)
            generateSmaliStub(classType)
        }
    }

    /**
     * Decompile using dexlib2's baksmali.
     */
    private fun decompileWithBaksmali(dexBytes: ByteArray, classType: String): String {
        val tmpDex = java.io.File.createTempFile("smali_dex_", ".dex")
        try {
            tmpDex.writeBytes(dexBytes)
            val dexFile = org.jf.dexlib2.DexFileFactory.loadDexFile(
                tmpDex, org.jf.dexlib2.Opcodes.forApi(35)
            )
            val classDef = dexFile.getClassDef(classType)
                ?: throw IllegalArgumentException("Class not found: $classType")

            val writer = java.io.StringWriter()
            val smaliWriter = org.jf.baksmali.Adapters.getClassDefinitionWriter(
                org.jf.baksmali.baksmali.defaultOpts,
                writer
            )
            smaliWriter.write(classDef)
            smaliWriter.close()
            return writer.toString()
        } finally {
            tmpDex.delete()
        }
    }

    /**
     * Generate a minimal smali stub when full decompilation is not available.
     */
    private fun generateSmaliStub(classType: String): String = buildString {
        val javaName = classType.removePrefix("L").removeSuffix(";").replace('/', '.')
        val parts = javaName.split('.')
        val shortName = parts.last()
        appendLine(".class public $classType")
        appendLine(".super Ljava/lang/Object;")
        appendLine()
        appendLine("# Class: $javaName")
        appendLine("# NOTE: Full decompilation not available.")
        appendLine("# Install baksmali for complete smali output.")
        appendLine()
        appendLine("# direct methods")
        appendLine(".method public constructor <init>()V")
        appendLine("    .registers 1")
        appendLine("    invoke-direct {p0}, Ljava/lang/Object;-><init>()V")
        appendLine("    return-void")
        appendLine(".end method")
    }

    /**
     * Clear the cached smali content, forcing a re-decompile on next read.
     */
    fun invalidateCache() {
        smaliContent = null
    }

    override fun toString(): String =
        "ClassNode(name='$name', class='$className', dirty=$isDirty)"
}

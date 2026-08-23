package com.xcoder.apk.tree

import android.util.Log
import java.util.zip.ZipFile

/**
 * Tree node for a DEX file inside an APK.
 *
 * Parses the class list using raw byte scanning and builds
 * a tree of [PackageNode] and [ClassNode] entries.
 *
 * Based on Dalvikus's DexFileNode pattern.
 */
class DexFileNode(
    override val name: String,
    private val apkNode: ApkNode,
    override val parent: ContainerNode? = null
) : ContainerNode(name, parent) {

    companion object {
        private const val TAG = "DexFileNode"
    }

    private var dexBytes: ByteArray? = null

    @Throws(java.io.IOException::class)
    fun readDexBytes(): ByteArray {
        dexBytes?.let { return it }
        val bytes = apkNode.readEntry(name)
        dexBytes = bytes
        return bytes
    }

    override fun loadChildren() {
        try {
            val bytes = readDexBytes()
            val classNames = parseRawClassNames(bytes)

            for (className in classNames) {
                val cleaned = className.removePrefix("L").removeSuffix(";")
                val segments = cleaned.split("/")
                if (segments.isEmpty() || segments.all { it.isBlank() }) continue

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
     * Scan DEX bytes for class name strings matching Lpackage/path/ClassName;
     */
    private fun parseRawClassNames(bytes: ByteArray): List<String> {
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

    val classCount: Int get() {
        if (!isLoaded) loadChildren()
        return countNodesOfType<ClassNode>(this)
    }

    val packageCount: Int get() {
        if (!isLoaded) loadChildren()
        return countNodesOfType<PackageNode>(this)
    }

    private fun <T : Node> countNodesOfType(container: ContainerNode): Int {
        var count = 0
        for (child in container.getChildren()) {
            if (child is T) count++
            else if (child is ContainerNode) count += countNodesOfType<T>(child)
        }
        return count
    }

    override fun toString(): String = "DexFileNode(name='$name', classes=$classCount)"
}

/**
 * A package node in the DEX class tree.
 */
class PackageNode(
    override val name: String,
    val packagePath: String,
    override val parent: ContainerNode?
) : ContainerNode(name, parent) {
    val displayPackageName: String get() = packagePath.replace('/', '.')
    val directChildCount: Int get() = getChildren().size
    val classCount: Int get() = getChildren().count { it is ClassNode }

    override fun loadChildren() { /* populated by DexFileNode */ }
    override fun toString(): String =
        "PackageNode(name='$name', pkg='$displayPackageName', children=${getChildrenIfLoaded().size})"
}

/**
 * A class node representing a single .smali file.
 *
 * Generates a stub smali output for viewing/editing.
 * Full decompilation requires baksmali (not bundled).
 */
class ClassNode(
    override val name: String,
    val className: String,
    val internalClassName: String,
    private val dexFileNode: DexFileNode,
    override val parent: ContainerNode?
) : FileNode(name, parent) {

    override val editable: Boolean = true
    override val mimeType: String = "text/x-smali"
    private var smaliContent: String? = null

    override val size: Long
        get() = smaliContent?.length?.toLong() ?: -1

    fun getSmaliContent(): String {
        smaliContent?.let { return it }
        val stub = generateSmaliStub(internalClassName)
        smaliContent = stub
        return stub
    }

    override fun readContent(): ByteArray = getSmaliContent().toByteArray(Charsets.UTF_8)

    override fun writeContent(content: ByteArray) {
        smaliContent = String(content, Charsets.UTF_8)
        isDirty = true
    }

    private fun generateSmaliStub(classType: String): String = buildString {
        val javaName = classType.removePrefix("L").removeSuffix(";").replace('/', '.')
        appendLine(".class public $classType")
        appendLine(".super Ljava/lang/Object;")
        appendLine()
        appendLine("# Class: $javaName")
        appendLine("# NOTE: Full decompilation requires baksmali/dexlib2.")
        appendLine("# Raw class browsing is available via DEX byte scanning.")
        appendLine()
        appendLine("# direct methods")
        appendLine(".method public constructor <init>()V")
        appendLine("    .registers 1")
        appendLine("    invoke-direct {p0}, Ljava/lang/Object;-><init>()V")
        appendLine("    return-void")
        appendLine(".end method")
    }

    fun invalidateCache() { smaliContent = null }

    override fun toString(): String =
        "ClassNode(name='$name', class='$className', dirty=$isDirty)"
}

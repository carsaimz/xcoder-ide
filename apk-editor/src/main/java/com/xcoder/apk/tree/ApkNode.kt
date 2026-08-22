package com.xcoder.apk.tree

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root tree node for an APK file.
 *
 * Opens the APK as a ZIP archive, lists all entries, and creates
 * typed child nodes based on file extension and content detection.
 *
 * Based on Dalvikus's ApkNode which:
 * - Opens APK as ZIP and iterates entries
 * - Detects DEX files (classes.dex, classes2.dex, ...)
 * - Detects binary XML files (AndroidManifest.xml, layout/*.xml)
 * - Detects resource.arsc for the compiled resource table
 * - Creates appropriate typed nodes for each category
 *
 * Typical tree structure:
 * ```
 * app.apk
 * ├── AndroidManifest.xml          (BinaryXmlNode)
 * ├── classes.dex                   (DexFileNode)
 * ├── classes2.dex                  (DexFileNode)
 * ├── resources.arsc                (ResourceArscNode)
 * ├── res/
 * │   ├── layout/
 * │   │   ├── activity_main.xml     (ZipEntryFileNode)
 * │   │   └── fragment_home.xml     (ZipEntryFileNode)
 * │   ├── drawable/
 * │   ├── values/
 * │   └── ...
 * ├── lib/
 * │   ├── arm64-v8a/
 * │   │   └── libnative.so
 * │   └── armeabi-v7a/
 * ├── META-INF/
 * │   ├── MANIFEST.MF
 * │   └── *.RSA / *.DSA / *.EC
 * └── assets/
 * ```
 */
class ApkNode(
    override val name: String,
    private val apkPath: String,
    override val parent: ContainerNode? = null
) : ContainerNode(name, parent) {

    companion object {
        private const val TAG = "ApkNode"

        /** Entry names that are DEX files. */
        private val DEX_PATTERN = Regex("^classes(\\d*)\\.dex$")

        /** Entry names known to be binary XML. */
        private val BINARY_XML_NAMES = setOf(
            "AndroidManifest.xml"
        )

        /** Directories that contain binary XML (Android compiled XML). */
        private val BINARY_XML_DIRS = setOf(
            "res/layout", "res/layout-",
            "res/layout-v", "res/layout-land",
            "res/layout-port", "res/layout-sw"
        )
    }

    /** Path to the APK file on disk. */
    val filePath: String get() = apkPath

    /** Whether the APK file exists and is readable. */
    val isValid: Boolean get() = File(apkPath).exists() && File(apkPath).canRead()

    /** APK file size in bytes. */
    val apkSize: Long get() = File(apkPath).length()

    /** Cached ZIP file reference. Closed when [close] is called. */
    private var zipFile: ZipFile? = null

    /**
     * Get or open the underlying ZIP file.
     *
     * @throws java.io.IOException if the file cannot be opened
     */
    @Throws(java.io.IOException::class)
    fun getZipFile(): ZipFile {
        if (zipFile == null) {
            zipFile = ZipFile(apkPath)
        }
        return zipFile!!
    }

    /**
     * Read raw bytes of a ZIP entry.
     *
     * @param entryPath the full path within the ZIP (e.g., "classes.dex")
     * @return the entry's content as bytes
     * @throws java.io.IOException if the entry cannot be read
     */
    @Throws(java.io.IOException::class)
    fun readEntry(entryPath: String): ByteArray {
        val zip = getZipFile()
        val entry = zip.getEntry(entryPath)
            ?: throw java.io.FileNotFoundException("Entry not found: $entryPath")
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    /**
     * Close the underlying ZIP file and release resources.
     */
    fun close() {
        try {
            zipFile?.close()
        } catch (_: Exception) {}
        zipFile = null
    }

    override fun loadChildren() {
        try {
            val zip = getZipFile()
            val entries = zip.entries()
            val directoryNodes = mutableMapOf<String, ContainerNode>()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val path = entry.name

                if (entry.isDirectory) {
                    // Create directory container nodes lazily
                    ensureDirectoryExists(path, directoryNodes)
                    continue
                }

                // Determine parent directory
                val lastSlash = path.lastIndexOf('/')
                val parentPath = if (lastSlash >= 0) path.substring(0, lastSlash + 1) else ""
                val parentDir = if (parentPath.isNotEmpty()) {
                    ensureDirectoryExists(parentPath, directoryNodes)
                } else {
                    this
                }

                // Determine file node type and create child
                val fileName = if (lastSlash >= 0) path.substring(lastSlash + 1) else path
                val childNode = createNodeForEntry(fileName, path, entry, parentDir)
                if (childNode != null) {
                    parentDir.addChild(childNode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load APK entries from $apkPath", e)
        }
    }

    /**
     * Ensure a directory node exists for the given path, creating
     * intermediate container nodes as needed.
     */
    private fun ensureDirectoryExists(
        dirPath: String,
        directoryMap: MutableMap<String, ContainerNode>
    ): ContainerNode {
        // Normalize: remove trailing slash
        val normalized = dirPath.removeSuffix("/")
        if (normalized.isEmpty()) return this

        directoryMap[normalized]?.let { return it }

        val lastSlash = normalized.lastIndexOf('/')
        val parentPath = if (lastSlash >= 0) normalized.substring(0, lastSlash + 1) else ""
        val parentNode = if (parentPath.isNotEmpty()) {
            ensureDirectoryExists(parentPath, directoryMap)
        } else {
            this
        }

        val dirName = if (lastSlash >= 0) normalized.substring(lastSlash + 1) else normalized
        val dirNode = ZipDirectoryNode(dirName, normalized, parentNode)
        directoryMap[normalized] = dirNode
        parentNode.addChild(dirNode)
        return dirNode
    }

    /**
     * Create the appropriate node type for a ZIP entry.
     *
     * Detection priority:
     * 1. DEX files → [DexFileNode]
     * 2. Binary XML (manifest, layouts) → [BinaryXmlNode]
     * 3. resource.arsc → [ResourceArscNode]
     * 4. Everything else → [ZipEntryFileNode]
     */
    private fun createNodeForEntry(
        fileName: String,
        fullPath: String,
        zipEntry: ZipEntry,
        parent: ContainerNode
    ): FileNode? {
        // Skip META-INF signature files from tree (they're not interesting to browse)
        // but keep MANIFEST.MF
        if (fullPath.startsWith("META-INF/") &&
            !fullPath.endsWith("MANIFEST.MF") &&
            (fullPath.endsWith(".RSA") || fullPath.endsWith(".DSA") ||
             fullPath.endsWith(".EC") || fullPath.endsWith(".SF"))
        ) {
            return null
        }

        return when {
            // DEX files: classes.dex, classes2.dex, etc.
            DEX_PATTERN.matches(fileName) -> {
                DexFileNode(fileName, this, parent)
            }

            // Binary XML: AndroidManifest.xml is always binary in APK
            BINARY_XML_NAMES.contains(fileName) -> {
                BinaryXmlNode(fileName, fullPath, this, parent)
            }

            // Layout XML in res/ is compiled (binary XML)
            isBinaryXmlPath(fullPath) -> {
                BinaryXmlNode(fileName, fullPath, this, parent)
            }

            // Compiled resource table
            fileName == "resources.arsc" -> {
                ResourceArscNode(fileName, this, parent)
            }

            // Everything else: raw file from ZIP
            else -> {
                ZipEntryFileNode(fileName, fullPath, this, parent, editable = true)
            }
        }
    }

    /**
     * Check if a path points to a binary XML file.
     *
     * In APKs, XML files under `res/` directories are compiled into
     * binary XML format (AXML). Files in `assets/` or the root are
     * typically plain text XML.
     */
    private fun isBinaryXmlPath(path: String): Boolean {
        if (!path.startsWith("res/")) return false
        if (!path.endsWith(".xml")) return false
        // Any XML under res/ is binary XML in a compiled APK
        return true
    }

    /**
     * List all DEX file nodes in this APK.
     */
    fun getDexNodes(): List<DexFileNode> {
        return getChildren().filterIsInstance<DexFileNode>()
    }

    /**
     * List all binary XML nodes.
     */
    fun getBinaryXmlNodes(): List<BinaryXmlNode> {
        val result = mutableListOf<BinaryXmlNode>()
        collectChildrenOfType(this, result)
        return result
    }

    private fun collectChildrenOfType(
        container: ContainerNode,
        out: MutableList<BinaryXmlNode>
    ) {
        for (child in container.getChildren()) {
            when (child) {
                is BinaryXmlNode -> out.add(child)
                is ContainerNode -> collectChildrenOfType(child, out)
            }
        }
    }
}

/**
 * A directory node within the APK's ZIP structure.
 *
 * Represents a ZIP directory entry (e.g., `res/layout/`).
 */
class ZipDirectoryNode(
    override val name: String,
    private val zipPath: String,
    override val parent: ContainerNode?
) : ContainerNode(name, parent) {

    /** The path of this directory within the ZIP. */
    val directoryPath: String get() = zipPath

    override fun loadChildren() {
        // Children are populated by ApkNode.loadChildren()
        // This is a no-op for ZIP directory nodes
    }
}

/**
 * A file node backed by a ZIP entry.
 *
 * Reads content from the parent APK's ZIP file. Supports
 * editing (content is cached in memory and can be written back).
 */
class ZipEntryFileNode(
    override val name: String,
    private val entryPath: String,
    private val apkNode: ApkNode,
    override val parent: ContainerNode?,
    override val editable: Boolean = true
) : FileNode(name, parent) {

    /** Cached content, set after first read or after write. */
    private var cachedContent: ByteArray? = null

    override val size: Long
        get() = cachedContent?.size?.toLong()
            ?: try {
                val zip = apkNode.getZipFile()
                zip.getEntry(entryPath)?.size ?: -1
            } catch (_: Exception) { -1 }

    override fun readContent(): ByteArray {
        cachedContent?.let { return it }
        val bytes = apkNode.readEntry(entryPath)
        cachedContent = bytes
        return bytes
    }

    override fun writeContent(content: ByteArray) {
        check(editable) { "ZipEntryFileNode '$name' is not editable" }
        cachedContent = content
        isDirty = true
    }

    override fun toString(): String =
        "ZipEntryFileNode(name='$name', entry='$entryPath', size=$size)"
}

/**
 * A node for binary XML files in the APK.
 *
 * Binary XML is Android's compiled XML format (AXML). These files
 * need to be decoded to plain XML for editing, then re-encoded.
 *
 * Examples: AndroidManifest.xml, res/layout/*.xml, res/values/*.xml
 */
class BinaryXmlNode(
    override val name: String,
    private val entryPath: String,
    private val apkNode: ApkNode,
    override val parent: ContainerNode?
) : FileNode(name, parent) {

    override val editable: Boolean = true
    override val mimeType: String = "application/xml"

    private var decodedXml: String? = null
    private var rawBytes: ByteArray? = null

    override val size: Long
        get() = rawBytes?.size?.toLong()
            ?: try {
                val zip = apkNode.getZipFile()
                zip.getEntry(entryPath)?.size ?: -1
            } catch (_: Exception) { -1 }

    override fun readContent(): ByteArray {
        // Return raw binary XML bytes
        rawBytes?.let { return it }
        val bytes = apkNode.readEntry(entryPath)
        rawBytes = bytes
        return bytes
    }

    /**
     * Decode the binary XML to human-readable plain XML.
     *
     * Uses a simple AXML decoder that reads the binary format.
     * In production, this would use Android's XmlBlock or a
     * third-party AXML decoder (e.g., androguard, apktool).
     *
     * @return decoded XML string, or the raw bytes as a string fallback
     */
    fun decodeToXml(): String {
        decodedXml?.let { return it }
        val bytes = readContent()
        // Try to detect if this is actually binary XML
        val decoded = tryDecodeAxml(bytes)
        decodedXml = decoded
        return decoded
    }

    override fun writeContent(content: ByteArray) {
        rawBytes = content
        decodedXml = null
        isDirty = true
    }

    /**
     * Write decoded XML back. The XML will need to be re-encoded
     * to binary format when rebuilding the APK.
     */
    fun writeDecodedXml(xml: String) {
        decodedXml = xml
        isDirty = true
    }

    /**
     * Simple binary XML detection and decoding.
     *
     * Binary XML starts with the chunk type 0x00080003 (RES_XML_TYPE).
     * If the file doesn't start with this magic, it's likely plain XML.
     */
    private fun tryDecodeAxml(bytes: ByteArray): String {
        if (bytes.size < 8) return String(bytes, Charsets.UTF_8)
        // Check for binary XML magic: little-endian 0x00080003
        val chunkType = (bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)
        if (chunkType == 0x00080003) {
            // This is binary XML — return a placeholder indicating
            // the file needs a proper AXML decoder
            return "<!-- Binary XML (AXML) - requires AXML decoder -->\n" +
                   "<!-- Original size: ${bytes.size} bytes -->\n"
        }
        // Plain text XML
        return String(bytes, Charsets.UTF_8)
    }

    override fun toString(): String =
        "BinaryXmlNode(name='$name', entry='$entryPath', binary=${rawBytes != null})"
}

/**
 * A node for the compiled resource table (resources.arsc).
 *
 * The resource table contains all resource IDs, values, and
 * configurations. It's the core of Android's resource system.
 */
class ResourceArscNode(
    override val name: String,
    private val apkNode: ApkNode,
    override val parent: ContainerNode?
) : FileNode(name, parent) {

    override val editable: Boolean = false
    override val mimeType: String = "application/octet-stream"

    private var cachedBytes: ByteArray? = null

    override val size: Long
        get() = cachedBytes?.size?.toLong()
            ?: try {
                val zip = apkNode.getZipFile()
                zip.getEntry("resources.arsc")?.size ?: -1
            } catch (_: Exception) { -1 }

    override fun readContent(): ByteArray {
        cachedBytes?.let { return it }
        val bytes = apkNode.readEntry("resources.arsc")
        cachedBytes = bytes
        return bytes
    }

    override fun writeContent(content: ByteArray) {
        throw UnsupportedOperationException("resources.arsc cannot be edited directly; use ResourceEditor")
    }

    override fun toString(): String =
        "ResourceArscNode(name='$name', size=$size)"
}

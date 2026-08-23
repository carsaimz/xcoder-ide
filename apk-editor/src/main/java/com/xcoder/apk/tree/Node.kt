package com.xcoder.apk.tree

/**
 * Sealed interface representing a node in the APK/DEX file tree.
 *
 * Based on Dalvikus's Node.kt architecture. Provides a hierarchical
 * view into APK contents (ZIP entries) and DEX class structures.
 *
 * The tree is lazily loaded — [ContainerNode] children are only
 * materialized when [ContainerNode.loadChildren] is called.
 */
sealed interface Node {

    /** Display name shown in the tree view. */
    val name: String

    /** Parent node, or `null` for the root. */
    val parent: ContainerNode?

    /** `true` for the root node of a tree. */
    val isRoot: Boolean get() = parent == null

    /**
 * Builds the full path from root to this node by walking up [parent] links.
 *
 * For a tree: `Root / classes.dex / com / example / MainActivity.smali`
 * this returns `"classes.dex/com/example/MainActivity.smali"`.
 */
    fun getPath(): String {
        val parts = mutableListOf<String>()
        var current: Node? = this
        while (current != null) {
            if (!current.isRoot) parts.add(0, current.name)
            current = current.parent
        }
        return parts.joinToString("/")
    }

    /**
 * Walks up to the root and returns the root node.
 */
    fun getRoot(): ContainerNode {
        var node = parent
        while (node?.parent != null) {
            node = node.parent
        }
        return node ?: (this as ContainerNode)
    }
}

/**
 * Abstract base for nodes that contain children.
 *
 * Children are loaded lazily via [loadChildren]. Once loaded, they
 * are cached in the [children] list. Call [refreshChildren] to
 * reload from source.
 *
 * Based on Dalvikus's ContainerNode which supports:
 * - Lazy loading of ZIP entries and DEX class lists
 * - Child replacement (e.g., after editing a file)
 * - Path resolution to find a descendant by relative path
 */
abstract class ContainerNode(
    override val name: String,
    override val parent: ContainerNode? = null
) : Node {

    /** Cached list of child nodes. Empty until [loadChildren] is called. */
    private val _children = mutableListOf<Node>()
    val children: List<Node> get() = _children

    /** Whether children have been loaded at least once. */
    var isLoaded: Boolean = false
        private set

    /**
 * Load child nodes from the underlying data source.
 *
 * Implementations should populate [_children] with the appropriate
 * node types (e.g., [ApkNode] for DEX/XML entries, [PackageNode] for smali packages).
 *
 * Called once on first access; subsequent calls to [getChildrenIfLoaded]
 * return the cached list. Use [refreshChildren] to force reload.
 */
    protected abstract fun loadChildren()

    /**
 * Returns the cached children list, loading them if necessary.
 *
 * This is the primary entry point for the tree view adapter.
 */
    fun getChildren(): List<Node> {
        if (!isLoaded) {
            loadChildren()
            isLoaded = true
        }
        return children
    }

    /**
 * Returns children if already loaded, without triggering a load.
 */
    fun getChildrenIfLoaded(): List<Node> = if (isLoaded) children else emptyList()

    /**
 * Force-reload children from the data source.
 *
 * Clears the current children list and calls [loadChildren] again.
 * Useful after an external modification (e.g., file saved, DEX rebuilt).
 */
    fun refreshChildren() {
        _children.clear()
        isLoaded = false
        loadChildren()
        isLoaded = true
    }

    /**
 * Replace a child node with a new one.
 *
 * Used when a file node is edited — the old node is replaced
 * in-place so the tree view can refresh.
 *
 * @param oldChild the existing child to replace
 * @param newChild the replacement node
 * @return `true` if the child was found and replaced
 */
    fun replaceChild(oldChild: Node, newChild: Node): Boolean {
        val idx = _children.indexOf(oldChild)
        if (idx < 0) return false
        _children[idx] = newChild
        return true
    }

    /**
 * Add a child node. Called during [loadChildren] implementations.
 */
    fun addChild(node: Node) {
        _children.add(node)
    }

    /**
 * Insert a child at a specific index.
 */
    fun addChild(index: Int, node: Node) {
        _children.add(index, node)
    }

    /**
 * Remove a child node.
 */
    fun removeChild(child: Node): Boolean {
        return _children.remove(child)
    }

    /**
 * Resolve a path relative to this node.
 *
 * Path segments are separated by `/`. Each segment is matched
 * against child node names. For container nodes, the search
 * continues recursively.
 *
 * Example: `resolvePath("com/example/Foo.smali")` navigates
 * through the `com` and `example` package nodes to find `Foo.smali`.
 *
 * @param path the relative path to resolve
 * @return the resolved node, or `null` if not found
     */
    fun resolvePath(path: String): Node? {
        val segments = path.split("/").filter { it.isNotEmpty() }
        return resolveSegments(segments, 0)
    }

    private fun resolveSegments(segments: List<String>, index: Int): Node? {
        if (index >= segments.size) return this
        val segment = segments[index]
        val children = getChildren()
        val child = children.find { it.name.equals(segment, ignoreCase = false) }
            ?: children.find { it.name.equals(segment, ignoreCase = true) }
            ?: return null
        return if (child is ContainerNode && index < segments.size - 1) {
            child.resolveSegments(segments, index + 1)
        } else if (index == segments.size - 1) {
            child
        } else {
            null
        }
    }

    /**
 * Find a node anywhere in this subtree by exact path.
 *
 * @param path full path from this node
 * @return the node, or null
     */
    fun findNodeByPath(path: String): Node? {
        if (getPath() == path) return this
        for (child in getChildren()) {
            if (child is ContainerNode) {
                val found = child.findNodeByPath(path)
                if (found != null) return found
            } else if (child.getPath() == path) {
                return child
            }
        }
        return null
    }

    /** Count total descendant nodes (recursive). */
    fun countDescendants(): Int {
        var count = 0
        for (child in getChildren()) {
            count++
            if (child is ContainerNode) count += child.countDescendants()
        }
        return count
    }

    override fun toString(): String = "ContainerNode(name='$name', children=${children.size})"
}

/**
 * Abstract base for nodes that represent file content.
 *
 * File nodes can be read and (optionally) written. The [editable]
 * flag indicates whether the content can be modified.
 *
 * Based on Dalvikus's FileNode which supports:
 * - Reading file content as bytes or UTF-8 string
 * - Writing modified content back
 * - Tracking edit state (dirty flag)
 */
abstract class FileNode(
    override val name: String,
    override val parent: ContainerNode? = null
) : Node {

    /**
 * Whether this file's content can be modified.
 *
 * Some nodes (e.g., compiled resources) are read-only.
 */
    abstract val editable: Boolean

    /** File size in bytes, or -1 if unknown. */
    abstract val size: Long

    /** MIME type hint for the file content. */
    open val mimeType: String? = null

    /** Whether the content has been modified since last read. */
    var isDirty: Boolean = false
        protected set

    /**
 * Read the file content as raw bytes.
 *
 * @return file content as byte array
 * @throws IOException if the file cannot be read
     */
    @Throws(java.io.IOException::class)
    abstract fun readContent(): ByteArray

    /**
 * Read the file content as a UTF-8 string.
 *
 * Convenience method for text file nodes (smali, XML, etc.).
 *
 * @return file content decoded as UTF-8
 * @throws IOException if the file cannot be read or is not valid UTF-8
     */
    @Throws(java.io.IOException::class)
    fun readTextContent(): String = String(readContent(), Charsets.UTF_8)

    /**
 * Write content to this file node.
 *
 * Implementations should persist the content back to the source
 * (e.g., update the in-memory ZIP entry or rewrite the DEX).
 *
 * @param content the new content to write
 * @throws IOException if the write fails
 * @throws UnsupportedOperationException if [editable] is false
     */
    @Throws(java.io.IOException::class)
    abstract fun writeContent(content: ByteArray)

    /**
 * Write text content (UTF-8) to this file node.
 *
 * @param text the new text content
     */
    @Throws(java.io.IOException::class)
    fun writeTextContent(text: String) {
        writeContent(text.toByteArray(Charsets.UTF_8))
        isDirty = false
    }

    /**
 * Get the file extension (lowercase, without dot).
 *
 * Examples: `"smali"`, `"xml"`, `"dex"`, `"png"`
 */
    val extension: String get() {
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot + 1).lowercase() else ""
    }

    override fun toString(): String = "FileNode(name='$name', size=$size, editable=$editable)"
}

/**
 * A [FileNode] backed by the filesystem.
 *
 * Reads and writes directly to a file on disk.
 * Used for extracted files that are edited in-place.
 */
class FileSystemNode(
    override val name: String,
    private val filePath: String,
    override val parent: ContainerNode? = null,
    override val editable: Boolean = true
) : FileNode(name, parent) {

    private val file = java.io.File(filePath)

    override val size: Long get() = if (file.exists()) file.length() else -1

    override fun readContent(): ByteArray = file.readBytes()

    override fun writeContent(content: ByteArray) {
        check(editable) { "FileSystemNode '$name' is not editable" }
        file.writeBytes(content)
        isDirty = false
    }
}

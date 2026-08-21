package com.xcoder.remote.util

/**
 * Path manipulation utilities for remote file system paths.
 *
 * All paths are Unix-style (forward slashes), regardless of protocol.
 * Handles normalization, joining, splitting, and parent/child resolution.
 */
object PathUtils {

    /** The Unix path separator. */
    const val SEPARATOR = "/"

    /** Regex that matches consecutive separators. */
    private val multipleSlashes = Regex("/+" )

    /**
     * Normalize a path: collapse multiple slashes, resolve `.` and `..`,
     * remove trailing slash (except for root `/`).
     */
    fun normalize(path: String): String {
        if (path.isBlank()) return "/"
        var normalized = path.replace("\\", "/")
        normalized = normalized.replace(multipleSlashes, SEPARATOR)
        val parts = split(normalized)
        val stack = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "" -> { /* skip empty */ }
                "." -> { /* skip current dir */ }
                ".." -> { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
                else -> stack.add(part)
            }
        }
        return if (stack.isEmpty()) "/" else "/${stack.joinToString(SEPARATOR)}"
    }

    /**
     * Join path segments together, handling leading/trailing slashes correctly.
     */
    fun join(base: String, vararg segments: String): String {
        if (segments.isEmpty()) return normalize(base)
        val parts = mutableListOf<String>()
        if (base.isNotBlank()) {
            parts.addAll(split(base))
        }
        for (segment in segments) {
            if (segment.isNotBlank()) {
                parts.addAll(split(segment))
            }
        }
        return if (parts.isEmpty()) "/" else "/${parts.joinToString(SEPARATOR)}"
    }

    /**
     * Split a path into its components, excluding empty segments.
     */
    fun split(path: String): List<String> {
        val normalized = normalize(path)
        return if (normalized == "/") emptyList()
        else normalized.trimStart('/').trimEnd('/').split(SEPARATOR)
    }

    /**
     * Get the file name (last component) of a path.
     */
    fun fileName(path: String): String {
        val normalized = normalize(path)
        return if (normalized == "/") ""
        else normalized.substringAfterLast('/')
    }

    /**
     * Get the parent directory of a path.
     * Returns empty string for root.
     */
    fun parent(path: String): String {
        val normalized = normalize(path)
        if (normalized == "/") return ""
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash <= 0) "/" else normalized.substring(0, lastSlash)
    }

    /**
     * Get the file extension (without the dot).
     */
    fun extension(path: String): String {
        val name = fileName(path)
        return if (!name.contains('.'')) ""
        else name.substringAfterLast('.', "")
    }

    /**
     * Check if [child] is a descendant of [parent].
     */
    fun isChildOf(child: String, parent: String): Boolean {
        val normalizedChild = normalize(child)
        val normalizedParent = normalize(parent)
        if (normalizedChild == normalizedParent) return false
        return normalizedChild.startsWith(if (normalizedParent.endsWith('/')) normalizedParent else "$normalizedParent/")
    }

    /**
     * Compute the relative path from [basePath] to [targetPath].
     */
    fun relativeTo(basePath: String, targetPath: String): String {
        val baseParts = split(normalize(basePath))
        val targetParts = split(normalize(targetPath))
        var commonIndex = 0
        while (commonIndex < baseParts.size && commonIndex < targetParts.size &&
            baseParts[commonIndex] == targetParts[commonIndex]
        ) {
            commonIndex++
        }
        val upCount = baseParts.size - commonIndex
        val result = mutableListOf<String>()
        repeat(upCount) { result.add("..") }
        result.addAll(targetParts.subList(commonIndex, targetParts.size))
        return result.joinToString(SEPARATOR)
    }

    /**
     * Ensure a path ends with exactly one slash.
     */
    fun ensureTrailingSlash(path: String): String {
        val n = normalize(path)
        return if (n.endsWith('/')) n else "$n/"
    }

    /**
     * Remove the trailing slash from a path.
     */
    fun removeTrailingSlash(path: String): String {
        return path.trimEnd('/')
    }

    /**
     * Get the depth (number of components) of a path.
     */
    fun depth(path: String): Int = split(path).size

    /**
     * Ensure a path starts with a slash (is absolute).
     */
    fun ensureAbsolute(path: String): String {
        return if (path.startsWith('/')) path else "/$path"
    }
}

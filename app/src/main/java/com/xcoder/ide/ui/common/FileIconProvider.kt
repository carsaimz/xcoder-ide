package com.xcoder.ide.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Provides Material Icons for file types based on file extension.
 *
 * Used in [EditorTabs] and the file tree to show the appropriate icon
 * for each file. Pattern from AndroidIDE's file-icon mapping in
 * `EditorTab` and `FileListAdapter`.
 *
 * The icon color matches the [com.xcoder.ide.theme.FileColors] palette
 * so that .kt files are purple, .java files are orange, etc.
 */
@Composable
fun FileIconProvider(
    fileName: String,
    modifier: Modifier = Modifier
) {
    val (icon, color) = iconForFile(fileName)
    Icon(
        imageVector = icon,
        contentDescription = "File: $fileName",
        modifier = modifier.size(14.dp),
        tint = color
    )
}

/** Returns (icon, color) pair for the given file name or path. */
@Composable
private fun iconForFile(fileName: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val name = fileName.substringAfterLast('/').substringAfterLast('\\')
    val ext = name.substringAfterLast('.', "").lowercase()

    // Check for directories (no extension and no dot in the name).
    if (!name.contains('.') && ext.isEmpty()) {
        return Icons.Default.Folder to Color(0xFF5C9CE6)
    }

    return when (ext) {
        // ── Kotlin / Java ───────────────────────────────────
        "kt", "kts" -> Icons.Default.Code to Color(0xFFB56DFF)
        "java" -> Icons.Default.Code to Color(0xFFF18A54)

        // ── XML / Layout ────────────────────────────────────
        "xml" -> Icons.Default.Description to Color(0xFF6ACB8F)

        // ── Gradle ──────────────────────────────────────────
        "gradle", "gradle.kts" -> Icons.Default.Build to Color(0xFF6DD4A0)

        // ── Images ──────────────────────────────────────────
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg" ->
            Icons.Default.Image to Color(0xFFE8A838)

        // ── Smali / DEX ─────────────────────────────────────
        "smali" -> Icons.Default.Memory to Color(0xFFFF6B6B)
        "dex" -> Icons.Default.Memory to Color(0xFFFF6B6B)

        // ── C / C++ ─────────────────────────────────────────
        "c" -> Icons.Default.Code to Color(0xFF4FC1FF)
        "h" -> Icons.Default.Code to Color(0xFF4FC1FF)
        "cpp", "cc", "cxx" -> Icons.Default.Code to Color(0xFF4FC1FF)
        "hpp", "hxx" -> Icons.Default.Code to Color(0xFF4FC1FF)

        // ── JavaScript / TypeScript ─────────────────────────
        "js", "mjs" -> Icons.Default.Code to Color(0xFFF7DF1E)
        "ts", "tsx" -> Icons.Default.Code to Color(0xFF4FC1FF)

        // ── Python ──────────────────────────────────────────
        "py" -> Icons.Default.Code to Color(0xFF4B8BBE)

        // ── Web ─────────────────────────────────────────────
        "html" -> Icons.Default.Language to Color(0xFFE44D26)
        "css", "scss", "sass" -> Icons.Default.Palette to Color(0xFF264DE4)

        // ── JSON / YAML ─────────────────────────────────────
        "json" -> Icons.Default.DataObject to Color(0xFFE5C84F)
        "yaml", "yml" -> Icons.Default.DataObject to Color(0xFFCB6D31)

        // ── Shell / Config ──────────────────────────────────
        "sh", "bash", "zsh" -> Icons.Default.Terminal to Color(0xFF4EAA25)
        "toml" -> Icons.Default.Settings to Color(0xFF9C4221)
        "properties" -> Icons.Default.Settings to Color(0xFF9C4221)
        "cfg", "conf", "ini" -> Icons.Default.Settings to Color(0xFF9C4221)

        // ── Markdown / Docs ─────────────────────────────────
        "md", "mdx" -> Icons.Default.Article to Color(0xFF519ABA)
        "txt" -> Icons.Default.Article to Color(0xFF89E051)

        // ── Archive ─────────────────────────────────────────
        "zip", "jar", "aar", "tar", "gz" -> Icons.Default.FolderZip to Color(0xFFE8A838)

        // ── Proguard ────────────────────────────────────────
        "pro" -> Icons.Default.Security to Color(0xFF8D6E63)

        // ── SQL ─────────────────────────────────────────────
        "sql" -> Icons.Default.Storage to Color(0xFFE38C00)

        // ── Kotlin Script (build.gradle.kts detection) ──────
        else -> {
            // Check for compound extensions like .gradle.kts
            if (name.endsWith(".gradle.kts") || name.endsWith(".gradle")) {
                Icons.Default.Build to Color(0xFF6DD4A0)
            } else {
                Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    }
}

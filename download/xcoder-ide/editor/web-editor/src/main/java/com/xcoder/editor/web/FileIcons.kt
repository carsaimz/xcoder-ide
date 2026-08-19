package com.xcoder.editor.web

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class FileIcon(
    val color: Color,
    val icon: ImageVector,
    val isFolder: Boolean = false
)

private val extensionMap = mapOf(
    "kt" to FileIcon(Color(0xFFA97BFF), Icons.Default.Code),
    "kts" to FileIcon(Color(0xFFA97BFF), Icons.Default.Code),
    "java" to FileIcon(Color(0xFFE76F00), Icons.Default.Code),
    "xml" to FileIcon(Color(0xFFE44D26), Icons.Default.Description),
    "json" to FileIcon(Color(0xFFF5D142), Icons.Default.DataObject),
    "js" to FileIcon(Color(0xFFF7DF1E), Icons.Default.Javascript),
    "mjs" to FileIcon(Color(0xFFF7DF1E), Icons.Default.Javascript),
    "cjs" to FileIcon(Color(0xFFF7DF1E), Icons.Default.Javascript),
    "ts" to FileIcon(Color(0xFF3178C6), Icons.Default.Code),
    "tsx" to FileIcon(Color(0xFF3178C6), Icons.Default.Code),
    "jsx" to FileIcon(Color(0xFF61DAFB), Icons.Default.Code),
    "py" to FileIcon(Color(0xFF3776AB), Icons.Default.Code),
    "pyw" to FileIcon(Color(0xFF3776AB), Icons.Default.Code),
    "html" to FileIcon(Color(0xFFE44D26), Icons.Default.Html),
    "htm" to FileIcon(Color(0xFFE44D26), Icons.Default.Html),
    "css" to FileIcon(Color(0xFF264DE4), Icons.Default.Palette),
    "scss" to FileIcon(Color(0xFFCD6799), Icons.Default.Palette),
    "sass" to FileIcon(Color(0xFFCD6799), Icons.Default.Palette),
    "less" to FileIcon(Color(0xFF1D365D), Icons.Default.Palette),
    "md" to FileIcon(Color(0xFF519ABA), Icons.Default.Article),
    "mdx" to FileIcon(Color(0xFF519ABA), Icons.Default.Article),
    "txt" to FileIcon(Color(0xFF888888), Icons.Default.Description),
    "sql" to FileIcon(Color(0xFFE38C00), Icons.Default.Storage),
    "sh" to FileIcon(Color(0xFF4EAA25), Icons.Default.Terminal),
    "bash" to FileIcon(Color(0xFF4EAA25), Icons.Default.Terminal),
    "zsh" to FileIcon(Color(0xFF4EAA25), Icons.Default.Terminal),
    "fish" to FileIcon(Color(0xFF4EAA25), Icons.Default.Terminal),
    "yml" to FileIcon(Color(0xFFCB171E), Icons.Default.Settings),
    "yaml" to FileIcon(Color(0xFFCB171E), Icons.Default.Settings),
    "toml" to FileIcon(Color(0xFF9C4221), Icons.Default.Settings),
    "gradle" to FileIcon(Color(0xFF02303A), Icons.Default.Build),
    "properties" to FileIcon(Color(0xFF4A6A30), Icons.Default.Settings),
    "lock" to FileIcon(Color(0xFF6B7280), Icons.Default.Lock),
    "gitignore" to FileIcon(Color(0xFFF05032), Icons.Default.VisibilityOff),
    "dockerfile" to FileIcon(Color(0xFF2496ED), Icons.Default.Memory),
    "png" to FileIcon(Color(0xFFA4C639), Icons.Default.Image),
    "jpg" to FileIcon(Color(0xFFA4C639), Icons.Default.Image),
    "jpeg" to FileIcon(Color(0xFFA4C639), Icons.Default.Image),
    "gif" to FileIcon(Color(0xFFA4C639), Icons.Default.Image),
    "svg" to FileIcon(Color(0xFFFFB13B), Icons.Default.Image),
    "webp" to FileIcon(Color(0xFFA4C639), Icons.Default.Image),
    "ico" to FileIcon(Color(0xFFA4C639), Icons.Default.Image),
    "bmp" to FileIcon(Color(0xFFA4C639), Icons.Default.Image),
    "c" to FileIcon(Color(0xFF555555), Icons.Default.Code),
    "h" to FileIcon(Color(0xFF555555), Icons.Default.Code),
    "cpp" to FileIcon(Color(0xFF00599C), Icons.Default.Code),
    "cc" to FileIcon(Color(0xFF00599C), Icons.Default.Code),
    "cxx" to FileIcon(Color(0xFF00599C), Icons.Default.Code),
    "hpp" to FileIcon(Color(0xFF00599C), Icons.Default.Code),
    "rs" to FileIcon(Color(0xFFCE422B), Icons.Default.Code),
    "go" to FileIcon(Color(0xFF00ADD8), Icons.Default.Code),
    "rb" to FileIcon(Color(0xFFCC342D), Icons.Default.Code),
    "php" to FileIcon(Color(0xFF777BB4), Icons.Default.Code),
    "swift" to FileIcon(Color(0xFFFA7343), Icons.Default.Code),
    "dart" to FileIcon(Color(0xFF0175C2), Icons.Default.Code),
    "lua" to FileIcon(Color(0xFF000080), Icons.Default.Code),
    "r" to FileIcon(Color(0xFF276DC3), Icons.Default.Code),
    "R" to FileIcon(Color(0xFF276DC3), Icons.Default.Code),
    "ktm" to FileIcon(Color(0xFFA97BFF), Icons.Default.Html),
    "groovy" to FileIcon(Color(0xFF4298B8), Icons.Default.Code),
    "gradle.kts" to FileIcon(Color(0xFF02303A), Icons.Default.Build),
    "proguard" to FileIcon(Color(0xFFE44D26), Icons.Default.Security),
    "dex" to FileIcon(Color(0xFF3DDC84), Icons.Default.Android),
    "apk" to FileIcon(Color(0xFF3DDC84), Icons.Default.Android),
    "aab" to FileIcon(Color(0xFF3DDC84), Icons.Default.Android),
    "so" to FileIcon(Color(0xFF888888), Icons.Default.Memory),
    "jar" to FileIcon(Color(0xFFE76F00), Icons.Default.Archive),
    "aar" to FileIcon(Color(0xFFA97BFF), Icons.Default.Archive),
    "zip" to FileIcon(Color(0xFF6D4C41), Icons.Default.Archive),
    "tar" to FileIcon(Color(0xFF6D4C41), Icons.Default.Archive),
    "gz" to FileIcon(Color(0xFF6D4C41), Icons.Default.Archive),
    "rar" to FileIcon(Color(0xFF6D4C41), Icons.Default.Archive),
    "7z" to FileIcon(Color(0xFF6D4C41), Icons.Default.Archive),
    "pdf" to FileIcon(Color(0xFFE74C3C), Icons.Default.PictureAsPdf),
    "csv" to FileIcon(Color(0xFF217346), Icons.Default.TableChart),
    "xls" to FileIcon(Color(0xFF217346), Icons.Default.TableChart),
    "xlsx" to FileIcon(Color(0xFF217346), Icons.Default.TableChart),
    "tf" to FileIcon(Color(0xFF7B42BC), Icons.Default.Cloud),
    "proto" to FileIcon(Color(0xFF3E8B85), Icons.Default.DataObject)
)

private val folderVariantMap = mapOf(
    "src" to FileIcon(Color(0xFFA97BFF), Icons.Default.Source, isFolder = true),
    "main" to FileIcon(Color(0xFF42A5F5), Icons.Default.Home, isFolder = true),
    "java" to FileIcon(Color(0xFFE76F00), Icons.Default.Folder, isFolder = true),
    "kotlin" to FileIcon(Color(0xFFA97BFF), Icons.Default.Folder, isFolder = true),
    "res" to FileIcon(Color(0xFF42A5F5), Icons.Default.Folder, isFolder = true),
    "drawable" to FileIcon(Color(0xFF66BB6A), Icons.Default.Image, isFolder = true),
    "layout" to FileIcon(Color(0xFF42A5F5), Icons.Default.Dashboard, isFolder = true),
    "values" to FileIcon(Color(0xFF26A69A), Icons.Default.Tune, isFolder = true),
    "assets" to FileIcon(Color(0xFF8D6E63), Icons.Default.Folder, isFolder = true),
    "build" to FileIcon(Color(0xFF4CAF50), Icons.Default.Build, isFolder = true),
    "gradle" to FileIcon(Color(0xFF02303A), Icons.Default.Folder, isFolder = true),
    ".gradle" to FileIcon(Color(0xFF546E7A), Icons.Default.Folder, isFolder = true),
    ".idea" to FileIcon(Color(0xFFFC801D), Icons.Default.Folder, isFolder = true),
    "git" to FileIcon(Color(0xFFF05032), Icons.Default.Folder, isFolder = true),
    ".git" to FileIcon(Color(0xFFF05032), Icons.Default.Folder, isFolder = true),
    "node_modules" to FileIcon(Color(0xFF539E43), Icons.Default.Folder, isFolder = true),
    "test" to FileIcon(Color(0xFFEF5350), Icons.Default.Science, isFolder = true),
    "androidTest" to FileIcon(Color(0xFF3DDC84), Icons.Default.Science, isFolder = true),
    "mipmap" to FileIcon(Color(0xFF66BB6A), Icons.Default.Image, isFolder = true),
    "menu" to FileIcon(Color(0xFF42A5F5), Icons.Default.Menu, isFolder = true),
    "navigation" to FileIcon(Color(0xFF5C6BC0), Icons.Default.Navigation, isFolder = true),
    "anim" to FileIcon(Color(0xFFFF7043), Icons.Default.Animation, isFolder = true),
    "raw" to FileIcon(Color(0xFF8D6E63), Icons.Default.Folder, isFolder = true),
    "font" to FileIcon(Color(0xFF7E57C2), Icons.Default.FontDownload, isFolder = true),
    "color" to FileIcon(Color(0xFFEC407A), Icons.Default.Palette, isFolder = true),
    "style" to FileIcon(Color(0xFF42A5F5), Icons.Default.Brush, isFolder = true)
)

private val defaultFolderIcon = FileIcon(Color(0xFF8D6E63), Icons.Default.Folder, isFolder = true)
private val defaultFileIcon = FileIcon(Color(0xFF90A4AE), Icons.Default.InsertDriveFile)

fun getFileIcon(fileName: String, isFolder: Boolean = false): FileIcon {
    if (isFolder) {
        return folderVariantMap[fileName] ?: defaultFolderIcon
    }
    val lastDot = fileName.lastIndexOf('.')
    if (lastDot < 0) {
        return folderVariantMap[fileName.lowercase()] ?: defaultFileIcon
    }
    val ext = fileName.substring(lastDot + 1).lowercase()
    val fileNameLower = fileName.lowercase()
    return extensionMap[ext] ?: extensionMap[fileNameLower] ?: defaultFileIcon
}

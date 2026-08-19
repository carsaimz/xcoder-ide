package com.xcoder.ai.tools

import com.google.gson.Gson
import com.xcoder.ai.*
import com.xcoder.core.file.FileManager
import dagger.hilt.android.scopes.ViewModelScoped
import java.net.URI
import javax.inject.Inject

@ViewModelScoped
class ReadFileTool @Inject constructor(
    private val fileManager: FileManager
) : AiTool {
    private val gson = Gson()

    override val definition = ToolDefinition.jsonSchema(
        name = "read_file",
        description = "Read the contents of a file. Returns the file content as text.",
        properties = mapOf(
            "path" to mapOf("type" to "string", "description" to "Absolute or relative path to the file to read")
        ),
        required = listOf("path")
    )

    override suspend fun execute(argumentsJson: String): ToolResult {
        val args = gson.fromJson(argumentsJson, Map::class.java)
        val path = args["path"]?.toString() ?: return ToolResult("", "read_file", "Error: path is required", isError = true)
        val uri = normalizePath(path)
        return try {
            val content = fileManager.readFile(uri)
            if (content.length > 50_000) {
                ToolResult("", "read_file", "${content.take(50_000)}\n\n[Truncated: file too long (${content.length} chars)]")
            } else {
                ToolResult("", "read_file", content)
            }
        } catch (e: Exception) {
            ToolResult("", "read_file", "Error reading file: ${e.message}", isError = true)
        }
    }

    private fun normalizePath(path: String): android.net.Uri {
        return if (path.startsWith("content://")) {
            android.net.Uri.parse(path)
        } else {
            android.net.Uri.fromFile(java.io.File(path))
        }
    }
}
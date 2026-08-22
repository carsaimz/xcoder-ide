package com.xcoder.ai.tools

import com.google.gson.Gson
import com.xcoder.ai.*
import com.xcoder.core.file.FileManager
import dagger.hilt.android.scopes.ViewModelScoped
import java.io.File
import javax.inject.Inject

@ViewModelScoped
class WriteFileTool @Inject constructor(
    private val fileManager: FileManager
) : AiTool {
    private val gson = Gson()

    override val definition = ToolDefinition.jsonSchema(
        name = "write_file",
        description = "Write content to an existing file. Creates parent directories if needed.",
        properties = mapOf(
            "path" to mapOf("type" to "string", "description" to "Path to the file"),
            "content" to mapOf("type" to "string", "description" to "Content to write")
        ),
        required = listOf("path", "content")
    )

    override suspend fun execute(argumentsJson: String): ToolResult {
        val args = gson.fromJson(argumentsJson, Map::class.java)
        val path = args["path"]?.toString() ?: return ToolResult("", "write_file", "Error: path is required", isError = true)
        val content = args["content"]?.toString() ?: return ToolResult("", "write_file", "Error: content is required", isError = true)
        return try {
            fileManager.writeFile(android.net.Uri.fromFile(File(path)), content)
            ToolResult("", "write_file", "Successfully wrote ${content.length} characters to $path")
        } catch (e: Exception) {
            ToolResult("", "write_file", "Error writing file: ${e.message}", isError = true)
        }
    }
}
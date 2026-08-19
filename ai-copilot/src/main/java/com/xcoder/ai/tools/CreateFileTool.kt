package com.xcoder.ai.tools

import com.google.gson.Gson
import com.xcoder.ai.*
import com.xcoder.core.file.FileManager
import dagger.hilt.android.scopes.ViewModelScoped
import java.io.File
import javax.inject.Inject

@ViewModelScoped
class CreateFileTool @Inject constructor(
    private val fileManager: FileManager
) : AiTool {
    private val gson = Gson()

    override val definition = ToolDefinition.jsonSchema(
        name = "create_file",
        description = "Create a new file with optional initial content.",
        properties = mapOf(
            "path" to mapOf("type" to "string", "description" to "Path for the new file"),
            "content" to mapOf("type" to "string", "description" to "Initial content (optional)", "default" to "")
        ),
        required = listOf("path")
    )

    override suspend fun execute(argumentsJson: String): ToolResult {
        val args = gson.fromJson(argumentsJson, Map::class.java)
        val path = args["path"]?.toString() ?: return ToolResult("", "create_file", "Error: path is required", isError = true)
        val content = args["content"]?.toString() ?: ""
        val file = File(path)
        return if (file.exists()) {
            ToolResult("", "create_file", "Error: File already exists: $path", isError = true)
        } else {
            try {
                file.parentFile?.mkdirs()
                file.writeText(content)
                ToolResult("", "create_file", "Created file: $path")
            } catch (e: Exception) {
                ToolResult("", "create_file", "Error: ${e.message}", isError = true)
            }
        }
    }
}
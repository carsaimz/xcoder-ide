package com.xcoder.ai.tools

import com.google.gson.Gson
import com.xcoder.ai.*
import com.xcoder.core.file.FileManager
import dagger.hilt.android.scopes.ViewModelScoped
import java.io.File
import javax.inject.Inject

@ViewModelScoped
class DeleteFileTool @Inject constructor(
    private val fileManager: FileManager
) : AiTool {
    private val gson = Gson()

    override val definition = ToolDefinition.jsonSchema(
        name = "delete_file",
        description = "Delete a file or empty directory.",
        properties = mapOf(
            "path" to mapOf("type" to "string", "description" to "Path to the file or directory to delete")
        ),
        required = listOf("path")
    )

    override suspend fun execute(argumentsJson: String): ToolResult {
        val args = gson.fromJson(argumentsJson, Map::class.java)
        val path = args["path"]?.toString() ?: return ToolResult("", "delete_file", "Error: path is required", isError = true)
        val file = File(path)
        return if (!file.exists()) {
            ToolResult("", "delete_file", "Error: File not found: $path", isError = true)
        } else {
            try {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
                ToolResult("", "delete_file", "Deleted: $path")
            } catch (e: Exception) {
                ToolResult("", "delete_file", "Error: ${e.message}", isError = true)
            }
        }
    }
}
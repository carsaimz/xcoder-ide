package com.xcoder.ai.tools

import com.google.gson.Gson
import com.xcoder.ai.*
import com.xcoder.core.terminal.ShellExecutor
import dagger.hilt.android.scopes.ViewModelScoped
import java.io.File
import javax.inject.Inject

@ViewModelScoped
class RunCommandTool @Inject constructor(
    private val shellExecutor: ShellExecutor
) : AiTool {
    private val gson = Gson()

    override val definition = ToolDefinition.jsonSchema(
        name = "run_command",
        description = "Execute a shell command in the project directory.",
        properties = mapOf(
            "command" to mapOf("type" to "string", "description" to "Shell command to execute"),
            "working_directory" to mapOf("type" to "string", "description" to "Working directory (optional)", "default" to ".")
        ),
        required = listOf("command")
    )

    override suspend fun execute(argumentsJson: String): ToolResult {
        val args = gson.fromJson(argumentsJson, Map::class.java)
        val command = args["command"]?.toString() ?: return ToolResult("", "run_command", "Error: command is required", isError = true)
        val workDir = args["working_directory"]?.toString() ?: "."
        return try {
            val parts = command.split(" ")
            val result = shellExecutor.execute(File(workDir), parts, timeoutMs = 30_000)
            val output = buildString {
                append("Exit code: ${result.exitCode}\n")
                if (result.stdout.isNotBlank()) append("STDOUT:\n${result.stdout}")
                if (result.stderr.isNotBlank()) append("STDERR:\n${result.stderr}")
            }
            ToolResult("", "run_command", output, isError = result.isFailure)
        } catch (e: Exception) {
            ToolResult("", "run_command", "Error: ${e.message}", isError = true)
        }
    }
}
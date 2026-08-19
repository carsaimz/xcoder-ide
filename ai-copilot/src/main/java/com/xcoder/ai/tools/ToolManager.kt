package com.xcoder.ai.tools

import com.xcoder.ai.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolManager @Inject constructor(
    private val readFileTool: ReadFileTool,
    private val writeFileTool: WriteFileTool,
    private val createFileTool: CreateFileTool,
    private val deleteFileTool: DeleteFileTool,
    private val runCommandTool: RunCommandTool
) {
    private val toolMap = mutableMapOf<String, AiTool>()

    init {
        registerTool(readFileTool)
        registerTool(writeFileTool)
        registerTool(createFileTool)
        registerTool(deleteFileTool)
        registerTool(runCommandTool)
    }

    fun registerTool(tool: AiTool) {
        toolMap[tool.definition.name] = tool
    }

    fun unregisterTool(name: String) {
        toolMap.remove(name)
    }

    suspend fun executeToolCall(call: ToolCall): ToolResult {
        val tool = toolMap[call.name]
        if (tool == null) {
            return ToolResult(call.id, call.name, "Error: Unknown tool '${call.name}'", isError = true)
        }
        return try {
            tool.execute(call.arguments)
        } catch (e: Exception) {
            ToolResult(call.id, call.name, "Error executing tool: ${e.message}", isError = true)
        }
    }

    fun getToolDefinitions(): List<ToolDefinition> = toolMap.values.map { it.definition }

    fun getRegisteredToolNames(): List<String> = toolMap.keys.toList()
}

interface AiTool {
    val definition: ToolDefinition
    suspend fun execute(argumentsJson: String): ToolResult
}
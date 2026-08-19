package com.xcoder.ai

import kotlinx.coroutines.flow.Flow

// --- Data classes ---

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

@Suppress("unused")
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null
)

@Suppress("unused")
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

@Suppress("unused")
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any> = emptyMap()
) {
    companion object {
        fun jsonSchema(
            name: String,
            description: String,
            properties: Map<String, Map<String, Any>>,
            required: List<String> = emptyList()
        ): ToolDefinition = ToolDefinition(
            name = name,
            description = description,
            parameters = mapOf(
                "type" to "object",
                "properties" to properties,
                "required" to required
            )
        )
    }
}

@Suppress("unused")
data class ToolResult(
    val toolCallId: String,
    val name: String,
    val content: String,
    val isError: Boolean = false
)

@Suppress("unused")
data class LlmResponse(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val finishReason: String = "stop",
    val usage: TokenUsage? = null
)

@Suppress("unused")
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@Suppress("unused")
data class StreamChunk(
    val content: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val finishReason: String? = null,
    val isFinal: Boolean = false
)

@Suppress("unused")
data class LlmConfig(
    val apiKey: String,
    val model: String,
    val baseUrl: String = "",
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 1f,
    val systemPrompt: String? = null,
    val maxRetries: Int = 3,
    val timeoutMs: Long = 60_000
)

// --- Abstract client ---

abstract class LlmClient(protected val config: LlmConfig) {

    abstract suspend fun sendMessage(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition> = emptyList()
    ): LlmResponse

    abstract fun streamMessage(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition> = emptyList()
    ): Flow<StreamChunk>

    protected suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(config.maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < config.maxRetries - 1) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                }
            }
        }
        throw lastException ?: RuntimeException("Unknown error after retries")
    }

    open fun close() {}
}
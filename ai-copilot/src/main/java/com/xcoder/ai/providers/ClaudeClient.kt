package com.xcoder.ai.providers

import com.google.gson.Gson
import com.xcoder.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

class ClaudeClient(config: LlmConfig) : LlmClient(config) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = config.baseUrl.ifEmpty { "https://api.anthropic.com" }
    private val anthropicVersion = "2023-06-01"

    override suspend fun sendMessage(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse = withContext(Dispatchers.IO) {
        withRetry {
            val systemMsg = messages.firstOrNull { it.role == MessageRole.SYSTEM }?.content
            val conversation = messages.filter { it.role != MessageRole.SYSTEM }
            val requestBody = buildRequestBody(conversation, tools, systemMsg, stream = false)
            val request = Request.Builder()
                .url("$baseUrl/v1/messages")
                .addHeader("x-api-key", config.apiKey)
                .addHeader("anthropic-version", anthropicVersion)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty response from Claude")
                if (!response.isSuccessful) {
                    throw IOException("Claude API error ${response.code}: $body")
                }
                parseResponse(body)
            }
        }
    }

    override fun streamMessage(messages: List<ChatMessage>, tools: List<ToolDefinition>): Flow<StreamChunk> = callbackFlow {
        val systemMsg = messages.firstOrNull { it.role == MessageRole.SYSTEM }?.content
        val conversation = messages.filter { it.role != MessageRole.SYSTEM }
        val requestBody = buildRequestBody(conversation, tools, systemMsg, stream = true)
        val request = Request.Builder()
            .url("$baseUrl/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", anthropicVersion)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val factory = EventSources.createFactory(client)
        factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val event = gson.fromJson(data, ClaudeStreamEvent::class.java)
                    if (event.type == "content_block_delta") {
                        val text = event.delta?.text
                        if (text != null) {
                            trySend(StreamChunk(content = text))
                        }
                        val inputJson = event.delta?.partialJson
                        if (inputJson != null) {
                            trySend(StreamChunk(content = inputJson))
                        }
                    } else if (event.type == "message_stop") {
                        trySend(StreamChunk(isFinal = true))
                        close()
                    }
                } catch (_: Exception) {}
            }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                trySend(StreamChunk(content = "Error: ${t?.message}", isFinal = true))
                close()
            }
        })
        awaitClose()
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        systemMsg: String?,
        stream: Boolean
    ): RequestBody {
        val body = mutableMapOf<String, Any>(
            "model" to config.model,
            "max_tokens" to config.maxTokens,
            "stream" to stream
        )
        if (systemMsg != null) body["system"] = systemMsg
        body["messages"] = messages.map { msg ->
            val map = mutableMapOf<String, Any>("role" to msg.role.name.lowercase())
            if (msg.role == MessageRole.TOOL) {
                map["role"] = "user"
                map["content"] = listOf(mapOf("type" to "tool_result", "tool_use_id" to (msg.toolCallId ?: ""), "content" to msg.content))
            } else if (msg.toolCalls.isNotEmpty()) {
                map["content"] = msg.toolCalls.map { tc ->
                    mapOf("type" to "tool_use", "id" to tc.id, "name" to tc.name, "input" to gson.fromJson(tc.arguments, Map::class.java))
                }
            } else {
                map["content"] = msg.content
            }
            map
        }
        if (tools.isNotEmpty()) {
            body["tools"] = tools.map { tool ->
                mapOf("name" to tool.name, "description" to tool.description, "input_schema" to tool.parameters)
            }
        }
        return gson.toJson(body).toRequestBody(jsonMediaType)
    }

    private fun parseResponse(body: String): LlmResponse {
        val response = gson.fromJson(body, ClaudeResponse::class.java)
        val textContent = response.content?.filter { it.type == "text" }?.map { it.text }?.joinToString("") ?: ""
        val toolUseContent = response.content?.filter { it.type == "tool_use" }?.map { block ->
            ToolCall(block.id ?: "", block.name ?: "", gson.toJson(block.input))
        } ?: emptyList()
        return LlmResponse(content = textContent, toolCalls = toolUseContent, finishReason = response.stopReason ?: "end_turn")
    }

    override fun close() { client.dispatcher.executorService.shutdown() }

    data class ClaudeResponse(val content: List<ClaudeContentBlock>?, val stopReason: String?)
    data class ClaudeContentBlock(val type: String, val text: String? = null, val id: String? = null, val name: String? = null, val input: Map<String, Any>? = null)
    data class ClaudeStreamEvent(val type: String, val delta: ClaudeDelta? = null)
    data class ClaudeDelta(val type: String? = null, val text: String? = null, val partialJson: String? = null)
}
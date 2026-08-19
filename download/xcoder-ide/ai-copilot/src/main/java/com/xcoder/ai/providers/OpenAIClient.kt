package com.xcoder.ai.providers

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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

class OpenAIClient(config: LlmConfig) : LlmClient(config) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = config.baseUrl.ifEmpty { "https://api.openai.com/v1" }

    override suspend fun sendMessage(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse = withContext(Dispatchers.IO) {
        withRetry {
            val requestBody = buildRequestBody(messages, tools, stream = false)
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty response from OpenAI")
                if (!response.isSuccessful) {
                    throw IOException("OpenAI API error ${response.code}: $body")
                }
                parseResponse(body)
            }
        }
    }

    override fun streamMessage(messages: List<ChatMessage>, tools: List<ToolDefinition>): Flow<StreamChunk> = callbackFlow {
        val requestBody = buildRequestBody(messages, tools, stream = true)
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val factory = EventSources.createFactory(client)
        factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    trySend(StreamChunk(isFinal = true))
                    close()
                    return
                }
                try {
                    val chunk = gson.fromJson(data, OpenAiChunk::class.java)
                    val delta = chunk.choices.firstOrNull()?.delta
                    if (delta != null) {
                        if (delta.content != null) {
                            trySend(StreamChunk(content = delta.content))
                        }
                        if (!delta.toolCalls.isNullOrEmpty()) {
                            val toolCalls = delta.toolCalls.map { tc ->
                                ToolCall(tc.id ?: "", tc.function?.name ?: "", tc.function?.arguments ?: "{}")
                            }
                            trySend(StreamChunk(toolCalls = toolCalls))
                        }
                        if (delta.toolCalls == null && delta.content == null) {
                            // Check finish reason
                            val finishReason = chunk.choices.firstOrNull()?.finishReason
                            if (finishReason != null) {
                                trySend(StreamChunk(finishReason = finishReason, isFinal = true))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                trySend(StreamChunk(content = "Error: ${t?.message ?: response?.message ?: "Unknown"}", isFinal = true))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }
        })

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(messages: List<ChatMessage>, tools: List<ToolDefinition>, stream: Boolean): RequestBody {
        val body = mutableMapOf<String, Any>(
            "model" to config.model,
            "messages" to messages.map { msg ->
                val map = mutableMapOf(
                    "role" to msg.role.name.lowercase(),
                    "content" to msg.content
                )
                if (msg.toolCalls.isNotEmpty()) {
                    map["tool_calls"] = msg.toolCalls.map { tc ->
                        mapOf("id" to tc.id, "type" to "function", "function" to mapOf("name" to tc.name, "arguments" to tc.arguments))
                    }
                }
                if (msg.toolCallId != null) {
                    map["tool_call_id"] = msg.toolCallId
                    map["role"] = "tool"
                    if (msg.name != null) map["name"] = msg.name
                }
                map
            },
            "max_tokens" to config.maxTokens,
            "temperature" to config.temperature,
            "top_p" to config.topP,
            "stream" to stream
        )
        if (tools.isNotEmpty()) {
            body["tools"] = tools.map { tool ->
                mapOf("type" to "function", "function" to mapOf("name" to tool.name, "description" to tool.description, "parameters" to tool.parameters))
            }
        }
        return gson.toJson(body).toRequestBody(jsonMediaType)
    }

    private fun parseResponse(body: String): LlmResponse {
        val response = gson.fromJson(body, OpenAiResponse::class.java)
        val choice = response.choices.firstOrNull()
        val message = choice?.message
        val toolCalls = message?.toolCalls?.map { tc ->
            ToolCall(tc.id ?: "", tc.function?.name ?: "", tc.function?.arguments ?: "{}")
        } ?: emptyList()
        return LlmResponse(
            content = message?.content ?: "",
            toolCalls = toolCalls,
            finishReason = choice?.finishReason ?: "stop",
            usage = response.usage?.let { TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens) }
        )
    }

    override fun close() { client.dispatcher.executorService.shutdown() }

    // --- OpenAI JSON models ---
    data class OpenAiResponse(val choices: List<OpenAiChoice>, val usage: OpenAiUsage? = null)
    data class OpenAiChoice(val message: OpenAiMessage, val finishReason: String? = null)
    data class OpenAiMessage(val content: String? = null, val toolCalls: List<OpenAiToolCall>? = null)
    data class OpenAiToolCall(val id: String? = null, val function: OpenAiFunction? = null)
    data class OpenAiFunction(val name: String, val arguments: String)
    data class OpenAiUsage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int)
    data class OpenAiChunk(val choices: List<OpenAiDeltaChoice>)
    data class OpenAiDeltaChoice(val delta: OpenAiDelta, val finishReason: String? = null)
    data class OpenAiDelta(val content: String? = null, val toolCalls: List<OpenAiDeltaToolCall>? = null)
    data class OpenAiDeltaToolCall(val id: String? = null, val index: Int, val function: OpenAiDeltaFunction?)
    data class OpenAiDeltaFunction(val name: String? = null, val arguments: String? = null)
}

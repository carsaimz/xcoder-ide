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

class GeminiClient(config: LlmConfig) : LlmClient(config) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = config.baseUrl.ifEmpty { "https://generativelanguage.googleapis.com/v1beta" }

    override suspend fun sendMessage(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse = withContext(Dispatchers.IO) {
        withRetry {
            val requestBody = buildRequestBody(messages, tools, stream = false)
            val url = "$baseUrl/models/${config.model}:generateContent?key=${config.apiKey}"
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(requestBody).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty response from Gemini")
                if (!response.isSuccessful) {
                    throw IOException("Gemini API error ${response.code}: $body")
                }
                parseResponse(body)
            }
        }
    }

    override fun streamMessage(messages: List<ChatMessage>, tools: List<ToolDefinition>): Flow<StreamChunk> = flow {
        val requestBody = buildRequestBody(messages, tools, stream = true)
        val url = "$baseUrl/models/${config.model}:streamGenerateContent?alt=sse&key=${config.apiKey}"
        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(requestBody).toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val source = response.body?.string() ?: return@flow
        source.lines().filter { it.startsWith("data: ") }.forEach { line ->
            val data = line.removePrefix("data: ")
            if (data == "[DONE]") {
                emit(StreamChunk(isFinal = true))
                return@forEach
            }
            try {
                val parsed = gson.fromJson(data, GeminiResponse::class.java)
                val text = parsed.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (text != null) {
                    emit(StreamChunk(content = text))
                }
            } catch (_: Exception) {}
        }
        emit(StreamChunk(isFinal = true))
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(messages: List<ChatMessage>, tools: List<ToolDefinition>, stream: Boolean): Map<String, Any> {
        val contents = mutableListOf<Map<String, Any>>()
        var systemInstruction: Map<String, Any>? = null

        messages.forEach { msg ->
            if (msg.role == MessageRole.SYSTEM) {
                systemInstruction = mapOf("parts" to listOf(mapOf("text" to msg.content)))
                return@forEach
            }
            contents.add(mapOf(
                "role" to if (msg.role == MessageRole.ASSISTANT) "model" else "user",
                "parts" to listOf(mapOf("text" to msg.content))
            ))
        }

        val body = mutableMapOf<String, Any>(
            "contents" to contents,
            "generationConfig" to mapOf(
                "maxOutputTokens" to config.maxTokens,
                "temperature" to config.temperature,
                "topP" to config.topP
            )
        )
        if (systemInstruction != null) body["systemInstruction"] = systemInstruction
        if (tools.isNotEmpty()) {
            body["tools"] = listOf(mapOf(
                "functionDeclarations" to tools.map { tool ->
                    mapOf("name" to tool.name, "description" to tool.description, "parameters" to tool.parameters)
                }
            ))
        }
        return body
    }

    private fun parseResponse(body: String): LlmResponse {
        val response = gson.fromJson(body, GeminiResponse::class.java)
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        val toolCalls = response.candidates?.firstOrNull()?.content?.parts
            ?.filter { it.functionCall != null }?.map { part ->
            val fc = part.functionCall!!
            ToolCall(fc.name, fc.name, gson.toJson(fc.args))
        } ?: emptyList()
        return LlmResponse(content = text, toolCalls = toolCalls, finishReason = "stop")
    }

    override fun close() { client.dispatcher.executorService.shutdown() }

    // Gemini JSON models
    data class GeminiResponse(val candidates: List<GeminiCandidate>?)
    data class GeminiCandidate(val content: GeminiContent?, val finishReason: String? = null)
    data class GeminiContent(val parts: List<GeminiPart>?, val role: String? = null)
    data class GeminiPart(val text: String? = null, val functionCall: GeminiFunctionCall? = null)
    data class GeminiFunctionCall(val name: String, val args: Map<String, Any> = emptyMap())
}
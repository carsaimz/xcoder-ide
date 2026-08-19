package com.xcoder.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xcoder.ai.context.ContextManager
import com.xcoder.ai.providers.ClaudeClient
import com.xcoder.ai.providers.GeminiClient
import com.xcoder.ai.providers.OpenAIClient
import com.xcoder.ai.tools.ToolManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val toolManager: ToolManager,
    private val contextManager: ContextManager
) : ViewModel() {

    enum class Provider { OPENAI, GEMINI, CLAUDE }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _selectedProvider = MutableStateFlow(Provider.OPENAI)
    val selectedProvider: StateFlow<Provider> = _selectedProvider.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentClient: LlmClient? = null
    private var currentJob: Job? = null
    private var systemPrompt: String = "You are XCoder AI, an intelligent coding assistant."

    fun setProvider(provider: Provider) { _selectedProvider.value = provider }

    fun configureProvider(provider: Provider, apiKey: String, model: String, baseUrl: String = "") {
        val config = LlmConfig(
            apiKey = apiKey,
            model = model,
            baseUrl = baseUrl,
            maxTokens = 4096,
            temperature = 0.7f
        )
        currentClient?.close()
        currentClient = when (provider) {
            Provider.OPENAI -> OpenAIClient(config)
            Provider.GEMINI -> GeminiClient(config)
            Provider.CLAUDE -> ClaudeClient(config)
        }
    }

    fun setSystemPrompt(prompt: String) { systemPrompt = prompt }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(role = MessageRole.USER, content = text)
        val updated = _messages.value + userMsg
        _messages.value = updated
        _error.value = null
        generateResponse(updated)
    }

    fun clearChat() {
        currentJob?.cancel()
        _messages.value = emptyList()
        _streamingContent.value = ""
        _isGenerating.value = false
    }

    fun stopGenerating() { currentJob?.cancel() }

    fun deleteMessage(index: Int) {
        val current = _messages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _messages.value = current
        }
    }

    private fun generateResponse(messages: List<ChatMessage>) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            val client = currentClient
            if (client == null) {
                _error.value = "No AI provider configured. Please set your API key in settings."
                return@launch
            }

            _isGenerating.value = true
            _streamingContent.value = ""

            try {
                val contextMessages = contextManager.buildContextForMessage(messages.last().content)
                val allMessages = contextMessages + listOf(
                    ChatMessage(role = MessageRole.SYSTEM, content = systemPrompt)
                ) + messages

                val tools = toolManager.getToolDefinitions()
                var currentMessages = allMessages.toMutableList()
                var toolLoop = 0
                val maxToolLoops = 10

                while (toolLoop < maxToolLoops) {
                    val fullContent = StringBuilder()
                    client.streamMessage(currentMessages, tools).collect { chunk ->
                        if (chunk.isFinal) return@collect
                        fullContent.append(chunk.content)
                        _streamingContent.value = fullContent.toString()
                    }

                    if (fullContent.isBlank()) {
                        val response = client.sendMessage(currentMessages, tools)
                        if (response.toolCalls.isNotEmpty()) {
                            currentMessages.add(ChatMessage(
                                role = MessageRole.ASSISTANT,
                                content = response.content,
                                toolCalls = response.toolCalls
                            ))
                            for (tc in response.toolCalls) {
                                val result = toolManager.executeToolCall(tc)
                                currentMessages.add(ChatMessage(
                                    role = MessageRole.TOOL,
                                    content = result.content,
                                    toolCallId = tc.id,
                                    name = tc.name
                                ))
                            }
                            toolLoop++
                            continue
                        }
                        currentMessages.add(ChatMessage(role = MessageRole.ASSISTANT, content = response.content))
                        _messages.value = currentMessages.filter { it.role in listOf(MessageRole.USER, MessageRole.ASSISTANT) }
                        break
                    }

                    currentMessages.add(ChatMessage(role = MessageRole.ASSISTANT, content = fullContent.toString()))
                    _messages.value = currentMessages.filter { it.role in listOf(MessageRole.USER, MessageRole.ASSISTANT) }
                    break
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _error.value = "Error: ${e.message}"
                }
            } finally {
                _isGenerating.value = false
                _streamingContent.value = ""
            }
        }
    }

    override fun onCleared() { currentClient?.close() }
}

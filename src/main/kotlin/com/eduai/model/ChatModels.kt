package com.eduai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- DeepSeek (OpenAI-compatible) chat completion API ----------

@Serializable
data class DeepSeekChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val thinking: Thinking = Thinking(),
    @SerialName("reasoning_effort") val reasoningEffort: String = "high",
    val stream: Boolean = false,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class Thinking(
    val type: String = "enabled",
)

@Serializable
data class DeepSeekChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
data class Choice(
    @SerialName("finish_reason") val finishReason: String? = null,
    val message: AssistantMessage? = null,
)

@Serializable
data class AssistantMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

// ---------- Local API (what this service exposes) ----------

@Serializable
data class LocalChatRequest(
    val prompt: String,
)

@Serializable
data class LocalChatResponse(
    val response: String,
    val reasoning: String? = null,
    val model: String? = null,
    val usage: Usage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

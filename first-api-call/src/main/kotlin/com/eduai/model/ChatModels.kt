package com.eduai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

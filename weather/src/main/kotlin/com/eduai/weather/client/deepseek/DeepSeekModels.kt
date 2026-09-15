// src/main/kotlin/com/eduai/weather/client/deepseek/DeepSeekModels.kt
package com.eduai.weather.client.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

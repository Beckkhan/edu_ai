// src/main/kotlin/com/eduai/turbofa/client/deepseek/DeepSeekModels.kt
package com.eduai.turbofa.client.deepseek

/**
 * One message of the ChatService ↔ TurbofaAgent contract (spec 5d).
 *
 * Roles are plain strings — "system" | "user" | "assistant" — exactly as ChatService constructs
 * them with named arguments; the agent maps them to Koog's SystemMessage/UserMessage/AssistantMessage.
 * The fueling_id of a request lives in the content ChatService built (5d, the single embedding point).
 */
data class ChatMessage(
    val role: String,
    val content: String,
)

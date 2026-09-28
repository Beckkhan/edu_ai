package com.eduai.turbofa.service

import com.eduai.turbofa.client.deepseek.ChatMessage
import com.eduai.turbofa.client.deepseek.TurbofaAgent
import com.eduai.turbofa.history.ChatHistoryStore
import java.time.Instant

private const val ROLE_USER = "user"

/** Must match the tool name in resources/tools/get_fueling_info.json (5b). */
private const val FUELING_INFO_TOOL = "get_fueling_info"

class ChatService(
    private val deepseekAgent: TurbofaAgent,
    private val ollamaAgent: TurbofaAgent,
    private val history: ChatHistoryStore,
) {
    suspend fun chat(
        prompt: String,
        fuelingId: String? = null,
        provider: Provider = Provider.DEEPSEEK,
    ): String {
        val dialogue = history.records()
        history.appendUser(prompt)

        // R9: only the outgoing message carries the fueling_id — the history keeps the raw prompt,
        // so a later request without a fueling_id replays a plain dialogue (no tool call).
        val userContent = if (fuelingId == null) prompt else promptWithFuelingId(prompt, fuelingId)
        val messages =
            dialogue.map { ChatMessage(role = it.role, content = it.content) } +
                ChatMessage(role = ROLE_USER, content = userContent)

        // D12: the provider choice is made here, in code — the LLM never decides its own routing.
        val reply =
            when (provider) {
                Provider.DEEPSEEK -> deepseekAgent.chat(messages)
                Provider.OLLAMA -> ollamaAgent.chat(messages)
            }
        history.appendAssistant(reply, Instant.now()) // D5: timestamped at the response receipt
        return reply
    }

    private fun promptWithFuelingId(prompt: String, fuelingId: String): String =
        "$prompt\n\nThe request refers to fueling_id=$fuelingId. " +
            "Use the $FUELING_INFO_TOOL tool with this fueling_id before answering."
}

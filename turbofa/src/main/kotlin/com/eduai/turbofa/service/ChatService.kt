package com.eduai.turbofa.service

import com.eduai.turbofa.client.deepseek.ChatMessage
import com.eduai.turbofa.client.deepseek.TurbofaAgent
import com.eduai.turbofa.history.ChatHistoryStore
import java.time.Instant

private const val ROLE_USER = "user"

/** Tool name as declared in resources/tools/get_fueling_info.json (5b, T1). */
private const val FUELING_INFO_TOOL = "get_fueling_info"

/**
 * R9/3.1: the round-trip glue — history + optional fueling_id → [TurbofaAgent] → reply.
 *
 * Stateless by design (D2): the whole history is replayed on every call, there is no Koog
 * session state. No LLM HTTP calls (R6) and no SQL (R5) live in this layer; DeepSeek belongs
 * to the agent, the read-only queries belong to the tool.
 */
class ChatService(
    private val agent: TurbofaAgent,
    private val history: ChatHistoryStore,
) {
    /**
     * Appends [prompt] to the history, runs the agent over the whole dialogue and returns the
     * assistant text.
     *
     * With [fuelingId] the outgoing user message embeds it (R9) so DeepSeek can call
     * `get_fueling_info`; without it the message list stays a plain dialogue (no tool call).
     */
    suspend fun chat(prompt: String, fuelingId: String? = null): String {
        val dialogue = history.records() // everything said before this request
        history.appendUser(prompt)

        // R9: only the outgoing message carries the fueling_id — the history keeps the raw prompt,
        // so a later request without a fueling_id replays a plain dialogue (no tool call).
        val userContent = if (fuelingId == null) prompt else promptWithFuelingId(prompt, fuelingId)
        val messages = dialogue.map { ChatMessage(role = it.role, content = it.content) } +
            ChatMessage(role = ROLE_USER, content = userContent)

        val reply = agent.chat(messages)
        history.appendAssistant(reply, Instant.now()) // D5: timestamped at DeepSeek response receipt
        return reply
    }

    private fun promptWithFuelingId(prompt: String, fuelingId: String): String =
        "$prompt\n\nThe request refers to fueling_id=$fuelingId. " +
            "Use the $FUELING_INFO_TOOL tool with this fueling_id before answering."
}

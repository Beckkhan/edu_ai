package com.eduai.turbofa.history

import java.time.Instant

/** 5d: they mirror the DeepSeek message roles. */
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

/**
 * One record of the conversation (5e). [receivedAt] is the moment the backend received the message
 * (D5): the arrival of a user prompt or the receipt of a DeepSeek response — never the later
 * file-write time and never a tool-call time.
 */
data class HistoryRecord(
    val role: String,
    val content: String,
    val receivedAt: Instant,
)

/**
 * R4/5e: the single source of truth for the message history — an in-memory cache plus a plain-text
 * file. DeepSeek is stateless (D2), so every request replays the full record list; the text file is
 * cleared on restart by the startup wiring (Application.kt).
 */
class ChatHistoryStore(
    private val cache: InMemoryHistoryCache,
    private val writer: TextFileHistoryWriter,
) {
    fun appendUser(content: String) {
        append(HistoryRecord(ROLE_USER, content, Instant.now()))
    }

    fun appendAssistant(content: String, receivedAt: Instant) {
        append(HistoryRecord(ROLE_ASSISTANT, content, receivedAt))
    }

    fun records(): List<HistoryRecord> = cache.records()

    fun clear() {
        cache.clear()
        writer.clear()
    }

    private fun append(record: HistoryRecord) {
        cache.add(record)
        writer.append(record)
    }
}

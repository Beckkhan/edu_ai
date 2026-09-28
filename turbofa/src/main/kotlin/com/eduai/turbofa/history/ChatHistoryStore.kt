package com.eduai.turbofa.history

import java.time.Instant

private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

/** [receivedAt] is the moment the backend received the message (D5). */
data class HistoryRecord(
    val role: String,
    val content: String,
    val receivedAt: Instant,
)

/**
 * Single source of truth for message history. DeepSeek is stateless (D2), so full
 * history is replayed on every request.
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

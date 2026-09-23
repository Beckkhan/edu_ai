package com.eduai.turbofa.history

import java.time.Instant

/** Roles of a history record; they mirror the DeepSeek message roles (5d). */
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

/**
 * One record of the conversation (5e).
 *
 * [receivedAt] is the moment the backend received the message (D5): the arrival of a user
 * prompt, or the receipt of a DeepSeek response — never the later file-write time and never
 * a tool-call time.
 */
data class HistoryRecord(
    val role: String,
    val content: String,
    val receivedAt: Instant,
)

/**
 * R4/5e: the single source of truth for the message history — an in-memory cache plus a
 * plain-text file. DeepSeek is stateless (D2), so every request replays the full record list.
 *
 * The text file is cleared on restart; the startup wiring (Application.kt, T9) calls [clear].
 */
class ChatHistoryStore(
    private val cache: InMemoryHistoryCache,
    private val writer: TextFileHistoryWriter,
) {
    /** Appends a user prompt; it is timestamped when the backend receives it. */
    fun appendUser(content: String) {
        append(HistoryRecord(ROLE_USER, content, Instant.now()))
    }

    /** Appends the assistant reply; [receivedAt] is the DeepSeek response receipt moment (D5). */
    fun appendAssistant(content: String, receivedAt: Instant) {
        append(HistoryRecord(ROLE_ASSISTANT, content, receivedAt))
    }

    /** The whole dialogue in order — what the next request replays to the agent. */
    fun records(): List<HistoryRecord> = cache.records()

    /** R4: clears the cache and truncates the text file. */
    fun clear() {
        cache.clear()
        writer.clear()
    }

    private fun append(record: HistoryRecord) {
        cache.add(record)
        writer.append(record)
    }
}

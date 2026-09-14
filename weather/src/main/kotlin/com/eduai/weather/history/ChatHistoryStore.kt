// src/main/kotlin/com/eduai/weather/history/ChatHistoryStore.kt
package com.eduai.weather.history

import com.eduai.weather.client.deepseek.ChatMessage
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Facade over the in-memory cache and the text file:
 * append() writes to the cache, flushToFile() persists the cache to disk.
 */
class ChatHistoryStore(
    private val cache: InMemoryHistoryCache = InMemoryHistoryCache(),
    private val writer: TextFileHistoryWriter = TextFileHistoryWriter(Paths.get("chat_history.txt")),
) {

    fun append(message: ChatMessage) = cache.append(message)

    fun recent(n: Int = 50): List<ChatMessage> = cache.recent(n)

    /** Last [n] messages from the history file. */
    fun loadRecent(n: Int = 50): List<ChatMessage> = writer.readAll().takeLast(n)

    /** Seeds the in-memory cache with the last [n] persisted messages (called on startup). */
    fun restoreFromDisk(n: Int = 50) {
        loadRecent(n).forEach(cache::append)
    }

    /** Appends all cached messages to the text file, rotating it first when it exceeds 5 MB. */
    fun flushToFile() {
        if (writer.sizeBytes() > MAX_FILE_BYTES) writer.rotate(System.currentTimeMillis())
        cache.all().forEach(writer::append)
        cache.clear()
    }

    /** Clears the in-memory cache and deletes the history file. */
    fun clearAll() {
        cache.clear()
        writer.clear()
    }

    companion object {
        private const val MAX_FILE_BYTES = 5L * 1024 * 1024

        fun fromConfig(filePath: String): ChatHistoryStore =
            ChatHistoryStore(writer = TextFileHistoryWriter(Path.of(filePath)))
    }
}

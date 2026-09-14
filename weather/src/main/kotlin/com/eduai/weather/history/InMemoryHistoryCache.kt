// src/main/kotlin/com/eduai/weather/history/InMemoryHistoryCache.kt
package com.eduai.weather.history

import com.eduai.weather.client.deepseek.ChatMessage

/** Thread-safe in-memory history cache, keeps at most [maxEntries] messages. */
class InMemoryHistoryCache(private val maxEntries: Int = 100) {

    private val messages = ArrayDeque<ChatMessage>()

    @Synchronized
    fun append(message: ChatMessage) {
        messages.addLast(message)
        while (messages.size > maxEntries) messages.removeFirst()
    }

    @Synchronized
    fun recent(n: Int = maxEntries): List<ChatMessage> = messages.takeLast(n)

    @Synchronized
    fun all(): List<ChatMessage> = messages.toList()

    @Synchronized
    fun clear() = messages.clear()
}

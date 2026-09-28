package com.eduai.turbofa.history

// Synchronized: Ktor serves requests on several threads.
class InMemoryHistoryCache {
    private val entries = mutableListOf<HistoryRecord>()

    @Synchronized
    fun add(record: HistoryRecord) {
        entries += record
    }

    @Synchronized
    fun records(): List<HistoryRecord> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

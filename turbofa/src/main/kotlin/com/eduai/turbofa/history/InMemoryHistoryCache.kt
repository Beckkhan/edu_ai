package com.eduai.turbofa.history

/** R4: the in-memory half of the history. Synchronized because Ktor serves requests on several threads. */
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

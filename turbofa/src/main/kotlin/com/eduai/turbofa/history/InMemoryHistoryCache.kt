package com.eduai.turbofa.history

/**
 * R4: the in-memory half of the history — the records replayed to the agent on every call.
 * Synchronized because Ktor serves requests on several threads.
 */
class InMemoryHistoryCache {
    private val entries = mutableListOf<HistoryRecord>()

    @Synchronized
    fun add(record: HistoryRecord) {
        entries += record
    }

    /** Snapshot copy, so callers can never mutate the cache. */
    @Synchronized
    fun records(): List<HistoryRecord> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

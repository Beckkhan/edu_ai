package com.eduai.turbofa.history

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal val HISTORY_FILE: Path = Path.of("logs", "chat-history.txt")

/**
 * File half of the history (R4/5e). One line per record with escaped newlines.
 */
class TextFileHistoryWriter(private val file: Path) {

    fun append(record: HistoryRecord) {
        file.parent?.let { Files.createDirectories(it) }
        val line = "${record.receivedAt} ${record.role}: ${escape(record.content)}\n"
        Files.writeString(
            file,
            line,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    fun clear() {
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(
            file,
            "",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    private fun escape(content: String): String =
        content.replace("\r\n", "\n").replace("\n", "\\n")
}

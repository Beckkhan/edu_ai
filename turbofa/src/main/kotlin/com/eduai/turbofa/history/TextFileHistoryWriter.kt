package com.eduai.turbofa.history

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * R4/5e: the file half of the history — one line per record, plain text:
 *
 * ```
 * 2026-09-23T21:58:14.001Z assistant: The fueling succeeded ...
 * ```
 *
 * Newlines inside the content are escaped as `\n`, so one record always stays one line.
 * The file and its parent directory are created on the first write.
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

    /** R4: truncates the file; called by the startup wiring (Application.kt, T9). */
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

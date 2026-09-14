// src/main/kotlin/com/eduai/weather/history/TextFileHistoryWriter.kt
package com.eduai.weather.history

import com.eduai.weather.client.deepseek.ChatMessage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Appends messages to a text file, one per line: role: content. */
class TextFileHistoryWriter(private val file: Path) {

    private companion object {
        val VALID_ROLES = setOf("user", "assistant", "system")
    }

    init {
        file.parent?.let(Files::createDirectories)
    }

    fun append(message: ChatMessage) {
        Files.writeString(
            file,
            "${message.role}: ${message.content}\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }

    fun readAll(): List<ChatMessage> =
        if (!Files.exists(file)) emptyList()
        else Files.readAllLines(file, StandardCharsets.UTF_8).mapNotNull { line ->
            val idx = line.indexOf(": ")
            if (idx <= 0) null
            else {
                val role = line.substring(0, idx)
                if (role !in VALID_ROLES) null
                else ChatMessage(role = role, content = line.substring(idx + 2))
            }
        }

    fun sizeBytes(): Long = if (Files.exists(file)) Files.size(file) else 0L

    /** Renames the current file to <name>.<timestamp><ext>; the next append starts a fresh file. */
    fun rotate(timestamp: Long) {
        if (!Files.exists(file)) return
        val name = file.fileName.toString()
        val idx = name.lastIndexOf('.')
        val rotatedName =
            if (idx > 0) "${name.substring(0, idx)}.$timestamp${name.substring(idx)}" else "$name.$timestamp"
        Files.move(file, file.resolveSibling(rotatedName), StandardCopyOption.REPLACE_EXISTING)
    }

    /** Deletes the history file (used to start with a clean history). */
    fun clear() {
        Files.deleteIfExists(file)
    }
}

package com.eduai.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Application configuration.
 *
 * Values come from environment variables first; if a variable is not set, a
 * `.env` file in the working directory (see `.env.example`) is used as a
 * fallback. Explicit constructor values are used by tests.
 */
data class AppConfig(
    val apiKey: String? = System.getenv("DEEPSEEK_API_KEY") ?: DotEnv["DEEPSEEK_API_KEY"],
    val baseUrl: String = System.getenv("DEEPSEEK_BASE_URL") ?: DotEnv["DEEPSEEK_BASE_URL"] ?: DEFAULT_BASE_URL,
    val model: String = System.getenv("DEEPSEEK_MODEL") ?: DotEnv["DEEPSEEK_MODEL"] ?: DEFAULT_MODEL,
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: DotEnv["PORT"]?.toIntOrNull() ?: DEFAULT_PORT,
) {
    /** Returns the API key or throws [MissingApiKeyException] with a helpful message. */
    fun requireApiKey(): String =
        apiKey?.takeIf { it.isNotBlank() }
            ?: throw MissingApiKeyException(
                "DEEPSEEK_API_KEY is not set. Get a key at https://platform.deepseek.com and " +
                    "either export it (export DEEPSEEK_API_KEY=sk-...) or put it in a .env file " +
                    "(see .env.example)."
            )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-v4-pro"
        const val DEFAULT_PORT = 8080
    }
}

class MissingApiKeyException(message: String) : Exception(message)

/** Minimal `.env` file support: KEY=VALUE lines, `#` comments, optional surrounding quotes. */
private object DotEnv {
    private val values: Map<String, String> by lazy { parse(Paths.get(".env")) }

    operator fun get(key: String): String? = values[key]

    private fun parse(path: Path): Map<String, String> =
        if (!Files.isRegularFile(path)) emptyMap()
        else Files.readAllLines(path)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim().trimQuotes()
                key to value
            }
            .toMap()

    private fun String.trimQuotes(): String =
        if (length >= 2 && startsWith("\"") && endsWith("\"")) substring(1, length - 1) else this
}

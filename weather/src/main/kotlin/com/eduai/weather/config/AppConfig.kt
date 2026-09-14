// src/main/kotlin/com/eduai/weather/config/AppConfig.kt
package com.eduai.weather.config

import java.io.File

class AppConfig(env: Map<String, String> = loadEnv()) {

    val apiKey: String = env["DEEPSEEK_API_KEY"] ?: error("DEEPSEEK_API_KEY is not set")
    val model: String = env["DEEPSEEK_MODEL"] ?: "deepseek-chat"
    val apiBaseUrl: String = env["DEEPSEEK_BASE_URL"] ?: "https://api.deepseek.com"
    val historyFilePath: String = env["HISTORY_FILE_PATH"] ?: "chat_history.txt"

    // Local Docker Postgres (postgres:15-alpine) defaults
    val dbUrl: String = env["DB_URL"] ?: "jdbc:postgresql://localhost:5432/weather"
    val dbUser: String = env["DB_USER"] ?: "weather"
    val dbPassword: String = env["DB_PASSWORD"] ?: "weather"

    companion object {
        private const val ENV_FILE = ".env"

        /** Reads .env from the project root; system env is the fallback when the file is missing. */
        private fun loadEnv(): Map<String, String> =
            System.getenv() + readEnvFile()

        /** Parses KEY=VALUE lines; skips empty lines and # comments. */
        private fun readEnvFile(): Map<String, String> {
            val file = File(ENV_FILE)
            if (!file.isFile) return emptyMap()
            return file.readLines()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) null
                    else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }
                .toMap()
        }
    }
}

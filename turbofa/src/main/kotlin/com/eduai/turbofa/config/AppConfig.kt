package com.eduai.turbofa.config

import java.io.File

class AppConfig(env: Map<String, String> = loadEnv()) {

    val deepSeekApiKey: String = env.required("DEEPSEEK_API_KEY")

    val deepSeekModel: String = env.optional("DEEPSEEK_MODEL") ?: DEFAULT_DEEPSEEK_MODEL

    val ollamaBaseUrl: String = env.optional("OLLAMA_BASE_URL") ?: DEFAULT_OLLAMA_BASE_URL

    val ollamaModel: String = env.optional("OLLAMA_MODEL") ?: DEFAULT_OLLAMA_MODEL

    val dbHost: String = env.required("DB_HOST")

    val dbPort: Int = env.intOr("DB_PORT", DEFAULT_DB_PORT)

    val dbUser: String = env.required("DB_USER")

    val dbPassword: String = env.required("DB_PASSWORD")

    val fuelingJdbcUrl: String = jdbcUrl(FUELING_DATABASE)

    val paymentJdbcUrl: String = jdbcUrl(PAYMENT_DATABASE)

    val vendorsJdbcUrl: String = jdbcUrl(VENDORS_DATABASE)

    private fun jdbcUrl(database: String): String = "jdbc:postgresql://$dbHost:$dbPort/$database"

    companion object {
        const val ENV_FILE = ".env"

        private const val DEFAULT_DEEPSEEK_MODEL = "deepseek-chat"
        private const val DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"
        private const val DEFAULT_OLLAMA_MODEL = "qwen3:8b"
        private const val DEFAULT_DB_PORT = 5432

        // Fixed domain database names of D7; DB_URL (the admin database) is never used for them (E3).
        private const val FUELING_DATABASE = "fueling"
        private const val PAYMENT_DATABASE = "payment"
        private const val VENDORS_DATABASE = "vendors"

        private fun loadEnv(): Map<String, String> = readEnvFile() + System.getenv()

        private fun readEnvFile(): Map<String, String> {
            val file = File(ENV_FILE)
            if (!file.isFile) return emptyMap()
            return file.readLines()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null
                    else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }
                .toMap()
        }
    }
}

private fun Map<String, String>.optional(name: String): String? =
    this[name]?.trim()?.takeIf(String::isNotEmpty)

private fun Map<String, String>.required(name: String): String =
    optional(name) ?: error("$name is not set — add it to .env or export it")

private fun Map<String, String>.intOr(name: String, default: Int): Int {
    val raw = optional(name) ?: return default
    return raw.toIntOrNull() ?: error("$name must be an integer, got '$raw'")
}

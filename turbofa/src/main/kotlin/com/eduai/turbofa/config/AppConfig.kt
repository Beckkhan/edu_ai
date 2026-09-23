// src/main/kotlin/com/eduai/turbofa/config/AppConfig.kt
package com.eduai.turbofa.config

import java.io.File

/**
 * Startup configuration: the only place in turbofa that reads the environment.
 *
 * Values come from the process environment; a variable that is not set falls back to the `.env`
 * file of the working directory (the project root under `./gradlew run`, layout of .env.example).
 * The environment wins over the file, so an exported variable overrides a stale file.
 *
 * Per D7/E3 the three JDBC URLs are derived here from DB_HOST / DB_PORT plus the fixed database
 * names `fueling`, `payment`, `vendors`. The .env `DB_URL` points at the `postgres` admin database,
 * which holds none of the domain tables, and is deliberately never read.
 *
 * Credentials have no defaults: a missing one fails at startup with a clear message instead of
 * surfacing as an authentication error on the first request, and no default ever carries a secret.
 *
 * @param env variables to read; injectable so callers and tests can supply their own set
 */
class AppConfig(env: Map<String, String> = loadEnv()) {

    /** DEEPSEEK_API_KEY, injected into the Koog client by the T9 wiring; never logged (5a). */
    val deepSeekApiKey: String = env.required("DEEPSEEK_API_KEY")

    /** DEEPSEEK_MODEL; DeepSeek's general chat model is the fallback (5c). */
    val deepSeekModel: String = env.optional("DEEPSEEK_MODEL") ?: DEFAULT_DEEPSEEK_MODEL

    /** DB_HOST of the external stage server (R5). */
    val dbHost: String = env.required("DB_HOST")

    /** DB_PORT of the external stage server; PostgreSQL's default port otherwise. */
    val dbPort: Int = env.intOr("DB_PORT", DEFAULT_DB_PORT)

    /** DB_USER of the external stage server; never logged (5a). */
    val dbUser: String = env.required("DB_USER")

    /** DB_PASSWORD of the external stage server; never logged (5a). */
    val dbPassword: String = env.required("DB_PASSWORD")

    /** JDBC URL of the `fueling` database — DataSourceFactory input of the T9 wiring (D7). */
    val fuelingJdbcUrl: String = jdbcUrl(FUELING_DATABASE)

    /** JDBC URL of the `payment` database — DataSourceFactory input of the T9 wiring (D7). */
    val paymentJdbcUrl: String = jdbcUrl(PAYMENT_DATABASE)

    /** JDBC URL of the `vendors` database — DataSourceFactory input of the T9 wiring (D7). */
    val vendorsJdbcUrl: String = jdbcUrl(VENDORS_DATABASE)

    private fun jdbcUrl(database: String): String = "jdbc:postgresql://$dbHost:$dbPort/$database"

    companion object {
        /** Environment file read for variables the process environment does not set. */
        const val ENV_FILE = ".env"

        private const val DEFAULT_DEEPSEEK_MODEL = "deepseek-chat"
        private const val DEFAULT_DB_PORT = 5432

        // Fixed domain database names of D7; DB_URL (the admin database) is never used for them (E3).
        private const val FUELING_DATABASE = "fueling"
        private const val PAYMENT_DATABASE = "payment"
        private const val VENDORS_DATABASE = "vendors"

        /** Process environment plus the `.env` file; the environment wins on conflicts. */
        private fun loadEnv(): Map<String, String> = readEnvFile() + System.getenv()

        /** Parses KEY=VALUE lines; skips empty lines and `#` comments. */
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

/** The trimmed value of [name], or null when unset or blank. */
private fun Map<String, String>.optional(name: String): String? =
    this[name]?.trim()?.takeIf(String::isNotEmpty)

/** The trimmed value of [name]; a missing credential fails fast instead of failing at runtime. */
private fun Map<String, String>.required(name: String): String =
    optional(name) ?: error("$name is not set — add it to .env or export it")

/** The integer value of [name]; [default] when unset or blank, a clear error when malformed. */
private fun Map<String, String>.intOr(name: String, default: Int): Int {
    val raw = optional(name) ?: return default
    return raw.toIntOrNull() ?: error("$name must be an integer, got '$raw'")
}

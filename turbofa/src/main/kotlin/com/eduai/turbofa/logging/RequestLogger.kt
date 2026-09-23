// src/main/kotlin/com/eduai/turbofa/logging/RequestLogger.kt
package com.eduai.turbofa.logging

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * R2 logging contract (docs/project-specification.md, contract 5a).
 *
 * The five call sites of spec 3.2 emit one multi-line event each; an event starts with a
 * label line "<ISO-8601 local date/time> <label>:" followed by the body as pretty-printed
 * JSON (2-space indent) - never an ellipsis, never a truncated body (R8). Events land only
 * in logs/turbofa.log through the logger "com.eduai.turbofa.requestlog" (logback.xml, D4).
 *
 * Secrets (Authorization, DEEPSEEK_API_KEY, DB_PASSWORD) never appear in any event.
 */
interface RequestLogger {
    fun brunoRequest(json: String)
    fun deepSeekRequest(json: String)
    fun deepSeekResponse(json: String)

    /** Called only when a tool is actually invoked for the request (R8). */
    fun toolCall(json: String)

    fun brunoResponse(json: String)
}

/**
 * SLF4J/logback implementation of [RequestLogger].
 *
 * @param loggerName logger the R2 file appender is attached to (D4)
 * @param secretValues extra secret values to mask; DEEPSEEK_API_KEY and DB_PASSWORD are
 *   always read from the environment and masked as well
 */
class Slf4jRequestLogger(
    loggerName: String = LOGGER_NAME,
    secretValues: Collection<String> = emptyList(),
) : RequestLogger {

    private val log: Logger = LoggerFactory.getLogger(loggerName)

    /** Values that must never reach the log; short ones are skipped (they would mangle text). */
    private val secrets: List<String> = (secretValues + secretsFromEnvironment())
        .filter { it.isNotBlank() && it.length >= MIN_SECRET_LENGTH }
        .distinct()

    override fun brunoRequest(json: String) = emit(LABEL_BRUNO_REQUEST, json)

    override fun deepSeekRequest(json: String) = emit(LABEL_DEEPSEEK_REQUEST, json)

    override fun deepSeekResponse(json: String) = emit(LABEL_DEEPSEEK_RESPONSE, json)

    override fun toolCall(json: String) = emit(LABEL_TOOL_CALL, json)

    override fun brunoResponse(json: String) = emit(LABEL_BRUNO_RESPONSE, json)

    private fun emit(label: String, json: String) {
        log.info(formatEvent(currentTimestamp(), label, json))
    }

    /**
     * One R2 event: the label line plus the redacted body as pretty-printed JSON (2-space
     * indent). A body that is not valid JSON is written redacted and verbatim instead of
     * being dropped - R8 forbids ellipses and truncated bodies.
     */
    internal fun formatEvent(dateTime: String, label: String, json: String): String =
        "$dateTime $label:\n" + prettyPrinted(redact(json))

    private fun prettyPrinted(json: String): String = try {
        PRETTY_JSON.encodeToString(JsonElement.serializer(), PRETTY_JSON.parseToJsonElement(json))
    } catch (_: IllegalArgumentException) {
        json
    }

    /** Masks secret values and Authorization / api-key / password fields before anything is written. */
    internal fun redact(text: String): String {
        var redacted = text
        for (secret in secrets) redacted = redacted.replace(secret, MASK)
        redacted = SECRET_FIELD.replace(redacted) { it.groupValues[1] + MASK + it.groupValues[3] }
        redacted = AUTHORIZATION_VALUE.replace(redacted) { it.groupValues[1] + MASK }
        return BEARER_TOKEN.replace(redacted) { it.groupValues[1] + MASK }
    }

    companion object {
        /** Logger wired to the R2 file appender in logback.xml (D4). */
        const val LOGGER_NAME = "com.eduai.turbofa.requestlog"

        // Labels exactly as R8 / spec 3.2 spell them.
        const val LABEL_BRUNO_REQUEST = "Request from Bruno to backend"
        const val LABEL_DEEPSEEK_REQUEST = "Request to Deepseek"
        const val LABEL_DEEPSEEK_RESPONSE = "Response from Deepseek"
        const val LABEL_TOOL_CALL = "Tool call"
        const val LABEL_BRUNO_RESPONSE = "Response from backend to Bruno"

        /** Placeholder that replaces every masked value. */
        const val MASK = "***"

        private const val MIN_SECRET_LENGTH = 4

        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        @OptIn(ExperimentalSerializationApi::class)
        private val PRETTY_JSON = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        /** JSON fields whose value is a secret, e.g. {"Authorization": "Bearer ..."} or {"DB_PASSWORD": "..."}. */
        private val SECRET_FIELD = Regex(
            """("(?:authorization|api[_-]?key|deepseek_api_key|db_password|password|secret|token)"\s*:\s*")([^"]*)(")""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Header form without JSON quotes, e.g. "Authorization: Bearer sk-...". The token class
         * stops at whitespace, quotes, commas and braces so it never swallows JSON structure or
         * a following "Bearer" keyword: every occurrence is masked independently, and the
         * separate [BEARER_TOKEN] pass still fires for the rest of the text.
         */
        private val AUTHORIZATION_VALUE = Regex(
            """(authorization\s*[:=]\s*)(?:bearer\s+)?([^\s",}]+)""",
            RegexOption.IGNORE_CASE,
        )

        /** A bearer token anywhere, e.g. inside a logged exception message. */
        private val BEARER_TOKEN = Regex(
            """(bearer\s+)([A-Za-z0-9._~+/=-]+)""",
            RegexOption.IGNORE_CASE,
        )

        private fun currentTimestamp(): String =
            LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).format(DATE_TIME)

        private fun secretsFromEnvironment(): List<String> = listOfNotNull(
            System.getenv("DEEPSEEK_API_KEY"),
            System.getenv("DB_PASSWORD"),
        )
    }
}

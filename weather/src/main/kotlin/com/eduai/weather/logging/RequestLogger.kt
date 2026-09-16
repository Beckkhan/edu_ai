// src/main/kotlin/com/eduai/weather/logging/RequestLogger.kt
package com.eduai.weather.logging

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * R2 logging contract (docs/project-specification.md, contract 5a). An event is multi-line:
 * a label line "<date/time> <label>:" followed by the body as pretty-printed JSON (2-space
 * indent). Events are counted by label lines — grepping one label yields one event per request.
 */
interface RequestLogger {
    fun brunoRequest(json: String)
    fun deepSeekRequest(json: String)
    fun deepSeekResponse(json: String)
    fun toolCall(json: String)      // called only when a tool is actually invoked
    fun brunoResponse(json: String)
}

/** SLF4J/logback implementation of [RequestLogger]. Secrets are redacted before writing. */
class Slf4jRequestLogger(
    loggerName: String = "com.eduai.weather.requestlog",
) : RequestLogger {

    private val log = LoggerFactory.getLogger(loggerName)

    override fun brunoRequest(json: String) = emit(LABEL_BRUNO_REQUEST, json)
    override fun deepSeekRequest(json: String) = emit(LABEL_DEEPSEEK_REQUEST, json)
    override fun deepSeekResponse(json: String) = emit(LABEL_DEEPSEEK_RESPONSE, json)
    override fun toolCall(json: String) = emit(LABEL_TOOL_CALL, json)
    override fun brunoResponse(json: String) = emit(LABEL_BRUNO_RESPONSE, json)

    private fun emit(label: String, json: String) {
        log.info(formatEvent(LocalDateTime.now().format(DATE_TIME), label, json))
    }

    companion object {
        const val LABEL_BRUNO_REQUEST = "Request from Bruno to backend"
        const val LABEL_DEEPSEEK_REQUEST = "Request to Deepseek"
        const val LABEL_DEEPSEEK_RESPONSE = "Response from Deepseek"
        const val LABEL_TOOL_CALL = "Tool call"
        const val LABEL_BRUNO_RESPONSE = "Response to Bruno"

        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

        /**
         * R2 event (5a): label line "<date/time> <label>:" plus the redacted body as
         * pretty-printed JSON on the following lines. One event = one label line.
         */
        internal fun formatEvent(dateTime: String, label: String, json: String): String =
            "$dateTime $label:\n" + prettyJson.encodeToString(
                prettyJson.parseToJsonElement(redact(json))
            )

        /** Masks secret values (API keys, Authorization headers) before anything is written. */
        internal fun redact(json: String): String =
            json.replace(SECRET_PATTERN) { match ->
                match.groupValues[1] + "***" + match.groupValues[3]
            }

        private val SECRET_PATTERN = Regex(
            """("?(?:authorization|api[_-]?key|api_key)"?\s*[:=]\s*")([^"]*)(")""",
            RegexOption.IGNORE_CASE,
        )
    }
}

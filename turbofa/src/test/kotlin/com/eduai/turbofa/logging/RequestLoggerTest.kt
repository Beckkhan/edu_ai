// src/test/kotlin/com/eduai/turbofa/logging/RequestLoggerTest.kt
package com.eduai.turbofa.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests of the R2 event contract (5a, D10): the six labels, the single-line
 * "<date/time> <label>: <compact json>" format, and secret redaction.
 *
 * The tests use the internal formatEvent/redact hooks plus a throwaway capture logger, so no
 * event is ever written to the R2 appenders (console, logs/turbofa.log) and no logback config is
 * touched. Unit tests need no DB, no network and no .env.
 */
class RequestLoggerTest {

    private val dateTime = "2026-09-23 21:58:14.001"

    private fun logger(secretValues: List<String> = emptyList()): Slf4jRequestLogger =
        Slf4jRequestLogger(loggerName = CAPTURE_LOGGER, secretValues = secretValues)

    @Test
    fun `all six labels keep their exact R2 wording`() {
        assertEquals("Request from Bruno to backend", Slf4jRequestLogger.LABEL_BRUNO_REQUEST)
        assertEquals("Request from backend to DeepSeek", Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST)
        assertEquals("Response from DeepSeek to backend", Slf4jRequestLogger.LABEL_DEEPSEEK_RESPONSE)
        assertEquals("Request from backend to Postgres", Slf4jRequestLogger.LABEL_TOOL_CALL)
        assertEquals("Response from Postgres to backend", Slf4jRequestLogger.LABEL_POSTGRES_RESPONSE)
        assertEquals("Response from backend to Bruno", Slf4jRequestLogger.LABEL_BRUNO_RESPONSE)
    }

    @Test
    fun `an event is one line - date, label with colon, then the compact body`() {
        val event = logger().formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_BRUNO_REQUEST,
            """{"prompt":"What happened with my fueling?","fueling_id":"99f068ca"}""",
        )

        assertEquals(1, event.lines().size, "the event is a single line (D10)")
        assertEquals(
            "$dateTime Request from Bruno to backend: " +
                """{"prompt":"What happened with my fueling?","fueling_id":"99f068ca"}""",
            event,
            "compact JSON on the same line — no pretty-print, no newlines",
        )
        assertEquals(
            Json.parseToJsonElement("""{"prompt":"What happened with my fueling?","fueling_id":"99f068ca"}"""),
            Json.parseToJsonElement(event.substringAfter(": ")),
            "the body must parse back to the logged JSON",
        )
        assertFalse("\n" in event, "no line breaks anywhere in the event")
    }

    @Test
    fun `the whole body is written - never an ellipsis or a truncated value`() {
        val longPrompt = "fueling analysis ".repeat(200)
        val event = logger().formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_DEEPSEEK_RESPONSE,
            """{"content":"$longPrompt"}""",
        )

        assertTrue(longPrompt in event, "the full value must be present (R8: no ellipsis)")
        assertFalse("..." in event)
    }

    @Test
    fun `each log point emits its label with a yyyy-MM-dd HH-mm-ss-SSS timestamp`() {
        val body = """{"prompt":"hi"}"""
        val events = capturedEvents { logger ->
            logger.brunoRequest(body)
            logger.deepSeekRequest(body)
            logger.deepSeekResponse(body)
            logger.toolCall(body)
            logger.postgresResponse(body)
            logger.brunoResponse(body)
        }

        assertEquals(6, events.size, "one event per log point")
        assertEquals(
            listOf(
                Slf4jRequestLogger.LABEL_BRUNO_REQUEST,
                Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
                Slf4jRequestLogger.LABEL_DEEPSEEK_RESPONSE,
                Slf4jRequestLogger.LABEL_TOOL_CALL,
                Slf4jRequestLogger.LABEL_POSTGRES_RESPONSE,
                Slf4jRequestLogger.LABEL_BRUNO_RESPONSE,
            ),
            events.map { it.substringAfter(' ').substringAfter(' ').substringBefore(": ") },
            "each method must emit its own R8 label (spec 3.2)",
        )
        events.forEach { event ->
            // timestamp parse throws unless the event starts with yyyy-MM-dd HH:mm:ss.SSS
            TIMESTAMP.parse(event.take(TIMESTAMP_LENGTH))
            assertTrue(event.lines().size == 1, "single-line event (D10)")
        }
    }

    @Test
    fun `injected secret values never appear in the event`() {
        val apiKey = "sk-deepseek-super-secret-1234"
        val dbPassword = "db-pa55word-9876"
        val event = logger(listOf(apiKey, dbPassword)).formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
            """{"model":"deepseek-v4","api_key":"$apiKey","db_password":"$dbPassword","prompt":"hi"}""",
        )

        assertFalse(apiKey in event, "an injected DEEPSEEK_API_KEY value must never reach the log")
        assertFalse(dbPassword in event, "an injected DB_PASSWORD value must never reach the log")
        assertTrue(Slf4jRequestLogger.MASK in event)
        val body = Json.parseToJsonElement(event.substringAfter(": ")).jsonObject
        assertEquals("hi", body.getValue("prompt").jsonPrimitive.content, "non-secret fields survive")
    }

    @Test
    fun `secret-named fields are masked without knowing their values`() {
        val event = logger().formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_BRUNO_REQUEST,
            """{"DEEPSEEK_API_KEY":"AnyVeryLongValue","DB_PASSWORD":"another-value","password":"p","token":"t","secret":"s","prompt":"keep me"}""",
        )

        assertFalse("AnyVeryLongValue" in event)
        assertFalse("another-value" in event)
        val body = Json.parseToJsonElement(event.substringAfter(": ")).jsonObject
        listOf("DEEPSEEK_API_KEY", "DB_PASSWORD", "password", "token", "secret").forEach { field ->
            assertEquals(Slf4jRequestLogger.MASK, body.getValue(field).jsonPrimitive.content, field)
        }
        assertEquals("keep me", body.getValue("prompt").jsonPrimitive.content)
    }

    @Test
    fun `an Authorization field in a JSON body is masked and the body stays valid JSON`() {
        val token = "sk-abcdef123456"
        val event = logger().formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
            """{"headers":{"Authorization":"Bearer $token"},"prompt":"hi"}""",
        )

        assertFalse(token in event, "the Bearer token must not survive")
        val body = Json.parseToJsonElement(event.substringAfter(": ")).jsonObject
        assertEquals(
            Slf4jRequestLogger.MASK,
            body.getValue("headers").jsonObject.getValue("Authorization").jsonPrimitive.content,
        )
        assertEquals("hi", body.getValue("prompt").jsonPrimitive.content)
    }

    @Test
    fun `plain Authorization and Bearer text is masked in a body that is not JSON`() {
        val token = "sk-abcdef123456"
        val event = logger().formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_DEEPSEEK_RESPONSE,
            "Authorization: Bearer $token\nand again Bearer $token",
        )

        assertFalse(token in event, "the token must not survive in either shape")
        assertFalse("Bearer $token" in event, "the whole Authorization value must be masked (D3 logs headers too)")
        assertTrue(Slf4jRequestLogger.MASK in event)
    }

    @Test
    fun `regression - two Bearer tokens in one JSON body are both masked and the body stays parseable JSON`() {
        val token = "sk-abcdef123456"
        val event = logger().formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
            """{"note":"Authorization: Bearer $token","trace":"Bearer $token"}""",
        )

        assertFalse(token in event, "the second occurrence must not survive the first Authorization match")
        assertFalse("Bearer $token" in event)
        // The body must still be the logged JSON, not the raw fallback of a mangled string
        val body = Json.parseToJsonElement(event.substringAfter(": ")).jsonObject
        assertTrue(Slf4jRequestLogger.MASK in body.getValue("note").jsonPrimitive.content)
        assertTrue(Slf4jRequestLogger.MASK in body.getValue("trace").jsonPrimitive.content)
    }

    @Test
    fun `regression - masking an Authorization value keeps the rest of the JSON body intact`() {
        val token = "sk-abcdef123456"
        val event = logger().formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
            """{"note":"Authorization: Bearer $token","prompt":"hi"}""",
        )

        assertFalse(token in event)
        val body = Json.parseToJsonElement(event.substringAfter(": ")).jsonObject
        assertTrue(Slf4jRequestLogger.MASK in body.getValue("note").jsonPrimitive.content)
        assertEquals("hi", body.getValue("prompt").jsonPrimitive.content, "the rest of the body must survive")
    }

    @Test
    fun `redact leaves a body without secrets unchanged`() {
        val body = """{"prompt":"status of 99f068ca-ac6a-43fb-a53b-d2e7a573cfe2?"}"""
        assertEquals(body, logger().redact(body))
    }

    @Test
    fun `a non-JSON body is emitted redacted instead of dropped or truncated`() {
        val token = "sk-abcdef1234"
        val event = logger(listOf(token)).formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_DEEPSEEK_RESPONSE,
            "raw response, token=$token",
        )

        assertTrue(event.startsWith("$dateTime Response from DeepSeek to backend: "))
        assertFalse(token in event)
        assertTrue("raw response, token=${Slf4jRequestLogger.MASK}" in event, "the body must not be dropped")
        assertEquals(1, event.lines().size, "single-line event (D10)")
    }

    /** Runs [block] against a throwaway logger and returns the formatted events it emitted. */
    private fun capturedEvents(block: (RequestLogger) -> Unit): List<String> {
        val logbackLogger = LoggerFactory.getLogger(CAPTURE_LOGGER) as LogbackLogger
        logbackLogger.isAdditive = false
        logbackLogger.level = Level.INFO
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logbackLogger.addAppender(appender)
        try {
            block(logger())
        } finally {
            logbackLogger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.map { it.formattedMessage }
    }

    private companion object {
        /** Deliberately not the R2 logger name: no test event reaches the R2 appenders (D10). */
        const val CAPTURE_LOGGER = "turbofa.test.requestlog.capture"

        private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        /** "yyyy-MM-dd HH:mm:ss.SSS" is exactly 23 characters. */
        private const val TIMESTAMP_LENGTH = 23
    }
}

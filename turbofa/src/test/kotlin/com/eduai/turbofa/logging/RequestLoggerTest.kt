// src/test/kotlin/com/eduai/turbofa/logging/RequestLoggerTest.kt
package com.eduai.turbofa.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests of the R2 event contract (5a): the five labels, the "<date/time> <label>:" line
 * followed by pretty-printed JSON, and secret redaction.
 *
 * The tests use the internal formatEvent/redact hooks plus a throwaway capture logger, so no
 * event is ever written to the R2 file appender (logs/turbofa.log) and no logback config is
 * touched. Unit tests need no DB, no network and no .env.
 */
class RequestLoggerTest {

    private val dateTime = "2026-09-23T21:58:14.001"

    private fun logger(secretValues: List<String> = emptyList()): Slf4jRequestLogger =
        Slf4jRequestLogger(loggerName = CAPTURE_LOGGER, secretValues = secretValues)

    @Test
    fun `all five labels keep their exact R2 wording`() {
        assertEquals("Request from Bruno to backend", Slf4jRequestLogger.LABEL_BRUNO_REQUEST)
        assertEquals("Request to Deepseek", Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST)
        assertEquals("Response from Deepseek", Slf4jRequestLogger.LABEL_DEEPSEEK_RESPONSE)
        assertEquals("Tool call", Slf4jRequestLogger.LABEL_TOOL_CALL)
        assertEquals("Response from backend to Bruno", Slf4jRequestLogger.LABEL_BRUNO_RESPONSE)
    }

    @Test
    fun `an event is the date, the label with a colon, then the pretty-printed body`() {
        val event = logger().formatEvent(
            dateTime,
            Slf4jRequestLogger.LABEL_BRUNO_REQUEST,
            """{"prompt":"What happened with my fueling?","fueling_id":"99f068ca"}""",
        )

        val lines = event.lines()
        assertEquals("$dateTime Request from Bruno to backend:", lines.first())
        assertTrue(lines.size > 1, "the body is a multi-line pretty JSON block")
        assertEquals(
            Json.parseToJsonElement("""{"prompt":"What happened with my fueling?","fueling_id":"99f068ca"}"""),
            Json.parseToJsonElement(event.substringAfter('\n')),
            "the body must parse back to the logged JSON",
        )
        assertTrue("\n  \"prompt\"" in event, "pretty printing uses a 2-space indent (5a)")
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
    fun `each log point emits its label with an ISO-8601 local timestamp`() {
        val body = """{"prompt":"hi"}"""
        val events = capturedEvents { logger ->
            logger.brunoRequest(body)
            logger.deepSeekRequest(body)
            logger.deepSeekResponse(body)
            logger.toolCall(body)
            logger.brunoResponse(body)
        }

        assertEquals(5, events.size, "one event per log point")
        assertEquals(
            listOf(
                Slf4jRequestLogger.LABEL_BRUNO_REQUEST,
                Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
                Slf4jRequestLogger.LABEL_DEEPSEEK_RESPONSE,
                Slf4jRequestLogger.LABEL_TOOL_CALL,
                Slf4jRequestLogger.LABEL_BRUNO_RESPONSE,
            ),
            events.map { it.lines().first().substringAfter(' ').removeSuffix(":") },
            "each method must emit its own R8 label (spec 3.2)",
        )
        events.forEach { event ->
            val labelLine = event.lines().first()
            LocalDateTime.parse(labelLine.substringBefore(' ')) // throws unless ISO-8601 local
            assertTrue(labelLine.endsWith(":") && '\n' in event)
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
        val body = Json.parseToJsonElement(event.substringAfter('\n')).jsonObject
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
        val body = Json.parseToJsonElement(event.substringAfter('\n')).jsonObject
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
        val body = Json.parseToJsonElement(event.substringAfter('\n')).jsonObject
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
        val body = Json.parseToJsonElement(event.substringAfter('\n')).jsonObject
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
        val body = Json.parseToJsonElement(event.substringAfter('\n')).jsonObject
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

        assertEquals("$dateTime Response from Deepseek:", event.lines().first())
        assertFalse(token in event)
        assertTrue("raw response, token=${Slf4jRequestLogger.MASK}" in event, "the body must not be dropped")
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
        /** Deliberately not the R2 logger name: no test event reaches logs/turbofa.log (D4). */
        const val CAPTURE_LOGGER = "turbofa.test.requestlog.capture"
    }
}

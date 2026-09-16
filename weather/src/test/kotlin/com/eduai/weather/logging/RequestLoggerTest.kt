// src/test/kotlin/com/eduai/weather/logging/RequestLoggerTest.kt
package com.eduai.weather.logging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Unit tests for the R2 event format (contract 5a): label line + parseable pretty JSON. */
class RequestLoggerTest {

    @Test
    fun `event starts with the label line`() {
        val event = Slf4jRequestLogger.formatEvent(
            "2026-09-16 18:30:00",
            Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
            """{"model":"deepseek-v4-pro"}""",
        )
        assertEquals("2026-09-16 18:30:00 Request to Deepseek:", event.lines().first())
    }

    @Test
    fun `event body is parseable pretty JSON with 2-space indent`() {
        val event = Slf4jRequestLogger.formatEvent(
            "2026-09-16 18:30:00",
            Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
            """{"model":"deepseek-v4-pro","n":2}""",
        )
        val body = event.substringAfter('\n')
        assertTrue(body.contains("\n  \"model\""), "body must be pretty-printed with 2-space indent")
        val parsed = Json.parseToJsonElement(body)
        assertEquals("deepseek-v4-pro", parsed.jsonObject.getValue("model").jsonPrimitive.content)
    }

    @Test
    fun `all five labels keep their R2 wording`() {
        assertEquals("Request from Bruno to backend", Slf4jRequestLogger.LABEL_BRUNO_REQUEST)
        assertEquals("Request to Deepseek", Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST)
        assertEquals("Response from Deepseek", Slf4jRequestLogger.LABEL_DEEPSEEK_RESPONSE)
        assertEquals("Tool call", Slf4jRequestLogger.LABEL_TOOL_CALL)
        assertEquals("Response to Bruno", Slf4jRequestLogger.LABEL_BRUNO_RESPONSE)
    }

    @Test
    fun `secrets are redacted and the event stays parseable`() {
        val event = Slf4jRequestLogger.formatEvent(
            "2026-09-16 18:30:00",
            Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
            """{"Authorization":"Bearer sk-1234","api_key":"secret-value","prompt":"hi"}""",
        )
        assertTrue("sk-1234" !in event, "Authorization value must be masked")
        assertTrue("secret-value" !in event, "api_key value must be masked")
        assertTrue("***" in event, "mask placeholder must be present")
        assertTrue("\"prompt\"" in event, "non-secret content must survive")
        // The body remains valid JSON after redaction
        Json.parseToJsonElement(event.substringAfter('\n'))
    }

    @Test
    fun `one event has exactly one label line`() {
        val event = Slf4jRequestLogger.formatEvent(
            "2026-09-16 18:30:00",
            Slf4jRequestLogger.LABEL_TOOL_CALL,
            """{"city":"Moscow","country":"Russia"}""",
        )
        assertEquals(1, event.lines().count { it.endsWith("Tool call:") })
    }
}

// src/test/kotlin/com/eduai/weather/logging/RequestLoggerTest.kt
package com.eduai.weather.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Unit tests for the R2 line format and secret redaction (contract 5a). */
class RequestLoggerTest {

    @Test
    fun `line matches the R2 format`() {
        val line = Slf4jRequestLogger.formatLine(
            "2026-09-16 18:30:00",
            Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST,
            """{"model":"deepseek-v4-pro"}""",
        )
        assertEquals(
            """2026-09-16 18:30:00 Request to Deepseek: {"model":"deepseek-v4-pro"}""",
            line,
        )
    }

    @Test
    fun `all five labels exist and keep their R2 wording`() {
        assertEquals("Request from Bruno to backend", Slf4jRequestLogger.LABEL_BRUNO_REQUEST)
        assertEquals("Request to Deepseek", Slf4jRequestLogger.LABEL_DEEPSEEK_REQUEST)
        assertEquals("Response from Deepseek", Slf4jRequestLogger.LABEL_DEEPSEEK_RESPONSE)
        assertEquals("Tool call", Slf4jRequestLogger.LABEL_TOOL_CALL)
        assertEquals("Response to Bruno", Slf4jRequestLogger.LABEL_BRUNO_RESPONSE)
    }

    @Test
    fun `secrets are redacted before writing`() {
        val redacted = Slf4jRequestLogger.redact(
            """{"Authorization":"Bearer sk-1234","api_key":"secret-value","prompt":"hi"}""",
        )
        assertTrue("sk-1234" !in redacted, "Authorization value must be masked")
        assertTrue("secret-value" !in redacted, "api_key value must be masked")
        assertTrue("***" in redacted, "mask placeholder must be present")
        assertTrue("\"prompt\":\"hi\"" in redacted, "non-secret content must survive")
    }

    @Test
    fun `non-secret json is unchanged`() {
        val json = """{"prompt":"Hello","model":"deepseek-v4-pro"}"""
        assertEquals(json, Slf4jRequestLogger.redact(json))
    }
}

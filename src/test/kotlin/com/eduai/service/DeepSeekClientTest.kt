package com.eduai.service

import com.eduai.config.AppConfig
import com.eduai.config.MissingApiKeyException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val okResponseJson = """
{
  "id": "chatcmpl-123",
  "model": "deepseek-v4-pro",
  "choices": [
    {
      "finish_reason": "stop",
      "message": {
        "role": "assistant",
        "content": "Hello! How can I help you today?",
        "reasoning_content": "The user greeted me, I should greet back."
      }
    }
  ],
  "usage": { "prompt_tokens": 9, "completion_tokens": 12, "total_tokens": 21 }
}
""".trimIndent()

private fun testConfig(apiKey: String? = "test-key") = AppConfig(
    apiKey = apiKey,
    baseUrl = "https://api.deepseek.com",
    model = "deepseek-v4-pro",
)

private fun jsonClient(engine: MockEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        // Matches production: send explicit defaults (thinking, reasoning_effort, stream)
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
}

class DeepSeekClientTest {

    @Test
    fun `sends a well-formed First API Call request and parses the response`() = runBlocking {
        val captured = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            captured += request
            respond(okResponseJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = DeepSeekClient(jsonClient(engine), testConfig())

        val response = client.chat("Hello!")

        assertEquals(1, captured.size)
        val request = captured.single()
        // URL, auth and content type
        assertEquals("/chat/completions", request.url.encodedPath)
        assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
        // Content-Type is carried on the body, not in the headers map
        assertEquals(ContentType.Application.Json, (request.body as TextContent).contentType)
        // Request body — exactly the shape from the DeepSeek docs example
        val body = (request.body as TextContent).text
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("deepseek-v4-pro", json["model"]?.jsonPrimitive?.content)
        assertEquals(false, json["stream"]?.jsonPrimitive?.booleanOrNull)
        assertEquals("high", json["reasoning_effort"]?.jsonPrimitive?.content)
        assertEquals("enabled", json["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        val messages = json["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("Hello!", messages[1].jsonObject["content"]?.jsonPrimitive?.content)
        // Parsed response
        assertEquals("chatcmpl-123", response.id)
        assertEquals("deepseek-v4-pro", response.model)
        val content = response.choices.first().message?.content
        assertNotNull(content)
        assertTrue(content.startsWith("Hello!"))
        assertEquals("stop", response.choices.first().finishReason)
        assertEquals(21, response.usage?.totalTokens)
    }

    @Test
    fun `maps a 401 from DeepSeek to DeepSeekApiException`() = runBlocking {
        val engine = MockEngine {
            respond("""{"error": "invalid api key"}""", HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = DeepSeekClient(jsonClient(engine), testConfig())

        val exception = assertFailsWith<DeepSeekApiException> { client.chat("Hello!") }
        assertEquals(401, exception.statusCode)
        assertTrue(exception.message!!.contains("401"))
    }

    @Test
    fun `throws MissingApiKeyException when no API key is configured`() = runBlocking {
        val engine = MockEngine { respond(okResponseJson, HttpStatusCode.OK) }
        val client = DeepSeekClient(jsonClient(engine), testConfig(apiKey = null))

        assertFailsWith<MissingApiKeyException> { client.chat("Hello!") }
    }
}

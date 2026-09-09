package com.eduai.routes

import com.eduai.config.AppConfig
import com.eduai.service.DeepSeekClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

private val deepSeekOkJson = """
{
  "model": "deepseek-v4-pro",
  "choices": [
    {
      "finish_reason": "stop",
      "message": {
        "role": "assistant",
        "content": "Hi there! I am DeepSeek.",
        "reasoning_content": "A friendly reply."
      }
    }
  ],
  "usage": { "prompt_tokens": 9, "completion_tokens": 6, "total_tokens": 15 }
}
""".trimIndent()

/** Builds the app under test with a mocked DeepSeek backend. */
private fun ApplicationTestBuilder.installApp(apiKey: String?, engine: MockEngine) {
    val httpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            // Matches production: send explicit defaults (thinking, reasoning_effort, stream)
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }
    val config = AppConfig(apiKey = apiKey, baseUrl = "https://api.deepseek.com", model = "deepseek-v4-pro")
    application {
        chatRoutes(DeepSeekClient(httpClient, config), config)
    }
}

private suspend fun HttpClient.postChat(promptJson: String) = post("/api/chat") {
    contentType(ContentType.Application.Json)
    setBody(promptJson)
}

class ChatRoutesTest {

    @Test
    fun `health endpoint returns ok`() = testApplication {
        installApp(apiKey = "test-key", engine = MockEngine { respond("") })

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"status\": \"ok\"")
    }

    @Test
    fun `POST api chat returns the DeepSeek response`() = testApplication {
        installApp(
            apiKey = "test-key",
            engine = MockEngine {
                respond(deepSeekOkJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )

        val response = client.postChat("""{"prompt": "Hello!"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "\"response\": \"Hi there! I am DeepSeek.\"")
        assertContains(body, "\"reasoning\": \"A friendly reply.\"")
        assertContains(body, "\"model\": \"deepseek-v4-pro\"")
        assertContains(body, "\"finish_reason\": \"stop\"")
        assertContains(body, "\"total_tokens\": 15")
    }

    @Test
    fun `DeepSeek auth failure is mapped to 401`() = testApplication {
        installApp(
            apiKey = "test-key",
            engine = MockEngine {
                respond("""{"error": "invalid api key"}""", HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )

        val response = client.postChat("""{"prompt": "Hello!"}""")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertContains(response.bodyAsText(), "\"error\"")
    }

    @Test
    fun `missing API key returns 500 with a clear message`() = testApplication {
        installApp(apiKey = null, engine = MockEngine { respond(deepSeekOkJson, HttpStatusCode.OK) })

        val response = client.postChat("""{"prompt": "Hello!"}""")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertContains(response.bodyAsText(), "DEEPSEEK_API_KEY")
    }

    @Test
    fun `blank prompt returns 400`() = testApplication {
        installApp(apiKey = "test-key", engine = MockEngine { respond(deepSeekOkJson, HttpStatusCode.OK) })

        val response = client.postChat("""{"prompt": "   "}""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `malformed request body returns 400`() = testApplication {
        installApp(apiKey = "test-key", engine = MockEngine { respond(deepSeekOkJson, HttpStatusCode.OK) })

        val response = client.postChat("""not json at all""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

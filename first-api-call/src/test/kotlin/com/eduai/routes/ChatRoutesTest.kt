package com.eduai.routes

import com.eduai.config.MissingApiKeyException
import com.eduai.model.LocalChatResponse
import com.eduai.model.Usage
import com.eduai.service.ChatService
import com.eduai.service.DeepSeekApiException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

private val okResponse = LocalChatResponse(
    response = "Hi there! I am DeepSeek.",
    reasoning = "A friendly reply.",
    model = "deepseek-v4-pro",
    usage = Usage(promptTokens = 9, completionTokens = 6, totalTokens = 15),
    finishReason = "stop",
)

/** A [ChatService] fake: the DeepSeek backend is simulated, no key or network needed. */
private fun fakeChatService(behavior: suspend (String) -> LocalChatResponse): ChatService =
    object : ChatService {
        override suspend fun chat(prompt: String): LocalChatResponse = behavior(prompt)
    }

/** Fails the test if the chat backend is actually called. */
private val neverCalled: suspend (String) -> LocalChatResponse = { error("chat service must not be called") }

private fun ApplicationTestBuilder.installApp(service: ChatService) {
    application {
        chatRoutes(service)
    }
}

private suspend fun HttpClient.postChat(promptJson: String) = post("/api/chat") {
    contentType(ContentType.Application.Json)
    setBody(promptJson)
}

class ChatRoutesTest {

    @Test
    fun `health endpoint returns ok`() = testApplication {
        installApp(fakeChatService(neverCalled))

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"status\": \"ok\"")
    }

    @Test
    fun `POST api chat returns the DeepSeek response`() = testApplication {
        installApp(fakeChatService { okResponse })

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
            fakeChatService {
                throw DeepSeekApiException("DeepSeek API returned 401: {\"error\": \"invalid api key\"}", 401)
            },
        )

        val response = client.postChat("""{"prompt": "Hello!"}""")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertContains(response.bodyAsText(), "\"error\"")
    }

    @Test
    fun `missing API key returns 500 with a clear message`() = testApplication {
        installApp(
            fakeChatService {
                throw MissingApiKeyException("DEEPSEEK_API_KEY is not set. Get a key at https://platform.deepseek.com")
            },
        )

        val response = client.postChat("""{"prompt": "Hello!"}""")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertContains(response.bodyAsText(), "DEEPSEEK_API_KEY")
    }

    @Test
    fun `blank prompt returns 400`() = testApplication {
        installApp(fakeChatService(neverCalled))

        val response = client.postChat("""{"prompt": "   "}""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `malformed request body returns 400`() = testApplication {
        installApp(fakeChatService(neverCalled))

        val response = client.postChat("""not json at all""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

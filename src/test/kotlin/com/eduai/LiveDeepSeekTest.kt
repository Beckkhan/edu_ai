package com.eduai

import com.eduai.config.AppConfig
import com.eduai.service.DeepSeekClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Makes a REAL call to the DeepSeek API ("First API Call" from the docs).
 *
 * Skipped by default. Run it with a real key:
 *   DEEPSEEK_API_KEY=sk-... ./gradlew test --tests "com.eduai.LiveDeepSeekTest"
 */
class LiveDeepSeekTest {

    @Test
    fun `real first api call`(): Unit = runBlocking {
        val config = AppConfig()
        assumeTrue(
            !config.apiKey.isNullOrBlank(),
            "DEEPSEEK_API_KEY is not set (env or .env) — skipping the live test",
        )
        val httpClient = HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 180_000
                socketTimeoutMillis = 180_000
            }
        }
        try {
            val response = DeepSeekClient(httpClient, config).chat("Hello!")
            val content = response.choices.firstOrNull()?.message?.content
            assertNotNull(content, "expected assistant content in response: $response")
            assertTrue(content.isNotBlank(), "expected non-blank assistant content")
            println("DeepSeek reply: $content")
        } finally {
            httpClient.close()
        }
    }
}

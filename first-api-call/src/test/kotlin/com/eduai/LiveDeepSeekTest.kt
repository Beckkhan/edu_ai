package com.eduai

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.eduai.config.AppConfig
import com.eduai.service.KoogChatService
import com.eduai.service.resolveModel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Makes a REAL call to the DeepSeek API ("First API Call" from the docs) through Koog.
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
        val client = DeepSeekLLMClient(
            apiKey = config.requireApiKey(),
            settings = DeepSeekClientSettings(
                baseUrl = config.baseUrl,
                timeoutConfig = ConnectionTimeoutConfig(
                    connectTimeoutMillis = 15_000,
                    requestTimeoutMillis = 180_000,
                    socketTimeoutMillis = 180_000,
                ),
            ),
        )
        val executor = MultiLLMPromptExecutor(listOf(client))
        try {
            val response = KoogChatService(executor, config, resolveModel(config.model)).chat("Hello!")
            assertTrue(response.response.isNotBlank(), "expected non-blank assistant content")
            println("DeepSeek reply: ${response.response}")
            response.reasoning?.let { println("DeepSeek reasoning: ${it.take(200)}…") }
        } finally {
            executor.close()
        }
    }
}

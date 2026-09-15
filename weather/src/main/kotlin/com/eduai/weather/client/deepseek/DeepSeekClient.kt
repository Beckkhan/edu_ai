// src/main/kotlin/com/eduai/weather/client/deepseek/DeepSeekClient.kt
package com.eduai.weather.client.deepseek

import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import com.eduai.weather.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import org.slf4j.LoggerFactory

class DeepSeekApiException(val statusCode: Int, message: String) : Exception(message)

/** Resolves a configured model id (e.g. `DEEPSEEK_MODEL`) to a Koog [LLModel]. */
private fun resolveModel(id: String): LLModel =
    DeepSeekModels.models.firstOrNull { it.id == id }
        ?: LLModel(provider = LLMProvider.DeepSeek, id = id)

/**
 * Stateless DeepSeek API client backed by the Koog framework: holds no conversation state,
 * every call receives the full message list and is executed as a Koog [ai.koog.prompt.Prompt]
 * through a shared [PromptExecutor].
 */
class DeepSeekClient(private val config: AppConfig) {

    /** Ktor client shared with Koog; logs every DeepSeek request/response (API key redacted). */
    private val http: HttpClient = HttpClient(CIO) {
        install(Logging) {
            level = LogLevel.ALL
            sanitizeHeader { it == HttpHeaders.Authorization }
            logger = object : Logger {
                override fun log(message: String) {
                    DeepSeekClient.log.info(message)
                }
            }
        }
    }

    private val llmClient = DeepSeekLLMClient(
        apiKey = config.apiKey,
        settings = DeepSeekClientSettings(
            baseUrl = config.apiBaseUrl,
            timeoutConfig = ConnectionTimeoutConfig(
                connectTimeoutMillis = 15_000,
                requestTimeoutMillis = 120_000,
                socketTimeoutMillis = 120_000,
            ),
        ),
        httpClientFactory = KtorKoogHttpClient.Factory(baseClient = http, withSse = false),
    )

    private val executor: PromptExecutor = MultiLLMPromptExecutor(llmClient)
    private val model: LLModel = resolveModel(config.model)

    suspend fun chat(messages: List<ChatMessage>): String {
        val koogPrompt = prompt("chat") {
            messages.forEach { message ->
                when (message.role) {
                    "system" -> system(message.content)
                    "user" -> user(message.content)
                    "assistant" -> assistant(message.content)
                    else -> throw IllegalArgumentException("Unsupported message role: ${message.role}")
                }
            }
        }
        val assistant = try {
            executor.execute(koogPrompt, model)
        } catch (e: KoogHttpClientException) {
            throw DeepSeekApiException(
                e.statusCode ?: 502,
                "DeepSeek API returned ${e.statusCode ?: 502}: ${e.errorBody ?: e.message ?: "unknown error"}",
            )
        } catch (e: LLMClientException) {
            throw DeepSeekApiException(502, "DeepSeek API error: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw DeepSeekApiException(502, "DeepSeek API returned an invalid response: ${e.message}")
        }
        val content = assistant.textContent()
        if (content.isBlank()) {
            throw DeepSeekApiException(200, "DeepSeek returned an empty message")
        }
        return content
    }

    fun close() = executor.close()

    companion object {
        private val log = LoggerFactory.getLogger("com.eduai.weather.client.deepseek.DeepSeekClient")
    }
}

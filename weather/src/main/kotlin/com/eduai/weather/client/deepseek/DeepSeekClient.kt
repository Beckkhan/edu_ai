// src/main/kotlin/com/eduai/weather/client/deepseek/DeepSeekClient.kt
package com.eduai.weather.client.deepseek

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import com.eduai.weather.config.AppConfig
import com.eduai.weather.logging.RequestLogger
import com.eduai.weather.logging.Slf4jRequestLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.json.Json

class DeepSeekApiException(val statusCode: Int, message: String) : Exception(message)

/** Resolves a configured model id (e.g. `DEEPSEEK_MODEL`) to a Koog [LLModel]. */
private fun resolveModel(id: String): LLModel =
    DeepSeekModels.models.firstOrNull { it.id == id }
        ?: LLModel(provider = LLMProvider.DeepSeek, id = id)

/**
 * Stateless Koog DeepSeek client (DeepSeekLLMClient + PromptExecutor). Requests and responses
 * on the DeepSeek HTTP channel are logged via [RequestLogger] (R2 points 2/3); the moment a
 * response body arrives is recorded for the save_weather received_at contract (D5).
 */
class DeepSeekClient(
    private val config: AppConfig,
    private val requestLogger: RequestLogger = Slf4jRequestLogger(),
) {

    /** Epoch millis when the last DeepSeek response body was received (D5/T9). */
    @Volatile
    var lastResponseReceivedAt: Long = 0
        private set

    private val http: HttpClient = HttpClient(CIO) { }

    val llmClient = DeepSeekLLMClient(
        apiKey = config.apiKey,
        settings = DeepSeekClientSettings(
            baseUrl = config.apiBaseUrl,
            timeoutConfig = ConnectionTimeoutConfig(
                connectTimeoutMillis = 15_000,
                requestTimeoutMillis = 120_000,
                socketTimeoutMillis = 120_000,
            ),
        ),
        httpClientFactory = DeepSeekLoggingHttpClientFactory(
            delegate = KtorKoogHttpClient.Factory(baseClient = http, withSse = false),
            requestLogger = requestLogger,
            onResponseReceived = { lastResponseReceivedAt = System.currentTimeMillis() },
        ),
    )

    val executor: PromptExecutor = MultiLLMPromptExecutor(llmClient)
    val model: LLModel = resolveModel(config.model)

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
}

/**
 * Wraps Koog's Ktor factory and attaches the R2 DeepSeek log points (2 and 3) to the client
 * it creates: the outgoing request body is logged at the request-pipeline Transform phase,
 * the incoming response body at the response-pipeline Transform phase, then passed on unchanged.
 */
class DeepSeekLoggingHttpClientFactory(
    private val delegate: KtorKoogHttpClient.Factory,
    private val requestLogger: RequestLogger,
    private val onResponseReceived: () -> Unit = {},
) : KoogHttpClient.Factory {

    override fun create(
        clientName: String,
        baseUrl: String,
        headers: Map<String, String>,
        queryParameters: Map<String, String>,
        requestTimeoutMillis: Long,
        connectTimeoutMillis: Long,
        socketTimeoutMillis: Long,
        json: Json,
    ): KoogHttpClient {
        val client = delegate.create(
            clientName, baseUrl, headers, queryParameters,
            requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis, json,
        )
        if (client is KtorKoogHttpClient) {
            attachLogging(client.ktorClient)
        }
        return client
    }

    private fun attachLogging(httpClient: HttpClient) {
        httpClient.requestPipeline.intercept(HttpRequestPipeline.Transform) { body ->
            when (body) {
                is String -> requestLogger.deepSeekRequest(body)
                is OutgoingContent.ByteArrayContent -> requestLogger.deepSeekRequest(body.bytes().decodeToString())
                is TextContent -> requestLogger.deepSeekRequest(body.text)
            }
            proceedWith(body)
        }
        // The raw response body is available at the Receive phase; log it there and hand the
        // bytes on in a fresh channel so downstream phases (DataConversion, bodyAsText) work.
        httpClient.responsePipeline.intercept(HttpResponsePipeline.Receive) { container ->
            when (val body = container.response) {
                is ByteReadChannel -> {
                    val bytes = body.toByteArray()
                    onResponseReceived()
                    requestLogger.deepSeekResponse(bytes.decodeToString())
                    proceedWith(container.copy(response = ByteReadChannel(bytes)))
                }
                is ByteArray -> {
                    onResponseReceived()
                    requestLogger.deepSeekResponse(body.decodeToString())
                    proceedWith(container)
                }
                else -> proceedWith(container)
            }
        }
    }
}

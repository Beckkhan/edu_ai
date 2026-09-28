package com.eduai.turbofa.client.deepseek

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * An id Koog does not know still runs: it keeps the configured id but takes the
 * capabilities of the known DeepSeek model — a bare LLModel has none, and Koog rejects
 * every request of such a model.
 */
internal fun resolveModel(id: String): LLModel =
    DeepSeekModels.models.firstOrNull { it.id == id }
        ?: DeepSeekModels.DeepSeekV4Pro.copy(id = id)

class DeepSeekClient(
    apiKey: String,
    modelId: String,
    httpClientFactory: KoogHttpClient.Factory = defaultHttpClientFactory(),
) {

    private val llmClient = DeepSeekLLMClient(
        apiKey = apiKey,
        settings = DeepSeekClientSettings(
            timeoutConfig = ConnectionTimeoutConfig(
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS,
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS,
                socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS,
            ),
        ),
        httpClientFactory = httpClientFactory,
    )

    val executor: PromptExecutor = MultiLLMPromptExecutor(llmClient)

    val model: LLModel = resolveModel(modelId)

    fun close() = executor.close()

    companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000L

        /** The tool queries three databases, and DeepSeek summaries of the result take a while. */
        const val REQUEST_TIMEOUT_MILLIS = 120_000L
    }
}

internal fun defaultHttpClientFactory(): KtorKoogHttpClient.Factory =
    KtorKoogHttpClient.Factory(baseClient = HttpClient(CIO), withSse = false)

// src/main/kotlin/com/eduai/turbofa/client/deepseek/DeepSeekClient.kt
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
 * Resolves a configured model id (DEEPSEEK_MODEL) to a Koog [LLModel].
 *
 * An id Koog does not know (e.g. AppConfig's `deepseek-chat` default) still runs: it keeps the
 * configured id but takes the capabilities of the known DeepSeek model — a bare
 * `LLModel(provider, id)` would carry no capabilities, and Koog rejects every request of such a
 * model with "does not support completion".
 */
private fun resolveModel(id: String): LLModel =
    DeepSeekModels.models.firstOrNull { it.id == id }
        ?: DeepSeekModels.DeepSeekV4Pro.copy(id = id)

/**
 * The Koog DeepSeek stack of spec 3.3: a [DeepSeekLLMClient] (R6 — DeepSeek is only ever reached
 * through Koog) wrapped in the [PromptExecutor] the agent runs on.
 *
 * The api key and the model id (AppConfig) are injected by the constructor — no environment access
 * here. The HTTP client factory is injectable as well so the R2 logging factory (T6) can be wired in
 * without touching this class; this class deliberately does not log requests or responses itself.
 *
 * @param apiKey DEEPSEEK_API_KEY from AppConfig; never logged
 * @param modelId DEEPSEEK_MODEL from AppConfig, e.g. "deepseek-chat"
 * @param httpClientFactory Koog HTTP client factory; defaults to a CIO client without SSE
 */
class DeepSeekClient(
    apiKey: String,
    modelId: String,
    httpClientFactory: KoogHttpClient.Factory = defaultHttpClientFactory(),
) {

    /** Koog DeepSeek client (R6); lives for the whole process, see [close]. */
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

    /** Executor the agent runs on (spec 3.3). */
    val executor: PromptExecutor = MultiLLMPromptExecutor(llmClient)

    /** Model resolved from [modelId]; the agent is built with it. */
    val model: LLModel = resolveModel(modelId)

    /** Releases the resources held by the Koog client. */
    fun close() = executor.close()

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000L

        /** Tool runs query three databases; DeepSeek summaries can take a while. */
        const val REQUEST_TIMEOUT_MILLIS = 120_000L
    }
}

/** Default transport: CIO without SSE — DeepSeek is called with regular (non-streaming) requests. */
private fun defaultHttpClientFactory(): KtorKoogHttpClient.Factory =
    KtorKoogHttpClient.Factory(baseClient = HttpClient(CIO), withSse = false)

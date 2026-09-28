package com.eduai.turbofa.client.ollama

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.eduai.turbofa.client.deepseek.DeepSeekClient
import com.eduai.turbofa.client.deepseek.defaultHttpClientFactory
import com.eduai.turbofa.client.deepseek.resolveModel

/**
 * Local Ollama through its OpenAI-compatible endpoint (/v1): the same Koog
 * DeepSeek client, pointed at OLLAMA_BASE_URL. Ollama ignores the api key.
 */
class OllamaClient(
    baseUrl: String,
    modelId: String,
    httpClientFactory: KoogHttpClient.Factory = defaultHttpClientFactory(),
) {
    private val llmClient =
        DeepSeekLLMClient(
            apiKey = "ollama",
            settings =
                DeepSeekClientSettings(
                    baseUrl = baseUrl.trimEnd('/'),
                    chatCompletionsPath = "/v1/chat/completions",
                    modelsPath = "/v1/models",
                    timeoutConfig =
                        ConnectionTimeoutConfig(
                            connectTimeoutMillis = DeepSeekClient.CONNECT_TIMEOUT_MILLIS,
                            requestTimeoutMillis = DeepSeekClient.REQUEST_TIMEOUT_MILLIS,
                            socketTimeoutMillis = DeepSeekClient.REQUEST_TIMEOUT_MILLIS,
                        ),
                ),
            httpClientFactory = httpClientFactory,
        )

    val executor: PromptExecutor = MultiLLMPromptExecutor(llmClient)

    val model: LLModel = resolveModel(modelId)

    fun close() = executor.close()
}

package com.eduai.service

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekParams
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.additionalPropertiesOf
import com.eduai.config.AppConfig
import com.eduai.model.LocalChatResponse
import com.eduai.model.Usage

/** Thrown when the DeepSeek API answers with a non-2xx status. */
class DeepSeekApiException(message: String, val statusCode: Int) : Exception(message)

/** What the local HTTP API needs from the chat backend. */
interface ChatService {
    suspend fun chat(prompt: String): LocalChatResponse
}

/** Resolves a configured model id (e.g. `DEEPSEEK_MODEL`) to a Koog [LLModel]. */
internal fun resolveModel(id: String): LLModel =
    DeepSeekModels.models.firstOrNull { it.id == id }
        ?: LLModel(provider = LLMProvider.DeepSeek, id = id)

/** DeepSeek params matching the previous raw call: thinking enabled, reasoning_effort high. */
internal fun chatParams(): DeepSeekParams = DeepSeekParams(
    additionalProperties = additionalPropertiesOf(
        "thinking" to mapOf("type" to "enabled"),
        "reasoning_effort" to "high",
    ),
)

/** Builds the Koog prompt sent to DeepSeek for every chat request. */
internal fun buildChatPrompt(systemPrompt: String, userPrompt: String, params: DeepSeekParams): Prompt =
    prompt("chat", params = params) {
        system(systemPrompt)
        user(userPrompt)
    }

/** Maps a Koog assistant message onto the local API response shape. */
internal fun Message.Assistant.toLocalChatResponse(modelId: String): LocalChatResponse {
    val content = textContent()
    if (content.isBlank()) {
        throw DeepSeekApiException("DeepSeek API returned an empty message", 502)
    }
    return LocalChatResponse(
        response = content,
        reasoning = parts
            .filterIsInstance<MessagePart.Reasoning>()
            .flatMap { it.content }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n"),
        model = modelId,
        usage = metaInfo.toUsage(),
        finishReason = finishReason,
    )
}

private fun ResponseMetaInfo.toUsage(): Usage? =
    totalTokensCount?.let { total ->
        Usage(
            promptTokens = inputTokensCount ?: 0,
            completionTokens = outputTokensCount ?: 0,
            totalTokens = total,
        )
    }

/** Translates Koog-level failures into [DeepSeekApiException] (mapped by the routes). */
internal fun translateError(e: Exception): DeepSeekApiException = when (e) {
    is KoogHttpClientException -> DeepSeekApiException(
        "DeepSeek API returned ${e.statusCode ?: 502}: ${e.errorBody ?: e.message ?: "unknown error"}",
        e.statusCode ?: 502,
    )
    is LLMClientException -> DeepSeekApiException("DeepSeek API error: ${e.message}", 502)
    is IllegalArgumentException -> DeepSeekApiException(
        "DeepSeek API returned an invalid response: ${e.message}", 502,
    )
    else -> throw e
}

/**
 * Chat backend backed by the Koog framework: a [Prompt] is executed through a shared
 * [PromptExecutor] wrapping a DeepSeek LLM client.
 */
class KoogChatService(
    private val executor: PromptExecutor,
    private val config: AppConfig,
    private val model: LLModel,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) : ChatService {

    /** Sends the "First API Call"-style chat completion request and returns the parsed response. */
    override suspend fun chat(prompt: String): LocalChatResponse {
        config.requireApiKey()
        val koogPrompt = buildChatPrompt(systemPrompt, prompt, chatParams())
        val assistant = try {
            executor.execute(koogPrompt, model)
        } catch (e: KoogHttpClientException) {
            throw translateError(e)
        } catch (e: LLMClientException) {
            throw translateError(e)
        } catch (e: IllegalArgumentException) {
            throw translateError(e)
        }
        return assistant.toLocalChatResponse(model.id)
    }

    private companion object {
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant."
    }
}

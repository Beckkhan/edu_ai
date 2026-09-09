package com.eduai.service

import com.eduai.config.AppConfig
import com.eduai.model.ChatMessage
import com.eduai.model.DeepSeekChatRequest
import com.eduai.model.DeepSeekChatResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Thrown when the DeepSeek API answers with a non-2xx status. */
class DeepSeekApiException(message: String, val statusCode: Int) : Exception(message)

/**
 * Thin wrapper around the DeepSeek chat completions endpoint
 * (OpenAI-compatible format, see https://api-docs.deepseek.com/).
 */
class DeepSeekClient(
    private val httpClient: HttpClient,
    private val config: AppConfig,
) {
    /** Sends the "First API Call"-style chat completion request and returns the parsed response. */
    suspend fun chat(prompt: String, systemPrompt: String = "You are a helpful assistant."): DeepSeekChatResponse {
        val request = DeepSeekChatRequest(
            model = config.model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = prompt),
            ),
        )
        val response = httpClient.post("${config.baseUrl.trimEnd('/')}/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${config.requireApiKey()}")
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw DeepSeekApiException(
                "DeepSeek API returned ${response.status.value}: ${response.bodyAsText()}",
                response.status.value,
            )
        }
        return response.body()
    }
}

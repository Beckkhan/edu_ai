// src/main/kotlin/com/eduai/weather/client/deepseek/DeepSeekClient.kt
package com.eduai.weather.client.deepseek

import com.eduai.weather.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class DeepSeekApiException(val statusCode: Int, message: String) : Exception(message)

/** Stateless DeepSeek API client: holds no conversation state, every call receives the full message list. */
class DeepSeekClient(private val config: AppConfig) {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 120_000
        }
    }

    suspend fun chat(messages: List<ChatMessage>): String {
        val response: HttpResponse = http.post("${config.apiBaseUrl}/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(ChatCompletionRequest(model = config.model, messages = messages))
        }
        if (response.status != HttpStatusCode.OK) {
            throw DeepSeekApiException(response.status.value, response.bodyAsText())
        }
        val completion: ChatCompletion = response.body()
        return completion.choices.firstOrNull()
            ?.message?.content
            ?.ifBlank { null }
            ?: throw DeepSeekApiException(200, "DeepSeek returned an empty message")
    }

    fun close() = http.close()
}

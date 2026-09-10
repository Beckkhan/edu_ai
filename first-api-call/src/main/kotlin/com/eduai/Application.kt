package com.eduai

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.eduai.config.AppConfig
import com.eduai.routes.chatRoutes
import com.eduai.service.KoogChatService
import com.eduai.service.resolveModel
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = AppConfig()
    val deepSeekClient = DeepSeekLLMClient(
        // The key is validated per request via config.requireApiKey() so a missing
        // key still yields the helpful 500 message instead of a startup crash.
        apiKey = config.apiKey.orEmpty(),
        settings = DeepSeekClientSettings(
            baseUrl = config.baseUrl,
            timeoutConfig = ConnectionTimeoutConfig(
                connectTimeoutMillis = 15_000,
                requestTimeoutMillis = 120_000,
                socketTimeoutMillis = 120_000,
            ),
        ),
    )
    val executor = MultiLLMPromptExecutor(listOf(deepSeekClient))
    val chatService = KoogChatService(executor, config, resolveModel(config.model))
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        chatRoutes(chatService)
    }.start(wait = true)
}

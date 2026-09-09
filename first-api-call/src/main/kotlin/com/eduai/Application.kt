package com.eduai

import com.eduai.config.AppConfig
import com.eduai.routes.chatRoutes
import com.eduai.service.DeepSeekClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.serialization.json.Json

fun main() {
    val config = AppConfig()
    val httpClient = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            // encodeDefaults: the docs' example always sends thinking/reasoning_effort/stream explicitly
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        chatRoutes(DeepSeekClient(httpClient, config), config)
    }.start(wait = true)
}

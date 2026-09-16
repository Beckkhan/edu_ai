// src/main/kotlin/com/eduai/weather/Application.kt
package com.eduai.weather

import com.eduai.weather.client.deepseek.DeepSeekClient
import com.eduai.weather.client.deepseek.KoogWeatherAgent
import com.eduai.weather.config.AppConfig
import com.eduai.weather.db.DataSourceFactory
import com.eduai.weather.db.WeatherLogRepository
import com.eduai.weather.history.ChatHistoryStore
import com.eduai.weather.logging.Slf4jRequestLogger
import com.eduai.weather.routes.chatRoutes
import com.eduai.weather.service.ChatService
import com.eduai.weather.weather.WeatherService
import java.sql.Timestamp
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.eduai.weather.Application")

fun Application.module() {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

    val config = AppConfig()
    val dataSource = DataSourceFactory(config).create()
    val history = ChatHistoryStore.fromConfig(config.historyFilePath)
    history.clearAll() // fresh history on every restart
    log.info("Chat history reset on startup")

    val requestLogger = Slf4jRequestLogger()
    val deepSeekClient = DeepSeekClient(config, requestLogger)

    val chatService = ChatService(
        agent = KoogWeatherAgent(
            executor = deepSeekClient.executor,
            model = deepSeekClient.model,
            repo = WeatherLogRepository(dataSource),
            requestLogger = requestLogger,
            // D5/T9: received_at = moment the DeepSeek response with the weather data arrived
            receiptTimeProvider = {
                Timestamp(
                    deepSeekClient.lastResponseReceivedAt
                        .takeIf { it > 0 }
                        ?: System.currentTimeMillis()
                )
            },
        ),
        history = history,
        weatherService = WeatherService(),
    )

    routing {
        chatRoutes(chatService, requestLogger)
    }
}

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") { module() }.start(wait = true)
}

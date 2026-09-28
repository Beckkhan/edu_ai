package com.eduai.turbofa

import com.eduai.turbofa.client.deepseek.DeepSeekClient
import com.eduai.turbofa.config.AppConfig
import com.eduai.turbofa.db.DataSourceFactory
import com.eduai.turbofa.di.clientModule
import com.eduai.turbofa.di.configModule
import com.eduai.turbofa.di.dbModule
import com.eduai.turbofa.di.loggingModule
import com.eduai.turbofa.di.serviceModule
import com.eduai.turbofa.history.ChatHistoryStore
import com.eduai.turbofa.history.HISTORY_FILE
import com.eduai.turbofa.logging.RequestLogger
import com.eduai.turbofa.routes.ChatJson
import com.eduai.turbofa.routes.chatRoutes
import com.eduai.turbofa.service.ChatService
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import org.slf4j.LoggerFactory

private const val HOST = "0.0.0.0"
private const val PORT = 8080

/**
 * D4/E4: logback 1.6.3 hard-forces append=true, so the file is truncated here, ABOVE
 * [log] — later truncation would hand TimeBasedRollingPolicy a stale lastModified.
 */
@Suppress("unused")
private val FRESH_R2_LOG_FILE: Unit = run {
    val logFile = Path.of("logs", "turbofa.log")
    logFile.parent?.let(Files::createDirectories)
    Files.writeString(logFile, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
}

private val log = LoggerFactory.getLogger("com.eduai.turbofa.Application")

fun main() {
    log.info("turbofa starting on http://$HOST:$PORT")
    embeddedServer(Netty, port = PORT, host = HOST, module = Application::module).start(wait = true)
}

fun Application.module() {
    // The Bruno-facing JSON policy T8 exports; the same instance the route encodes with, so the
    // body Bruno receives is exactly the body logged as R8 log point 5.
    install(ContentNegotiation) { json(ChatJson) }

    val di = DI {
        import(configModule)
        import(dbModule)
        import(clientModule)
        import(loggingModule)
        import(serviceModule)
    }

    // A missing credential must abort before any startup file I/O.
    di.direct.instance<AppConfig>()

    // History (R4, 5e): cleared on restart before the first request.
    val history: ChatHistoryStore by di.instance()
    history.clear()
    log.info("Chat history cleared on startup ($HISTORY_FILE)")

    val chatService: ChatService by di.instance()
    val requestLogger: RequestLogger by di.instance()
    routing { chatRoutes(chatService, requestLogger) }

    // Releases the Koog executor and the three pools; without this the JVM exit of ./gradlew run
    // would leave Hikari's housekeeping threads to the shutdown hook.
    val deepSeekClient: DeepSeekClient by di.instance()
    val dataSources: DataSourceFactory by di.instance()
    monitor.subscribe(ApplicationStopped) {
        deepSeekClient.close()
        dataSources.close()
    }

    log.info("turbofa ready: POST http://$HOST:$PORT/chat")
}

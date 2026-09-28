package com.eduai.turbofa

import com.eduai.turbofa.client.deepseek.DeepSeekClient
import com.eduai.turbofa.db.DataSourceFactory
import com.eduai.turbofa.di.SharedDI
import com.eduai.turbofa.di.clientModule
import com.eduai.turbofa.di.configModule
import com.eduai.turbofa.di.dbModule
import com.eduai.turbofa.di.loggingModule
import com.eduai.turbofa.di.serviceModule
import com.eduai.turbofa.history.ChatHistoryStore
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
import org.kodein.di.instance
import org.slf4j.LoggerFactory

private const val HOST = "0.0.0.0"
private const val PORT = 8080

/**
 * R4/5e history text file. Deliberately NOT logs/turbofa.log: that file belongs exclusively to the
 * R2 logback appender (D4) and is made fresh by [FRESH_R2_LOG_FILE], below.
 */
internal val HISTORY_FILE: Path = Path.of("logs", "chat-history.txt")

/**
 * D4 (corrected 2026-09-23, E4): logback 1.6.3 hard-forces append=true on its RollingFileAppender —
 * `<append>false</append>` is silently inert — so a fresh per-run log file cannot come from
 * logback.xml and is made fresh here instead, mirroring the R4 history-file clear (5a).
 *
 * Declared ABOVE [log] on purpose: file-level properties initialize in declaration order, so this
 * truncation runs before the first LoggerFactory call, i.e. before logback opens the file.
 * Truncating later (in main() or module()) would hand TimeBasedRollingPolicy the stale file's
 * lastModified as its initial rolling period, rolling the emptied file over the previous day's
 * archive name on the first R2 event.
 */
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

/**
 * Pure assembly (R10): no SQL, no LLM call and no R2 log line is emitted here. Failures
 * during construction (a missing credential, an unreachable DB, an empty tool descriptor
 * set) abort the startup instead of surfacing on the first Bruno request.
 */
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
    SharedDI.init(di)

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

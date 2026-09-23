// src/main/kotlin/com/eduai/turbofa/Application.kt
package com.eduai.turbofa

import ai.koog.http.client.ktor.KtorKoogHttpClient
import com.eduai.turbofa.client.deepseek.DeepSeekClient
import com.eduai.turbofa.client.deepseek.DeepSeekLoggingHttpClientFactory
import com.eduai.turbofa.client.deepseek.KoogTurbofaAgent
import com.eduai.turbofa.config.AppConfig
import com.eduai.turbofa.db.DataSourceFactory
import com.eduai.turbofa.db.FuelingDataSource
import com.eduai.turbofa.history.ChatHistoryStore
import com.eduai.turbofa.history.InMemoryHistoryCache
import com.eduai.turbofa.history.TextFileHistoryWriter
import com.eduai.turbofa.logging.RequestLogger
import com.eduai.turbofa.logging.Slf4jRequestLogger
import com.eduai.turbofa.routes.ChatJson
import com.eduai.turbofa.routes.chatRoutes
import com.eduai.turbofa.service.ChatService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
import org.slf4j.LoggerFactory

/** Server binding of the Bruno API (R9); the port Bruno's requests use. */
private const val HOST = "0.0.0.0"
private const val PORT = 8080

/**
 * R4/5e history text file. Deliberately NOT logs/turbofa.log: that file belongs exclusively to the
 * R2 logback appender (D4) and is made fresh by [FRESH_R2_LOG_FILE], below.
 */
private val HISTORY_FILE: Path = Path.of("logs", "chat-history.txt")

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
 * T9 wiring of spec 3.3, in dependency order: config ← db/client/history/tool ← service ← routes.
 *
 * Pure assembly (R10): no SQL, no LLM call and no R2 log line is emitted here — the requests flow
 * through the wired objects. Failures during construction (a missing credential, an unreachable DB,
 * an empty tool descriptor set) abort the startup instead of surfacing on the first Bruno request.
 *
 * The history file is truncated at startup (R4): every run starts from an empty dialogue. The R2 log
 * file is truncated even earlier, before this module runs — see [FRESH_R2_LOG_FILE] (D4).
 */
fun Application.module() {
    // The Bruno-facing JSON policy T8 exports; the same instance the route encodes with, so the
    // body Bruno receives is exactly the body logged as R8 log point 5.
    install(ContentNegotiation) { json(ChatJson) }

    val config = AppConfig()
    val requestLogger: RequestLogger = Slf4jRequestLogger()

    // History (R4, 5e): in-memory cache + text file, cleared on restart before the first request.
    val history = ChatHistoryStore(InMemoryHistoryCache(), TextFileHistoryWriter(HISTORY_FILE))
    history.clear()
    log.info("Chat history cleared on startup ($HISTORY_FILE)")

    // Read-only pools of the three domain databases (D7) and the five SELECTs of 5f.
    val dataSources = DataSourceFactory(
        fuelingJdbcUrl = config.fuelingJdbcUrl,
        paymentJdbcUrl = config.paymentJdbcUrl,
        vendorsJdbcUrl = config.vendorsJdbcUrl,
        user = config.dbUser,
        password = config.dbPassword,
    )
    val fuelingDataSource = FuelingDataSource(
        fuelingDatabase = dataSources.fuelingDatabase,
        paymentDatabase = dataSources.paymentDatabase,
        vendorsDatabase = dataSources.vendorsDatabase,
    )

    // R6: DeepSeek is reached only through Koog. The logging factory (T6) wraps the regular CIO
    // transport so log points 2 (Request to Deepseek) and 3 (Response from Deepseek) fire.
    val deepSeekClient = DeepSeekClient(
        apiKey = config.deepSeekApiKey,
        modelId = config.deepSeekModel,
        httpClientFactory = DeepSeekLoggingHttpClientFactory(
            delegate = KtorKoogHttpClient.Factory(baseClient = HttpClient(CIO), withSse = false),
            requestLogger = requestLogger,
        ),
    )

    val chatService = ChatService(
        agent = KoogTurbofaAgent(
            executor = deepSeekClient.executor,
            model = deepSeekClient.model,
            dataSource = fuelingDataSource,
            requestLogger = requestLogger,
        ),
        history = history,
    )

    routing { chatRoutes(chatService, requestLogger) }

    // Releases the Koog executor and the three pools; without this the JVM exit of ./gradlew run
    // would leave Hikari's housekeeping threads to the shutdown hook.
    monitor.subscribe(ApplicationStopped) {
        deepSeekClient.close()
        dataSources.close()
    }

    log.info("turbofa ready: POST http://$HOST:$PORT/chat")
}

package com.eduai.turbofa.di

import ai.koog.http.client.ktor.KtorKoogHttpClient
import com.eduai.turbofa.HISTORY_FILE
import com.eduai.turbofa.client.deepseek.DeepSeekClient
import com.eduai.turbofa.client.deepseek.DeepSeekLoggingHttpClientFactory
import com.eduai.turbofa.client.deepseek.KoogTurbofaAgent
import com.eduai.turbofa.client.deepseek.TurbofaAgent
import com.eduai.turbofa.config.AppConfig
import com.eduai.turbofa.db.DataSourceFactory
import com.eduai.turbofa.db.FuelingDataSource
import com.eduai.turbofa.history.ChatHistoryStore
import com.eduai.turbofa.history.InMemoryHistoryCache
import com.eduai.turbofa.history.TextFileHistoryWriter
import com.eduai.turbofa.logging.RequestLogger
import com.eduai.turbofa.logging.Slf4jRequestLogger
import com.eduai.turbofa.service.ChatService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val configModule = DI.Module("config") {
    bind<AppConfig>() with singleton { AppConfig() }
}

val dbModule = DI.Module("db") {
    bind<DataSourceFactory>() with singleton {
        val config = instance<AppConfig>()
        DataSourceFactory(
            fuelingJdbcUrl = config.fuelingJdbcUrl,
            paymentJdbcUrl = config.paymentJdbcUrl,
            vendorsJdbcUrl = config.vendorsJdbcUrl,
            user = config.dbUser,
            password = config.dbPassword,
        )
    }
    bind<FuelingDataSource>() with singleton {
        val pools = instance<DataSourceFactory>()
        FuelingDataSource(
            fuelingDatabase = pools.fuelingDatabase,
            paymentDatabase = pools.paymentDatabase,
            vendorsDatabase = pools.vendorsDatabase,
        )
    }
}

val clientModule = DI.Module("client") {
    bind<DeepSeekClient>() with singleton {
        DeepSeekClient(
            apiKey = instance<AppConfig>().deepSeekApiKey,
            modelId = instance<AppConfig>().deepSeekModel,
            httpClientFactory = DeepSeekLoggingHttpClientFactory(
                delegate = KtorKoogHttpClient.Factory(baseClient = HttpClient(CIO), withSse = false),
                requestLogger = instance(),
            ),
        )
    }
    bind<TurbofaAgent>() with singleton {
        val client = instance<DeepSeekClient>()
        KoogTurbofaAgent(
            executor = client.executor,
            model = client.model,
            dataSource = instance(),
            requestLogger = instance(),
        )
    }
}

val loggingModule = DI.Module("logging") {
    bind<RequestLogger>() with singleton { Slf4jRequestLogger() }
}

val serviceModule = DI.Module("service") {
    bind<ChatHistoryStore>() with singleton {
        ChatHistoryStore(InMemoryHistoryCache(), TextFileHistoryWriter(HISTORY_FILE))
    }
    bind<ChatService>() with singleton {
        ChatService(
            agent = instance(),
            history = instance(),
        )
    }
}

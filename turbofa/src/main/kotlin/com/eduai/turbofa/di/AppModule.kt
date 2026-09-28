package com.eduai.turbofa.di

import com.eduai.turbofa.client.deepseek.DeepSeekClient
import com.eduai.turbofa.client.deepseek.DeepSeekLoggingHttpClientFactory
import com.eduai.turbofa.client.deepseek.KoogTurbofaAgent
import com.eduai.turbofa.client.deepseek.TurbofaAgent
import com.eduai.turbofa.client.deepseek.defaultHttpClientFactory
import com.eduai.turbofa.client.ollama.OllamaClient
import com.eduai.turbofa.client.ollama.OllamaTurbofaAgent
import com.eduai.turbofa.config.AppConfig
import com.eduai.turbofa.db.DataSourceFactory
import com.eduai.turbofa.db.FuelingDataSource
import com.eduai.turbofa.history.ChatHistoryStore
import com.eduai.turbofa.history.HISTORY_FILE
import com.eduai.turbofa.history.InMemoryHistoryCache
import com.eduai.turbofa.history.TextFileHistoryWriter
import com.eduai.turbofa.logging.RequestLogger
import com.eduai.turbofa.logging.Slf4jRequestLogger
import com.eduai.turbofa.service.ChatService
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

private const val DEEPSEEK_TAG = "deepseek"
private const val OLLAMA_TAG = "ollama"

val configModule =
    DI.Module("config") {
        bind<AppConfig>() with singleton { AppConfig() }
    }

val dbModule =
    DI.Module("db") {
        bind<DataSourceFactory>() with
            singleton {
                val config = instance<AppConfig>()
                DataSourceFactory(
                    fuelingJdbcUrl = config.fuelingJdbcUrl,
                    paymentJdbcUrl = config.paymentJdbcUrl,
                    vendorsJdbcUrl = config.vendorsJdbcUrl,
                    user = config.dbUser,
                    password = config.dbPassword,
                )
            }
        bind<FuelingDataSource>() with
            singleton {
                FuelingDataSource(instance())
            }
    }

val clientModule =
    DI.Module("client") {
        bind<DeepSeekClient>() with
            singleton {
                val config = instance<AppConfig>()
                DeepSeekClient(
                    apiKey = config.deepSeekApiKey,
                    modelId = config.deepSeekModel,
                    httpClientFactory =
                        DeepSeekLoggingHttpClientFactory(
                            delegate = defaultHttpClientFactory(),
                            requestLogger = instance(),
                        ),
                )
            }
        bind<OllamaClient>() with
            singleton {
                val config = instance<AppConfig>()
                OllamaClient(
                    baseUrl = config.ollamaBaseUrl,
                    modelId = config.ollamaModel,
                )
            }
        bind<TurbofaAgent>(tag = DEEPSEEK_TAG) with
            singleton {
                val client = instance<DeepSeekClient>()
                KoogTurbofaAgent(
                    executor = client.executor,
                    model = client.model,
                    dataSource = instance(),
                    requestLogger = instance(),
                )
            }
        bind<TurbofaAgent>(tag = OLLAMA_TAG) with
            singleton {
                val client = instance<OllamaClient>()
                OllamaTurbofaAgent(
                    executor = client.executor,
                    model = client.model,
                    dataSource = instance(),
                    requestLogger = instance(),
                )
            }
    }

val loggingModule =
    DI.Module("logging") {
        bind<RequestLogger>() with singleton { Slf4jRequestLogger() }
    }

val serviceModule =
    DI.Module("service") {
        bind<ChatHistoryStore>() with
            singleton {
                ChatHistoryStore(
                    InMemoryHistoryCache(),
                    TextFileHistoryWriter(HISTORY_FILE),
                )
            }
        bind<ChatService>() with
            singleton {
                ChatService(
                    deepseekAgent = instance(tag = DEEPSEEK_TAG),
                    ollamaAgent = instance(tag = OLLAMA_TAG),
                    history = instance(),
                )
            }
    }

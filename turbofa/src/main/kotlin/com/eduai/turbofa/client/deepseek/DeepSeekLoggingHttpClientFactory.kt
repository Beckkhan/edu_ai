// src/main/kotlin/com/eduai/turbofa/client/deepseek/DeepSeekLoggingHttpClientFactory.kt
package com.eduai.turbofa.client.deepseek

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import com.eduai.turbofa.logging.RequestLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.json.Json

/**
 * DeepSeek-side R2 logging (spec 3.2, R8): the factory seam [DeepSeekClient] exposes
 * (`httpClientFactory`), so every request the Koog client sends and every response it receives
 * carries the two log points of the DeepSeek channel.
 *
 * It wraps another [KoogHttpClient.Factory] (the caller's `KtorKoogHttpClient.Factory`, i.e. the
 * regular CIO transport) and decorates the Ktor client that factory creates:
 *
 * - **log point 2** `Request to Deepseek` — emitted in the **Transform** phase of the request
 *   pipeline, where the outgoing body (String / [TextContent] / [OutgoingContent.ByteArrayContent])
 *   is still visible; the body is passed on unchanged.
 * - **log point 3** `Response from Deepseek` — emitted in the **Receive** phase of the response
 *   pipeline (D3: Transform/Parse phases do not fire for Koog, so a hook there would be dead code);
 *   the raw body is read from its [ByteReadChannel], logged, and handed on in a fresh channel so
 *   the downstream DataConversion phases can still consume it.
 *
 * Bodies go to [RequestLogger] as the json Koog actually sent/received; masking of secrets is
 * RequestLogger's job (T5, D4) — this factory logs nothing itself and knows no api key.
 *
 * This class only decorates transport: no other log point belongs here (1/5 = ChatRoutes,
 * 4 = FuelingInfoTool).
 *
 * @param delegate transport the BaseUrl/headers/timeouts/json of Koog are delegated to
 * @param requestLogger R2 sink of log points 2 and 3 (T5)
 */
class DeepSeekLoggingHttpClientFactory(
    private val delegate: KoogHttpClient.Factory,
    private val requestLogger: RequestLogger,
) : KoogHttpClient.Factory {

    /**
     * Creates the delegate's client and attaches the two log points to it. The delegate must
     * produce a [KtorKoogHttpClient] (the only factory whose transport exposes the Ktor pipelines);
     * anything else fails fast instead of losing log points 2/3 silently (R8).
     */
    override fun create(
        clientName: String,
        baseUrl: String,
        headers: Map<String, String>,
        queryParameters: Map<String, String>,
        requestTimeoutMillis: Long,
        connectTimeoutMillis: Long,
        socketTimeoutMillis: Long,
        json: Json,
    ): KoogHttpClient {
        val client = delegate.create(
            clientName, baseUrl, headers, queryParameters,
            requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis, json,
        )
        val ktorClient = client as? KtorKoogHttpClient ?: error(
            "R2 logging requires a KtorKoogHttpClient, but ${delegate::class.simpleName} produced " +
                "${client::class.simpleName}; wire a KtorKoogHttpClient.Factory as delegate (T6)",
        )
        attachLogging(ktorClient.ktorClient)
        return ktorClient
    }

    private fun attachLogging(httpClient: HttpClient) {
        httpClient.requestPipeline.intercept(HttpRequestPipeline.Transform) { body ->
            when (body) {
                is String -> requestLogger.deepSeekRequest(body)
                is OutgoingContent.ByteArrayContent -> requestLogger.deepSeekRequest(body.bytes().decodeToString())
                is TextContent -> requestLogger.deepSeekRequest(body.text)
            }
            proceedWith(body)
        }
        httpClient.responsePipeline.intercept(HttpResponsePipeline.Receive) { container ->
            when (val body = container.response) {
                is ByteReadChannel -> {
                    val bytes = body.toByteArray()
                    requestLogger.deepSeekResponse(bytes.decodeToString())
                    proceedWith(container.copy(response = ByteReadChannel(bytes)))
                }
                is ByteArray -> {
                    requestLogger.deepSeekResponse(body.decodeToString())
                    proceedWith(container)
                }
                else -> proceedWith(container)
            }
        }
    }
}

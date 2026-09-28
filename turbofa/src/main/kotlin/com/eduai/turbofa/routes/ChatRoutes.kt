package com.eduai.turbofa.routes

import com.eduai.turbofa.logging.RequestLogger
import com.eduai.turbofa.service.ChatService
import com.eduai.turbofa.service.Provider
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LocalChatRequest(
    val prompt: String,
    @SerialName("fueling_id") val fuelingId: String? = null,
    val provider: Provider = Provider.DEEPSEEK,
)

@Serializable
data class LocalChatResponse(val response: String)

/** Rejection body, so Bruno receives JSON on 400 as well. */
@Serializable
data class LocalChatErrorResponse(val error: String)

/**
 * The JSON policy of the Bruno API (R9): fields outside the DTO are ignored, and a null default
 * (an absent fueling_id) stays omitted, so logged bodies match the 5a example. Application.kt
 * installs it once while the same instance encodes the route's responses.
 */
val ChatJson: Json = Json { ignoreUnknownKeys = true }

/**
 * `POST /chat` — the complete Bruno-facing API (R9): log point 1, delegate to ChatService,
 * log point 5, respond; a body outside the R9 DTO is rejected with 400 + LocalChatErrorResponse.
 */
fun Route.chatRoutes(chatService: ChatService, requestLogger: RequestLogger) {
    post("/chat") {
        val request = call.receiveChatRequest()
        if (request == null) {
            call.reject(requestLogger, INVALID_REQUEST)
            return@post
        }

        // R8 log point 1, before any processing (spec 3.2).
        requestLogger.brunoRequest(ChatJson.encodeToString(LocalChatRequest.serializer(), request))
        if (request.prompt.isBlank()) {
            call.reject(requestLogger, BLANK_PROMPT)
            return@post
        }

        // D6: fueling_id is a UUID string; any other shape would only reach the tool as a guaranteed
        // miss, so it is rejected here. An absent or blank id stays as before.
        val fuelingId = request.fuelingId
        if (fuelingId != null && fuelingId.isNotBlank() && !UUID_FORMAT.matches(fuelingId)) {
            call.reject(requestLogger, MALFORMED_FUELING_ID)
            return@post
        }

        val payload = LocalChatResponse(chatService.chat(request.prompt, request.fuelingId, request.provider))

        // R8 log point 5, with the exact body Bruno receives.
        requestLogger.brunoResponse(ChatJson.encodeToString(LocalChatResponse.serializer(), payload))
        call.respond(payload)
    }
}

/**
 * Receives the R9 DTO, or null when the body is not the contract's JSON shape
 * (deserialization failures → BadRequestException, converter misses → ContentTransformationException).
 */
private suspend fun ApplicationCall.receiveChatRequest(): LocalChatRequest? =
    try {
        receive<LocalChatRequest>()
    } catch (_: BadRequestException) {
        null
    } catch (_: ContentTransformationException) {
        null
    }

private suspend fun ApplicationCall.reject(requestLogger: RequestLogger, message: String) {
    val body = LocalChatErrorResponse(message)
    requestLogger.brunoResponse(ChatJson.encodeToString(LocalChatErrorResponse.serializer(), body))
    respond(HttpStatusCode.BadRequest, body)
}

private val UUID_FORMAT = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

private const val INVALID_REQUEST =
    "request body must be JSON like {\"prompt\": \"...\", \"fueling_id\": \"<uuid>\", \"provider\": \"deepseek\"}"
private const val BLANK_PROMPT = "prompt must not be blank"
private const val MALFORMED_FUELING_ID =
    "fueling_id must be a UUID like 99f068ca-ac6a-43fb-a53b-d2e7a573cfe2"

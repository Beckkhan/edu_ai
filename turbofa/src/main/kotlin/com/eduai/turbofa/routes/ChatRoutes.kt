// src/main/kotlin/com/eduai/turbofa/routes/ChatRoutes.kt
package com.eduai.turbofa.routes

import com.eduai.turbofa.logging.RequestLogger
import com.eduai.turbofa.service.ChatService
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

/**
 * R9 request of the Bruno contract: a prompt plus an OPTIONAL fueling_id.
 *
 * fueling_id is the UUID string of the external schema (D6) — R9's `<int>` is stale (E1). It is
 * null when Bruno omits the field; a non-string value fails deserialization and is rejected, and a
 * present string must additionally be the UUID textual form (checked in [chatRoutes]), so the tool
 * chain can never see anything but a string id.
 */
@Serializable
data class LocalChatRequest(
    val prompt: String,
    @SerialName("fueling_id") val fuelingId: String? = null,
)

/** R9 response: the assistant text — whether a tool ran is the agent's business, not Bruno's. */
@Serializable
data class LocalChatResponse(val response: String)

/** Body of a rejected request, so Bruno receives JSON on 400 as well. */
@Serializable
data class LocalChatErrorResponse(val error: String)

/**
 * The JSON policy of the Bruno API (R9): fields outside the DTO are ignored, and a null default
 * (an absent fueling_id) stays omitted, so logged bodies match the 5a example.
 *
 * Application.kt installs it once: `install(ContentNegotiation) { json(ChatJson) }`.
 */
val ChatJson: Json = Json { ignoreUnknownKeys = true }

/**
 * `POST /chat` — the complete Bruno-facing API (spec 3.1, R9).
 *
 * Thin by contract (R10): receive the DTO, emit log point 1, delegate to [ChatService] (which owns
 * history and the single fueling_id embedding), emit log point 5, respond. No DeepSeek, Koog, tool
 * or JDBC type is imported here — [ChatService] is the route's only dependency.
 *
 * A body that is not the R9 DTO (unparseable JSON, missing or blank prompt, a fueling_id that is not
 * the UUID form of D6) is rejected with 400 and a [LocalChatErrorResponse]; R8 log point 5 records
 * the body Bruno receives back on rejection too. Content negotiation with [ChatJson] must be
 * installed by the caller.
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

        val payload = LocalChatResponse(chatService.chat(request.prompt, request.fuelingId))

        // R8 log point 5, with the exact body Bruno receives.
        requestLogger.brunoResponse(ChatJson.encodeToString(LocalChatResponse.serializer(), payload))
        call.respond(payload)
    }
}

/**
 * Receives the R9 DTO, or null when the body is not the contract's JSON shape.
 *
 * ContentNegotiation wraps every deserialization failure (missing or blank-typed prompt, a
 * non-string fueling_id, malformed JSON) into [BadRequestException]; a body without a matching
 * converter (foreign Content-Type, empty body) surfaces as [ContentTransformationException].
 * Both mean the same thing to Bruno: not a valid request.
 */
private suspend fun ApplicationCall.receiveChatRequest(): LocalChatRequest? = try {
    receive<LocalChatRequest>()
} catch (_: BadRequestException) {
    null
} catch (_: ContentTransformationException) {
    null
}

/** Rejects a request that never reaches [ChatService]: log point 5, then a 400 with a JSON body. */
private suspend fun ApplicationCall.reject(requestLogger: RequestLogger, message: String) {
    val body = LocalChatErrorResponse(message)
    requestLogger.brunoResponse(ChatJson.encodeToString(LocalChatErrorResponse.serializer(), body))
    respond(HttpStatusCode.BadRequest, body)
}

/** Textual UUID form of the external fueling_id (D6), 8-4-4-4-12; hex case does not matter. */
private val UUID_FORMAT = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

private const val INVALID_REQUEST =
    "request body must be JSON like {\"prompt\": \"...\", \"fueling_id\": \"<uuid>\"}"
private const val BLANK_PROMPT = "prompt must not be blank"
private const val MALFORMED_FUELING_ID =
    "fueling_id must be a UUID like 99f068ca-ac6a-43fb-a53b-d2e7a573cfe2"

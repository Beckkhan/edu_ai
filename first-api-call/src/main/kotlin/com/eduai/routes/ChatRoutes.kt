package com.eduai.routes

import com.eduai.config.AppConfig
import com.eduai.config.MissingApiKeyException
import com.eduai.model.DeepSeekChatResponse
import com.eduai.model.ErrorResponse
import com.eduai.model.HealthResponse
import com.eduai.model.LocalChatRequest
import com.eduai.model.LocalChatResponse
import com.eduai.service.DeepSeekApiException
import com.eduai.service.DeepSeekClient
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun Application.chatRoutes(deepSeekClient: DeepSeekClient, config: AppConfig) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }
    install(StatusPages) {
        exception<MissingApiKeyException> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "DEEPSEEK_API_KEY is not set"),
            )
        }
        exception<DeepSeekApiException> { call, cause ->
            val status = when (cause.statusCode) {
                400 -> HttpStatusCode.BadRequest
                401 -> HttpStatusCode.Unauthorized
                403 -> HttpStatusCode.Forbidden
                404 -> HttpStatusCode.NotFound
                429 -> HttpStatusCode.TooManyRequests
                in 500..599 -> HttpStatusCode.BadGateway
                else -> HttpStatusCode.InternalServerError
            }
            call.respond(status, ErrorResponse(cause.message ?: "DeepSeek API error"))
        }
        // In Ktor 3, malformed request bodies can surface as any of these,
        // depending on where conversion fails (see KTOR exception hierarchy).
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${cause.message}"))
        }
        exception<ContentConvertException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${cause.message}"))
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: expected JSON with a 'prompt' field"))
        }
        exception<SerializationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${cause.message}"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error: ${cause.message}"))
        }
    }
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        post("/api/chat") {
            val request = call.receive<LocalChatRequest>()
            if (request.prompt.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("'prompt' must not be blank"))
                return@post
            }
            val deepSeekResponse = deepSeekClient.chat(request.prompt)
            call.respond(deepSeekResponse.toLocalChatResponse())
        }
    }
}

private fun DeepSeekChatResponse.toLocalChatResponse(): LocalChatResponse {
    val choice = choices.firstOrNull()
        ?: throw DeepSeekApiException("DeepSeek API returned no choices", 502)
    val content = choice.message?.content
        ?: throw DeepSeekApiException("DeepSeek API returned an empty message", 502)
    return LocalChatResponse(
        response = content,
        reasoning = choice.message.reasoningContent,
        model = model,
        usage = usage,
        finishReason = choice.finishReason,
    )
}

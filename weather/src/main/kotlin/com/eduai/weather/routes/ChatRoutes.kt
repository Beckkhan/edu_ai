// src/main/kotlin/com/eduai/weather/routes/ChatRoutes.kt
package com.eduai.weather.routes

import com.eduai.weather.service.ChatService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class LocalChatRequest(val prompt: String)

@Serializable
data class LocalChatResponse(val response: String)

/** Requires ContentNegotiation with kotlinx.serialization installed on the application. */
fun Route.chatRoutes(service: ChatService) {
    post("/chat") {
        val request = call.receive<LocalChatRequest>()
        val response = service.chat(request.prompt)
        call.respond(LocalChatResponse(response = response))
    }
}

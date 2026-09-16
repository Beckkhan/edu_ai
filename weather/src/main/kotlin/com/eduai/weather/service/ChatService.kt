// src/main/kotlin/com/eduai/weather/service/ChatService.kt
package com.eduai.weather.service

import com.eduai.weather.client.deepseek.ChatMessage
import com.eduai.weather.client.deepseek.WeatherAgent
import com.eduai.weather.history.ChatHistoryStore
import com.eduai.weather.weather.WeatherService

class ChatService(
    private val agent: WeatherAgent,
    private val history: ChatHistoryStore,
    private val weatherService: WeatherService,
) {

    /** City awaiting a country clarification; set on CLARIFY, consumed by the next user answer. */
    private var pendingCity: String? = null

    suspend fun chat(prompt: String): String {
        // A) The user is answering an active clarification → deterministic FETCH, no LLM parsing
        pendingCity?.let { city ->
            pendingCity = null
            history.append(ChatMessage(role = "user", content = prompt))
            val answer = processFetch("FETCH: $city, ${prompt.trim()}", prompt)
            history.append(ChatMessage(role = "assistant", content = answer))
            history.flushToFile()
            return answer
        }

        // B) Normal flow: strict LLM router
        history.append(ChatMessage(role = "user", content = prompt))
        val routed = agent.chat(
            listOf(ChatMessage(role = "system", content = ROUTER_INSTRUCTION)) + history.recent(10)
        )

        return when {
            routed.startsWith(CLARIFY_PREFIX) -> {
                val city = routed.removePrefix(CLARIFY_PREFIX).trim()
                pendingCity = city
                val answer = "Could you please specify the country for $city?"
                history.append(ChatMessage(role = "assistant", content = answer))
                history.flushToFile()
                answer
            }

            routed.startsWith(FETCH_PREFIX) -> {
                val answer = processFetch(routed, prompt)
                history.append(ChatMessage(role = "assistant", content = answer))
                history.flushToFile()
                answer
            }

            else -> {
                val answer = routed.removePrefix(NORMAL_PREFIX).trim()
                history.append(ChatMessage(role = "assistant", content = answer))
                history.flushToFile()
                answer
            }
        }
    }

    /** "FETCH: City, Country" → real weather → agent saves via tool call → formatted final answer. */
    private suspend fun processFetch(fetchCommand: String, originalPrompt: String): String {
        val (city, country) = parseLocation(fetchCommand.removePrefix(FETCH_PREFIX).trim())
        val weather = weatherService.getWeather(city, country)
        return agent.chat(
            listOf(
                ChatMessage(
                    role = "system",
                    content = "The real weather in ${weather.city}, ${weather.country} is " +
                        "${weather.temperature}°C, ${weather.description}. " +
                        "Call save_weather with this data (city, country, temperature, description), " +
                        "then answer the user's original question using this exact data.",
                ),
                ChatMessage(role = "user", content = originalPrompt),
            )
        )
    }

    /** "St. Petersburg, Russia" → ("St. Petersburg", "Russia"). */
    private fun parseLocation(location: String): Pair<String, String> {
        val idx = location.lastIndexOf(',')
        return if (idx < 0) location to ""
        else location.substring(0, idx).trim() to location.substring(idx + 1).trim()
    }

    companion object {
        private const val CLARIFY_PREFIX = "CLARIFY: "
        private const val FETCH_PREFIX = "FETCH: "
        private const val NORMAL_PREFIX = "NORMAL: "
        private const val ROUTER_INSTRUCTION =
            "Analyze the request. Output EXACTLY one of these formats, with NO other text:\n" +
                "1. If weather query and city is ambiguous: 'CLARIFY: [City Name]'\n" +
                "2. If weather query and city is clear: 'FETCH: [City], [Country]'\n" +
                "3. Otherwise: 'NORMAL: [Your normal reply]'"
    }
}

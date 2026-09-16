// src/test/kotlin/com/eduai/weather/service/ChatServiceTest.kt
package com.eduai.weather.service

import com.eduai.weather.client.deepseek.ChatMessage
import com.eduai.weather.client.deepseek.WeatherAgent
import com.eduai.weather.history.ChatHistoryStore
import com.eduai.weather.history.InMemoryHistoryCache
import com.eduai.weather.history.TextFileHistoryWriter
import com.eduai.weather.weather.WeatherData
import com.eduai.weather.weather.WeatherService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatServiceTest {

    private fun historyMock() = mockk<ChatHistoryStore> {
        every { append(any()) } just Runs
        every { recent(any()) } returns emptyList()
        every { flushToFile() } just Runs
    }

    @Test
    fun `FETCH route calls weather service and hands real weather to the agent for the tool call`() = runTest {
        val agent = mockk<WeatherAgent> {
            coEvery { chat(any()) } returns "FETCH: St. Petersburg, Russia" andThen
                "В Санкт-Петербурге сейчас ясно, 21.5°C"
        }
        val history = historyMock()
        val weather = WeatherData("St Petersburg", "Russia", 21.5, "Clear")
        val weatherService = mockk<WeatherService> {
            coEvery { getWeather("St. Petersburg", "Russia") } returns weather
        }

        val service = ChatService(agent, history, weatherService)

        val response = service.chat("What is the weather in St. Petersburg?")

        assertEquals("В Санкт-Петербурге сейчас ясно, 21.5°C", response)
        coVerify { weatherService.getWeather("St. Petersburg", "Russia") }
        // DB writes happen only via the LLM-initiated tool call: the agent must receive the
        // real weather data and the save_weather instruction, not a direct tool.execute()
        coVerify(exactly = 2) { agent.chat(any()) }
    }

    @Test
    fun `CLARIFY route stores pending city asks for country and calls no weather service`() = runTest {
        val agent = mockk<WeatherAgent> {
            coEvery { chat(any()) } returns "CLARIFY: St. Petersburg"
        }
        val history = historyMock()
        val weatherService = mockk<WeatherService>()

        val service = ChatService(agent, history, weatherService)

        val response = service.chat("Weather in St. Petersburg")

        assertEquals("Could you please specify the country for St. Petersburg?", response)
        coVerify(exactly = 0) { weatherService.getWeather(any(), any()) }
    }

    @Test
    fun `normal reply strips the NORMAL prefix`() = runTest {
        val agent = mockk<WeatherAgent> { coEvery { chat(any()) } returns "NORMAL: Привет!" }
        val history = historyMock()
        val weatherService = mockk<WeatherService>()

        val service = ChatService(agent, history, weatherService)

        val response = service.chat("Привет")

        assertEquals("Привет!", response)
        coVerify(exactly = 0) { weatherService.getWeather(any(), any()) }
    }

    @Test
    fun `two-turn clarification flow resolves deterministically and fetches via the agent`() = runTest {
        val calls = mutableListOf<List<ChatMessage>>()
        val agent = mockk<WeatherAgent> {
            coEvery { chat(any()) } coAnswers {
                calls += firstArg<List<ChatMessage>>()
                if (calls.size == 1) "CLARIFY: St. Petersburg"
                else "В Санкт-Петербурге сейчас ясно, 5.0°C"
            }
        }
        val history = historyMock()
        val weather = WeatherData("St Petersburg", "Russia", 5.0, "Clear")
        val weatherService = mockk<WeatherService> {
            coEvery { getWeather("St. Petersburg", "Russia") } returns weather
        }

        val service = ChatService(agent, history, weatherService)

        // Turn 1: ambiguous city → clarification question, pending city remembered
        val first = service.chat("Weather in St. Petersburg")
        assertEquals("Could you please specify the country for St. Petersburg?", first)
        coVerify(exactly = 0) { weatherService.getWeather(any(), any()) }

        // Turn 2: user answers → deterministic FETCH, the router is not consulted again
        val second = service.chat("Russia")
        assertEquals("В Санкт-Петербурге сейчас ясно, 5.0°C", second)
        coVerify { weatherService.getWeather("St. Petersburg", "Russia") }

        // Only 2 LLM calls total: router (turn 1) + final agent call (turn 2);
        // the final call carries the real weather and the save_weather instruction (D6)
        assertEquals(2, calls.size)
        assertTrue(calls[1].first().content.contains("The real weather"))
        assertTrue(calls[1].first().content.contains("save_weather"))
    }

    @Test
    fun `history is restored from file on startup`() {
        val writer = mockk<TextFileHistoryWriter> {
            every { readAll() } returns listOf(
                ChatMessage(role = "user", content = "Привет"),
                ChatMessage(role = "assistant", content = "Здравствуйте"),
            )
        }
        val store = ChatHistoryStore(cache = InMemoryHistoryCache(), writer = writer)

        store.restoreFromDisk(n = 50)

        assertEquals(2, store.recent().size)
        assertEquals("Привет", store.recent()[0].content)
        assertEquals("Здравствуйте", store.recent()[1].content)
    }
}

// src/main/kotlin/com/eduai/weather/tool/SaveWeatherTool.kt
package com.eduai.weather.tool

import com.eduai.weather.db.WeatherLogRepository
import com.eduai.weather.weather.WeatherData

/** Saves weather data to Postgres. String form: "city|country|temperature|description". */
class SaveWeatherTool(private val repo: WeatherLogRepository) : Tool {

    override val name = "save_weather"
    override val description =
        "Saves weather for a city to the weather_log table (city, country, temperature, description)"

    suspend fun execute(data: WeatherData): String {
        val id = repo.save(data.city, data.country, data.temperature, data.description)
        return "Saved weather for ${data.city}, ${data.country} (id=$id)"
    }

    override suspend fun execute(input: String): String {
        val parts = input.split("|", limit = 4)
        require(parts.size == 4) { "Expected input 'city|country|temperature|description', got: $input" }
        return execute(
            WeatherData(
                city = parts[0].trim(),
                country = parts[1].trim(),
                temperature = parts[2].trim().toDouble(),
                description = parts[3].trim(),
            )
        )
    }
}

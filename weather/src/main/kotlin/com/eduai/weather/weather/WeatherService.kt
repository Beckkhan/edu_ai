// src/main/kotlin/com/eduai/weather/weather/WeatherService.kt
package com.eduai.weather.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class WeatherData(
    val city: String,
    val country: String,
    val temperature: Double,
    val description: String,
)

/** Real weather via the free Open-Meteo API (no key): geocoding, then current forecast. */
class WeatherService(
    private val http: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) {

    private val stAbbrev = Regex("""(?i)\bst\.""")
    private val cyrillic = Regex("""[А-Яа-яЁё]""")

    @Serializable
    private data class GeoResult(
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val country: String? = null,
        val admin1: String? = null,
        @SerialName("feature_code") val featureCode: String? = null,
    )

    @Serializable
    private data class GeocodingResponse(val results: List<GeoResult> = emptyList())

    @Serializable
    private data class CurrentWeather(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int,
    )

    @Serializable
    private data class ForecastResponse(val current: CurrentWeather)

    /** Fetches current weather for the city and country chosen by the LLM router. */
    suspend fun getWeather(city: String, country: String): WeatherData {
        // The geocoder is literal: "St. Petersburg" matches only US places, so also
        // query the expanded "Saint Petersburg" form and merge the results.
        val expanded = stAbbrev.containsMatchIn(city)
        val queries = if (expanded) listOf(city, stAbbrev.replace(city, "Saint")) else listOf(city)
        val core = city.trimEnd('?', '!', '.', ',').substringAfterLast(' ')
        // For Latin queries, keep only fuzzy matches containing the city's core word.
        // Skipped for Cyrillic queries: inflected forms ("Москве") never appear in Latin result names.
        val applyCore = expanded || !cyrillic.containsMatchIn(city)

        val place = queries
            .flatMap { geocode(it, count = 5) }
            .filter { it.country != null && (it.featureCode?.startsWith("PPL") ?: true) }
            .filter { !applyCore || it.name.contains(core, ignoreCase = true) }
            .firstOrNull { countryMatches(it.country.orEmpty(), country) }
            ?: error("City not found: $city, $country")

        val forecast: ForecastResponse = http.get("https://api.open-meteo.com/v1/forecast") {
            url {
                parameters.append("latitude", place.latitude.toString())
                parameters.append("longitude", place.longitude.toString())
                parameters.append("current", "temperature_2m,weather_code")
            }
        }.body()
        return WeatherData(
            city = place.name,
            country = place.country.orEmpty(),
            temperature = forecast.current.temperature,
            description = wmoDescription(forecast.current.weatherCode),
        )
    }

    private suspend fun geocode(query: String, count: Int): List<GeoResult> =
        http.get("https://geocoding-api.open-meteo.com/v1/search") {
            url {
                parameters.append("name", query)
                parameters.append("count", count.toString())
            }
        }.body<GeocodingResponse>().results

    /** Loose country match: handles "USA" vs "United States", "UK" vs "United Kingdom". */
    private fun countryMatches(candidate: String, requested: String): Boolean {
        val aliases = mapOf("usa" to "united states", "us" to "united states", "uk" to "united kingdom")
        val c = candidate.lowercase()
        val r = aliases[requested.lowercase()] ?: requested.lowercase()
        return c == r || c.contains(r) || r.contains(c)
    }

    /** WMO weather code → description: 0=Clear, 1-3=Cloudy, 51+=Rain. */
    private fun wmoDescription(code: Int): String = when (code) {
        0 -> "Clear"
        in 1..3 -> "Cloudy"
        else -> "Rain"
    }
}

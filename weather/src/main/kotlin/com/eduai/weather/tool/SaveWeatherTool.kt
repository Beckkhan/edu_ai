// src/main/kotlin/com/eduai/weather/tool/SaveWeatherTool.kt
package com.eduai.weather.tool

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.typeToken
import com.eduai.weather.db.WeatherLogRepository
import com.eduai.weather.logging.RequestLogger
import java.sql.Timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Arguments of the save_weather LLM tool; schema per resources/tools/save_weather.json. */
@Serializable
data class SaveWeatherArgs(
    val city: String,
    val country: String,
    val temperature: Double,
    val description: String,
    @SerialName("received_at") val receivedAt: String = "",
)

/**
 * Koog tool handler for save_weather: persists weather via [WeatherLogRepository].
 * The descriptor (name/description/parameters) is loaded from resources/tools/save_weather.json.
 * received_at comes from [receiptTimeProvider] — the DeepSeek response receipt time (D5).
 */
class SaveWeatherTool(
    private val repo: WeatherLogRepository,
    private val requestLogger: RequestLogger,
    descriptor: ToolDescriptor,
    private val receiptTimeProvider: () -> Timestamp = { Timestamp(System.currentTimeMillis()) },
) : Tool<SaveWeatherArgs, String>(
    argsType = typeToken<SaveWeatherArgs>(),
    resultType = typeToken<String>(),
    descriptor = descriptor,
) {

    override suspend fun execute(args: SaveWeatherArgs): String {
        // R2 log point 4: emitted only when the LLM actually invokes the tool
        requestLogger.toolCall(Json.encodeToString(SaveWeatherArgs.serializer(), args))
        val id = repo.save(
            args.city, args.country, args.temperature, args.description,
            receiptTimeProvider(),
        )
        return "Saved weather for ${args.city}, ${args.country} (id=$id)"
    }

    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result
}

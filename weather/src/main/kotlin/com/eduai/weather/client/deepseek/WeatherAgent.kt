// src/main/kotlin/com/eduai/weather/client/deepseek/WeatherAgent.kt
package com.eduai.weather.client.deepseek

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.eduai.weather.db.WeatherLogRepository
import com.eduai.weather.logging.RequestLogger
import com.eduai.weather.tool.SaveWeatherTool
import java.io.File
import java.sql.Timestamp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Contract 5d: the Koog agent surface ChatService depends on. */
interface WeatherAgent {
    /** Returns the final assistant text; tool calls execute inside the agent. */
    suspend fun chat(messages: List<ChatMessage>): String
}

/**
 * Koog implementation of [WeatherAgent]: an AIAgent with singleRunStrategy (D1) whose tool
 * set is loaded from the resources/tools directory (D3) and is never empty (R4).
 * Stateless per D7: every chat() builds a fresh agent whose initial Prompt carries the full
 * message history; no Koog session state is kept between calls.
 */
class KoogWeatherAgent(
    private val executor: PromptExecutor,
    private val model: LLModel,
    private val repo: WeatherLogRepository,
    private val requestLogger: RequestLogger,
    private val receiptTimeProvider: () -> Timestamp = { Timestamp(System.currentTimeMillis()) },
) : WeatherAgent {

    private val toolRegistry: ToolRegistry = ToolRegistry {
        loadToolDescriptors().forEach { descriptor ->
            require(descriptor.name == "save_weather") {
                "No tool handler for '${descriptor.name}'; only save_weather is supported"
            }
            tool(SaveWeatherTool(repo, requestLogger, descriptor, receiptTimeProvider))
        }
    }

    override suspend fun chat(messages: List<ChatMessage>): String {
        require(messages.isNotEmpty()) { "chat() requires at least one message" }
        val last = messages.last()
        require(last.role == "user") { "Last message must be the user prompt, got role: ${last.role}" }

        val initialPrompt = prompt("chat") {
            messages.dropLast(1).forEach { message ->
                when (message.role) {
                    "system" -> system(message.content)
                    "user" -> user(message.content)
                    "assistant" -> assistant(message.content)
                    else -> throw IllegalArgumentException("Unsupported message role: ${message.role}")
                }
            }
        }
        return buildAgent(initialPrompt).run(last.content)
    }

    private fun buildAgent(initialPrompt: Prompt): AIAgent<String, String> =
        AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .toolRegistry(toolRegistry)
            .prompt(initialPrompt)
            .graphStrategy(singleRunStrategy())
            .build()

    /** Loads tool descriptors from the resources/tools directory; fails fast on an empty tool set (R4). */
    private fun loadToolDescriptors(): List<ToolDescriptor> {
        val toolsDir = File(
            checkNotNull(javaClass.getResource("/tools/")) {
                "resources/tools directory not found on the classpath"
            }.toURI()
        )
        val files = toolsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()
        require(files.isNotEmpty()) {
            "resources/tools contains no *.json tool descriptors (R4: tools array must be non-empty)"
        }
        return files.map(::parseDescriptor)
    }

    private fun parseDescriptor(file: File): ToolDescriptor {
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        val name = json.getValue("name").jsonPrimitive.content
        val description = json["description"]?.jsonPrimitive?.content.orEmpty()
        val parameters = json["parameters"]?.jsonObject
        val properties = parameters?.get("properties")?.jsonObject ?: emptyMap()
        val required = parameters?.get("required")?.jsonArray
            ?.map { it.jsonPrimitive.content } ?: emptyList()

        val params = properties.map { (key, value) ->
            ToolParameterDescriptor(
                name = key,
                description = value.jsonObject["description"]?.jsonPrimitive?.content.orEmpty(),
                type = when (value.jsonObject["type"]?.jsonPrimitive?.content) {
                    "number" -> ToolParameterType.Float
                    else -> ToolParameterType.String
                },
            )
        }
        return ToolDescriptor(
            name = name,
            description = description,
            requiredParameters = params.filter { it.name in required },
            optionalParameters = params.filter { it.name !in required },
        )
    }
}

package com.eduai.turbofa.client.deepseek

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
import com.eduai.turbofa.db.FuelingDataSource
import com.eduai.turbofa.logging.RequestLogger
import com.eduai.turbofa.tool.FuelingInfoTool
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Must match the tool name in resources/tools/get_fueling_info.json (5b). */
private const val FUELING_INFO_TOOL = "get_fueling_info"

/** 5d contract: the role strings ChatService writes on [ChatMessage]. */
private const val ROLE_SYSTEM = "system"
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

private const val TOOLS_RESOURCE = "/tools/"

private const val JSON_SUFFIX = ".json"

/**
 * The stakeholder wants plain readable text; DeepSeek otherwise answers with heavy markdown
 * (### headers, | tables, - lists, ** bold) that reads as visual clutter. 3-5 sentences with the
 * key facts of the fueling, nothing but the tool result's data — no analysis, no recommendations.
 */
private const val SYSTEM_PROMPT =
    "You are a fueling-data assistant. Respond in the same language as the user's request. " +
        "Answer in plain text WITHOUT any markdown formatting: no headers, tables, lists, or " +
        "bold text. Only factual data from the tool result — no analysis or recommendations. " +
        "Write a short coherent text of 3-5 sentences covering: fueling id (short form) and " +
        "status; fuel type, volume, price per liter, total amount; payment type, " +
        "method/payment system, payment status; station id/name, brand, location (region, " +
        "city); fueling time range from start to completion."

interface TurbofaAgent {
    suspend fun chat(messages: List<ChatMessage>): String
}

class KoogTurbofaAgent(
    private val executor: PromptExecutor,
    private val model: LLModel,
    private val dataSource: FuelingDataSource,
    requestLogger: RequestLogger,
) : TurbofaAgent {

    /** 5c: built once at startup, so a missing/broken descriptor fails the process, not a request. */
    private val toolRegistry: ToolRegistry = ToolRegistry {
        loadToolDescriptors().forEach { descriptor ->
            require(descriptor.name == FUELING_INFO_TOOL) {
                "No tool handler for '${descriptor.name}'; only $FUELING_INFO_TOOL is supported"
            }
            tool(FuelingInfoTool(dataSource, requestLogger, descriptor))
        }
    }

    override suspend fun chat(messages: List<ChatMessage>): String {
        require(messages.isNotEmpty()) { "chat() requires at least one message" }
        val promptMessage = messages.last()
        require(promptMessage.role == ROLE_USER) {
            "The last message must be the user prompt, got role '${promptMessage.role}'"
        }

        // D2: the whole prior dialogue is the initial prompt; the last user message is the run input
        // (Koog appends it as the final user message of the same, single Prompt).
        return buildAgent(historyPrompt(messages.dropLast(1))).run(promptMessage.content)
    }

    private fun historyPrompt(history: List<ChatMessage>): Prompt = prompt("chat") {
        system(SYSTEM_PROMPT)
        history.forEach { message ->
            when (message.role) {
                ROLE_SYSTEM -> system(message.content)
                ROLE_USER -> user(message.content)
                ROLE_ASSISTANT -> assistant(message.content)
                else -> throw IllegalArgumentException("Unsupported message role '${message.role}'")
            }
        }
    }

    private fun buildAgent(history: Prompt): AIAgent<String, String> =
        AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .toolRegistry(toolRegistry)
            .prompt(history)
            .graphStrategy(singleRunStrategy())
            .build()

    /** The `*.json` descriptors of `resources/tools` (5c); an empty set fails fast (R7). */
    private fun loadToolDescriptors(): List<ToolDescriptor> {
        val directory = File(
            checkNotNull(javaClass.getResource(TOOLS_RESOURCE)) {
                "$TOOLS_RESOURCE is not on the classpath — no tool descriptor to register (5c)"
            }.toURI(),
        )
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(JSON_SUFFIX) }.orEmpty()
        require(files.isNotEmpty()) {
            "resources/tools contains no *$JSON_SUFFIX descriptor — the tools array must never be empty (R7)"
        }
        return files.sortedBy(File::getName).map(::parseDescriptor)
    }
}

private fun parseDescriptor(file: File): ToolDescriptor {
    val json = Json.parseToJsonElement(file.readText()).jsonObject
    val parameters = json["parameters"] as? JsonObject
    val declared = parameterDescriptors(parameters)
    val required = requiredNames(parameters).toSet()
    return ToolDescriptor(
        name = json.stringField("name") ?: error("Tool descriptor ${file.name} has no \"name\""),
        description = json.stringField("description").orEmpty(),
        requiredParameters = declared.filter { it.name in required },
        optionalParameters = declared.filterNot { it.name in required },
    )
}

private fun parameterDescriptors(schema: JsonObject?): List<ToolParameterDescriptor> =
    (schema?.get("properties") as? JsonObject).orEmpty().map { (name, property) ->
        val parameter = property as? JsonObject
        ToolParameterDescriptor(
            name = name,
            description = parameter.stringField("description").orEmpty(),
            type = parameterType(parameter),
        )
    }

private fun parameterType(schema: JsonObject?): ToolParameterType = when (schema.stringField("type")) {
    "boolean" -> ToolParameterType.Boolean
    "integer" -> ToolParameterType.Integer
    "number" -> ToolParameterType.Float
    "array" -> ToolParameterType.List(parameterType(schema?.get("items") as? JsonObject))
    "object" -> ToolParameterType.Object(
        properties = parameterDescriptors(schema),
        requiredProperties = requiredNames(schema),
    )
    else -> ToolParameterType.String
}

private fun requiredNames(schema: JsonObject?): List<String> =
    (schema?.get("required") as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }

private fun JsonObject?.stringField(field: String): String? = (this?.get(field) as? JsonPrimitive)?.content

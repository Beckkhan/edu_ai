// src/main/kotlin/com/eduai/turbofa/client/deepseek/TurbofaAgent.kt
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

/** The tool handler this agent owns; the descriptor of resources/tools/get_fueling_info.json must match (5b). */
private const val FUELING_INFO_TOOL = "get_fueling_info"

/** Roles of the 5d contract — the strings ChatService uses on [ChatMessage]. */
private const val ROLE_SYSTEM = "system"
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

/** Where the tool descriptors live on the classpath (5c). */
private const val TOOLS_RESOURCE = "/tools/"

private const val JSON_SUFFIX = ".json"

/**
 * Spec 5d: the surface ChatService depends on. Tool calls execute inside the agent, so the service
 * never sees a Koog type; chat() receives the complete message list — ChatService already embedded
 * the fueling_id when the request carried one (5d, the single embedding point).
 */
interface TurbofaAgent {
    /** Returns the final assistant text; tool calls execute inside the agent. */
    suspend fun chat(messages: List<ChatMessage>): String
}

/**
 * Koog implementation of [TurbofaAgent]: an [AIAgent] on `singleRunStrategy` (D1) whose tool set is
 * loaded from the `*.json` files of `resources/tools` at startup (5c).
 *
 * `singleRunStrategy` makes the `get_fueling_info` call optional: a dialogue without a fueling_id
 * gets a plain-text reply, a question carrying one can trigger the tool (R9). The registry always
 * holds [FUELING_INFO_TOOL] — construction fails fast when no descriptor is present, so every
 * backend→DeepSeek request carries a non-empty `tools` array (R7).
 *
 * Stateless (D2): every [chat] call builds ONE fresh agent whose initial Prompt is the received
 * dialogue; no Koog session state survives a call — ChatHistoryStore is the single source of truth.
 *
 * @param executor Koog executor of [DeepSeekClient] (R6)
 * @param model model resolved from DEEPSEEK_MODEL
 * @param dataSource read-only queries the tool handler runs (5f)
 * @param requestLogger R2 log point 4 is emitted by the tool handler on an actual tool call (spec 3.2)
 */
class KoogTurbofaAgent(
    private val executor: PromptExecutor,
    private val model: LLModel,
    private val dataSource: FuelingDataSource,
    requestLogger: RequestLogger,
) : TurbofaAgent {

    /** 5c: built once at startup so a missing/broken descriptor fails the process, not a request. */
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

    /** One Prompt with the system/user/assistant roles of the received list (5d, D2). */
    private fun historyPrompt(history: List<ChatMessage>): Prompt = prompt("chat") {
        history.forEach { message ->
            when (message.role) {
                ROLE_SYSTEM -> system(message.content)
                ROLE_USER -> user(message.content)
                ROLE_ASSISTANT -> assistant(message.content)
                else -> throw IllegalArgumentException("Unsupported message role '${message.role}'")
            }
        }
    }

    /** A fresh agent per call (D2): no run state is kept between requests. */
    private fun buildAgent(history: Prompt): AIAgent<String, String> =
        AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .toolRegistry(toolRegistry)
            .prompt(history)
            .graphStrategy(singleRunStrategy())
            .build()

    /** The `*.json` descriptors of `resources/tools` (5c); fails fast when the set would be empty (R7). */
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

/**
 * One JSON descriptor (the T1 format, 5b) → Koog [ToolDescriptor]: the top-level `properties` become
 * the parameter descriptors, `required` splits them into required and optional parameters.
 */
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

/** The JSON-schema `properties` of an object schema (top-level or nested) → Koog parameters. */
private fun parameterDescriptors(schema: JsonObject?): List<ToolParameterDescriptor> =
    (schema?.get("properties") as? JsonObject).orEmpty().map { (name, property) ->
        val parameter = property as? JsonObject
        ToolParameterDescriptor(
            name = name,
            description = parameter.stringField("description").orEmpty(),
            type = parameterType(parameter),
        )
    }

/**
 * JSON-schema `type` → Koog's parameter model, the translation this loader owns for the descriptors
 * of `resources/tools`: primitives, arrays of them and nested objects. A missing or unsupported type
 * falls back to [ToolParameterType.String] — the type Koog's OpenAI/DeepSeek schema generator
 * reports for plain text parameters, which is what the current descriptor uses (5b).
 */
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

/** The JSON-schema `required` names of an object schema. */
private fun requiredNames(schema: JsonObject?): List<String> =
    (schema?.get("required") as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }

/** The String value of a JSON-schema field, or null when it is absent. */
private fun JsonObject?.stringField(field: String): String? = (this?.get(field) as? JsonPrimitive)?.content

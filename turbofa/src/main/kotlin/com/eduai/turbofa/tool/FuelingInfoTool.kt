// src/main/kotlin/com/eduai/turbofa/tool/FuelingInfoTool.kt
package com.eduai.turbofa.tool

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.typeToken
import com.eduai.turbofa.db.FuelingDataSource
import com.eduai.turbofa.logging.RequestLogger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Arguments of the get_fueling_info LLM tool; parameters per resources/tools/get_fueling_info.json (5b, D6). */
@Serializable
data class GetFuelInfoArgs(
    @SerialName("fueling_id") val fuelingId: String,
)

/**
 * Koog tool handler for get_fueling_info (R7): aggregates the proliv data of one fueling from
 * the fueling / payment / vendors databases through the read-only queries of [FuelingDataSource]
 * (spec 5f) and returns it as compact JSON for DeepSeek to summarize.
 *
 * The descriptor (name/description/parameters) is loaded by the agent from
 * resources/tools/get_fueling_info.json and passed in as-is, so the handler reports exactly the
 * schema the model was given (5b).
 */
class FuelingInfoTool(
    private val dataSource: FuelingDataSource,
    private val requestLogger: RequestLogger,
    descriptor: ToolDescriptor,
) : Tool<GetFuelInfoArgs, String>(
    argsType = typeToken<GetFuelInfoArgs>(),
    resultType = typeToken<String>(),
    descriptor = descriptor,
) {

    override suspend fun execute(args: GetFuelInfoArgs): String {
        // R2 log point 4 (spec 3.2): emitted only when the LLM actually invokes the tool.
        requestLogger.toolCall(Json.encodeToString(GetFuelInfoArgs.serializer(), args))

        val fueling = dataSource.fuelingById(args.fuelingId)
        // D8: user_id is the only verified link from a fueling to payments (order_id is not a
        // fueling id), so the payments are scoped to the fueling's user.
        val userId = fueling?.get("user_id") as? String

        val result = buildJsonObject {
            put("fuelings", rowToJson(fueling))
            // null when there is no fueling row: without user_id the query cannot be scoped
            put(
                "payments",
                userId?.let { rowsToJson(dataSource.paymentsByUserId(it, PAYMENTS_LIMIT)) } ?: JsonNull,
            )
            put("fueling_orders", rowToJson(dataSource.fuelingOrdersById(args.fuelingId)))
            put("fueling_events", rowsToJson(dataSource.fuelingEventsById(args.fuelingId, EVENTS_LIMIT)))
            // D9: best-effort — the table covers only a subset of fuelings, a missing row is normal
            put("vendor_fueling_orders", rowToJson(dataSource.vendorFuelingOrdersById(args.fuelingId)))
        }.toString()

        // R2 log point 4b (spec 3.2, D10): the aggregated result coming back from Postgres.
        requestLogger.postgresResponse(result)

        return result
    }

    /** The result already is JSON text; it must reach the model unquoted and unescaped (R11). */
    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result

    private companion object {
        /** D8: the fueling user's latest 10 payments (R11). */
        const val PAYMENTS_LIMIT = 10

        /** R11: the latest 20 events of the fueling. */
        const val EVENTS_LIMIT = 20

        /** Raw JDBC column map (spec 5f) → JSON object; no row → JSON null. */
        fun rowToJson(row: Map<String, Any?>?): JsonElement =
            if (row == null) JsonNull else JsonObject(row.mapValues { (_, value) -> valueToJson(value) })

        fun rowsToJson(rows: List<Map<String, Any?>>): JsonElement =
            JsonArray(rows.map { rowToJson(it) })

        /**
         * Values stay opaque (spec 5f): primitives keep their type, everything else
         * (Timestamps, jsonb objects, ...) is stringified as-is.
         */
        fun valueToJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
}

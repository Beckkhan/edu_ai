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

/** Arguments of get_fueling_info; parameters follow resources/tools/get_fueling_info.json (5b, D6). */
@Serializable
data class GetFuelInfoArgs(
    @SerialName("fueling_id") val fuelingId: String,
)

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
        requestLogger.postgresRequest(Json.encodeToString(GetFuelInfoArgs.serializer(), args))

        val fueling = dataSource.fuelingById(args.fuelingId)
        // D8: user_id is the only verified link from a fueling to payments (order_id is not a
        // fueling id), so the payments are scoped to the fueling's user.
        val userId = fueling?.get("user_id") as? String

        val result = buildJsonObject {
            put("fuelings", rowToJson(fueling))
            // Without user_id (no fueling row) the payments query cannot be scoped — null, not [].
            put(
                "payments",
                userId?.let { rowsToJson(dataSource.paymentsByUserId(it)) } ?: JsonNull,
            )
            put("fueling_orders", rowToJson(dataSource.fuelingOrdersById(args.fuelingId)))
            put("fueling_events", rowsToJson(dataSource.fuelingEventsById(args.fuelingId)))
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
        fun rowToJson(row: Map<String, Any?>?): JsonElement =
            if (row == null) JsonNull else JsonObject(row.mapValues { (_, value) -> valueToJson(value) })

        fun rowsToJson(rows: List<Map<String, Any?>>): JsonElement =
            JsonArray(rows.map { rowToJson(it) })

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

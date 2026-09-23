// src/main/kotlin/com/eduai/turbofa/db/FuelingDataSource.kt
package com.eduai.turbofa.db

import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * The five SELECT-only queries of spec 5f: the fueling data of one fueling_id, gathered from the
 * `fueling`, `payment` and `vendors` databases (D7).
 *
 * Every id is a TEXT UUID (D6), hence the String parameters. Rows come back as ordered raw column
 * maps and go to the tool as-is (no domain classes, R10): the heterogeneous epoch timestamps
 * (numeric / bigint / text) stay opaque, and only jsonb columns are unwrapped to their text,
 * because a [PGobject] cannot be serialized.
 *
 * @param fuelingDatabase pool for the `fueling` database (query 1)
 * @param paymentDatabase pool for the `payment` database (query 2)
 * @param vendorsDatabase pool for the `vendors` database (queries 3-5)
 */
class FuelingDataSource(
    private val fuelingDatabase: DataSource,
    private val paymentDatabase: DataSource,
    private val vendorsDatabase: DataSource,
) {

    /**
     * 1) The fueling row, or null when the id is unknown. `fuelings` is partitioned by month; the
     * parent table routes the lookup across its partitions.
     *
     * The parent carries no primary key, so should a UUID ever repeat the first row is returned.
     */
    fun fuelingById(id: String): Map<String, Any?>? =
        query(fuelingDatabase, SQL_FUELING_BY_ID, id).firstOrNull()

    /**
     * 2) The user's [limit] latest payments (D8): `payments` has no fueling_id and its order_id is
     * not a fueling id (verified), so the user_id from [fuelingById] is the only link.
     */
    fun paymentsByUserId(userId: String, limit: Int = PAYMENT_LIMIT): List<Map<String, Any?>> =
        query(paymentDatabase, SQL_PAYMENTS_BY_USER, userId, limit)

    /** 3) The vendor-side order of the fueling (verified 1:1), or null. */
    fun fuelingOrdersById(id: String): Map<String, Any?>? =
        query(vendorsDatabase, SQL_FUELING_ORDER_BY_ID, id).firstOrNull()

    /** 4) The vendor-side events of the fueling, newest first. */
    fun fuelingEventsById(id: String, limit: Int = EVENT_LIMIT): List<Map<String, Any?>> =
        query(vendorsDatabase, SQL_FUELING_EVENTS_BY_ID, id, limit)

    /**
     * 5) The vendor order of the fueling, best-effort (D9): `vendor_fueling_orders` uses the same
     * UUID key space but covers only a subset of fuelings, so an empty result is normal.
     */
    fun vendorFuelingOrdersById(id: String): Map<String, Any?>? =
        query(vendorsDatabase, SQL_VENDOR_ORDER_BY_ID, id).firstOrNull()

    /**
     * Runs one SELECT through a prepared statement ([id] plus an optional row [limit]); the result
     * set, the statement and the connection are closed in the finally block.
     */
    private fun query(
        dataSource: DataSource,
        sql: String,
        id: String,
        limit: Int? = null,
    ): List<Map<String, Any?>> {
        require(limit == null || limit > 0) { "limit must be positive" }
        var connection: Connection? = null
        var statement: PreparedStatement? = null
        var resultSet: ResultSet? = null
        try {
            val openedConnection = dataSource.connection
            connection = openedConnection
            val preparedStatement = openedConnection.prepareStatement(sql)
            statement = preparedStatement
            preparedStatement.setString(1, id)
            if (limit != null) preparedStatement.setInt(2, limit)
            val rows = preparedStatement.executeQuery()
            resultSet = rows
            val result = ArrayList<Map<String, Any?>>()
            while (rows.next()) result += columnMap(rows)
            return result
        } finally {
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }

    /** One row as an ordered column map; the values keep their JDBC form, jsonb becomes text. */
    private fun columnMap(resultSet: ResultSet): Map<String, Any?> {
        val metadata = resultSet.metaData
        val row = LinkedHashMap<String, Any?>(metadata.columnCount)
        for (column in 1..metadata.columnCount) {
            row[metadata.getColumnLabel(column)] = serializableValue(resultSet.getObject(column))
        }
        return row
    }

    private fun serializableValue(value: Any?): Any? = when (value) {
        is PGobject -> value.value
        else -> value
    }

    companion object {
        /** Default LIMIT of the user's payments (D8, R11). */
        const val PAYMENT_LIMIT = 10

        /** Default LIMIT of the vendor events (5f, R11). */
        const val EVENT_LIMIT = 20

        // The five statements of spec 5f, in order; SELECT only (R5).
        private const val SQL_FUELING_BY_ID = "SELECT * FROM fuelings WHERE fueling_id = ?"

        private const val SQL_PAYMENTS_BY_USER =
            "SELECT * FROM payments WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"

        private const val SQL_FUELING_ORDER_BY_ID =
            "SELECT * FROM fueling_orders WHERE fueling_id = ?"

        private const val SQL_FUELING_EVENTS_BY_ID =
            "SELECT * FROM fueling_events WHERE fueling_id = ? ORDER BY created_at DESC LIMIT ?"

        private const val SQL_VENDOR_ORDER_BY_ID =
            "SELECT * FROM vendor_fueling_orders WHERE fueling_id = ?"
    }
}
